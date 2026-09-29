package com.raunak.daytimeline.filter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.raunak.daytimeline.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A local, DNS-only VPN. Android sends every DNS lookup to a virtual resolver address inside this
 * VPN; Chronora answers blocked names itself and forwards the rest to the chosen upstream resolver
 * over a socket excluded from the VPN. Only DNS is routed here, so normal traffic stays direct and
 * nothing is sent to any Chronora server.
 */
class WebFilterVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var filter: DomainFilter? = null
    @Volatile private var config = WebFilterConfig()
    private val pendingLog = ConcurrentLinkedQueue<BlockLogEntry>()
    private val uidNames = HashMap<Int, String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopFilter(); return START_NOT_STICKY }
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0), type)
        val store = WebFilterStore(this)
        config = store.config
        // Started by the system for Always-on VPN: treat that as the filter being switched on.
        if (!config.enabled && intent?.action != ACTION_START && intent?.action != ACTION_RELOAD) { store.update { it.copy(enabled = true) }; config = store.config }
        scope.launch {
            filter = store.buildFilter(config)
            if (intent?.action == ACTION_RELOAD && tun != null) return@launch
            establish()
        }
        return START_STICKY
    }

    private fun establish() {
        runCatching { tun?.close() }
        val builder = Builder()
            .setSession("Chronora web filter")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .setBlocking(true)
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
        runCatching { builder.addDisallowedApplication(packageName) }
        tun = builder.establish() ?: run { stopSelf(); return }
        running = true
        val fd = tun!!.fileDescriptor
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        scope.launch {
            val buffer = ByteArray(32767)
            while (isActive) {
                val n = try { input.read(buffer) } catch (_: Exception) { break }
                if (n <= 0) continue
                val query = DnsPacket.parse(buffer, n) ?: continue
                launch { handle(query, output) }
            }
        }
        scope.launch {
            while (isActive) {
                delay(5000)
                val batch = generateSequence { pendingLog.poll() }.toList()
                if (batch.isNotEmpty()) {
                    val store = WebFilterStore(this@WebFilterVpnService)
                    store.appendLog(batch)
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(store.blockedToday()))
                }
            }
        }
    }

    private fun handle(query: DnsQuery, output: FileOutputStream) {
        val f = filter ?: return
        val app = if (config.appRules.isNotEmpty()) ownerPackage(query) else null
        if (app != null && FilterLock.appBlocked(config.appRules[app], onWifi())) {
            reply(output, query, DnsPacket.answer(query, DnsPacket.RCODE_NXDOMAIN))
            pendingLog += BlockLogEntry(System.currentTimeMillis(), query.name, "App firewall", app)
            return
        }
        when (val verdict = f.decide(query.name)) {
            is FilterVerdict.Block -> {
                // Also refuse HTTPS/SVCB records so browsers cannot learn alternative endpoints.
                reply(output, query, DnsPacket.answer(query, DnsPacket.RCODE_NXDOMAIN))
                pendingLog += BlockLogEntry(System.currentTimeMillis(), query.name, verdict.reason, app ?: ownerPackage(query) ?: "")
            }
            is FilterVerdict.Rewrite -> {
                if (query.type != DnsPacket.TYPE_A) { reply(output, query, DnsPacket.answer(query)); return }
                val upstreamAnswer = forward(DnsPacket.buildQuery(query.id, verdict.target)) ?: return
                reply(output, query, DnsPacket.answer(query, 0, DnsPacket.aRecords(upstreamAnswer)))
            }
            FilterVerdict.Allow -> forward(query.dns)?.let { reply(output, query, it) }
        }
    }

    private fun forward(dns: ByteArray): ByteArray? {
        for (server in listOf(config.upstream.primary, config.upstream.secondary)) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4000
                    socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(server), 53))
                    val buf = ByteArray(4096)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    return buf.copyOf(packet.length)
                }
            } catch (_: Exception) { /* try the secondary server */ }
        }
        return null
    }

    private fun reply(output: FileOutputStream, query: DnsQuery, dns: ByteArray) {
        val packet = DnsPacket.wrapResponse(query, dns)
        synchronized(output) { runCatching { output.write(packet) } }
    }

    /** Which app asked (Android 10+ attributes resolver sockets to the requesting app). */
    private fun ownerPackage(query: DnsQuery): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val uid = runCatching {
            cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(query.sourceIp), query.sourcePort),
                InetSocketAddress(InetAddress.getByAddress(query.destIp), query.destPort)
            )
        }.getOrDefault(-1)
        if (uid < 10000) return null
        return synchronized(uidNames) { uidNames.getOrPut(uid) { packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "uid $uid" } }
    }

    private fun onWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun notification(blockedToday: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Web filter active")
            .setContentText("$blockedToday blocked today · ${config.upstream.label.substringBefore(" (")}")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun stopFilter() {
        running = false
        runCatching { tun?.close() }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopFilter(); super.onRevoke() }

    override fun onDestroy() {
        running = false
        scope.cancel()
        runCatching { tun?.close() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "chronora.filter.START"
        const val ACTION_RELOAD = "chronora.filter.RELOAD"
        const val ACTION_STOP = "chronora.filter.STOP"
        private const val VPN_ADDRESS = "10.111.222.2"
        private const val DNS_ADDRESS = "10.111.222.1"
        private const val CHANNEL = "chronora_web_filter"
        private const val NOTIFICATION_ID = 7401
        @Volatile var running = false
            private set

        /** Starts or reloads the filter; returns false when VPN consent is still needed. */
        fun start(context: Context, reload: Boolean = false): Boolean {
            if (prepare(context) != null) return false
            ContextCompat.startForegroundService(context, Intent(context, WebFilterVpnService::class.java).setAction(if (reload && running) ACTION_RELOAD else ACTION_START))
            return true
        }

        fun stop(context: Context) {
            if (running) context.startService(Intent(context, WebFilterVpnService::class.java).setAction(ACTION_STOP))
        }

        private fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Web filter", NotificationManager.IMPORTANCE_MIN))
        }
    }
}
