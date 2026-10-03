package com.raunak.daytimeline.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.raunak.daytimeline.BuildConfig
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A published build: version code from the tag "v123", the APK link and the release notes. */
data class AppRelease(val versionCode: Int, val versionName: String, val apkUrl: String, val sizeBytes: Long, val notes: String, val publishedAt: String)

sealed class UpdateCheck {
    data class Available(val release: AppRelease) : UpdateCheck()
    object UpToDate : UpdateCheck()
    data class Failed(val message: String) : UpdateCheck()
}

/**
 * Self-updates from this project's GitHub Releases (built and published by CI on every push to main).
 * Downloads the APK and hands it to Android's package installer. On Android 12+ once Chronora itself
 * installed the current version, later updates install without a confirmation screen.
 */
object AppUpdater {
    private val api get() = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
    const val ACTION_INSTALL_RESULT = "com.raunak.daytimeline.UPDATE_INSTALL_RESULT"
    private const val CHANNEL = "app_updates"

    val currentVersion: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    /** "v123" → 123. */
    fun versionFromTag(tag: String): Int? = tag.trim().removePrefix("v").removePrefix("V").substringBefore('-').toIntOrNull()

    fun parseRelease(json: String): AppRelease? = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        val code = versionFromTag(o.get("tag_name").asString) ?: return null
        val asset = o.getAsJsonArray("assets").map { it.asJsonObject }.firstOrNull { it.get("name").asString.endsWith(".apk") } ?: return null
        AppRelease(code, o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "v$code", asset.get("browser_download_url").asString,
            asset.get("size")?.asLong ?: 0L, o.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim(), o.get("published_at")?.takeIf { !it.isJsonNull }?.asString.orEmpty())
    }.getOrNull()

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(api).openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            when (c.responseCode) {
                200 -> {
                    val r = parseRelease(c.inputStream.bufferedReader().use { it.readText() }) ?: return@runCatching UpdateCheck.Failed("No app file in the latest release yet")
                    if (r.versionCode > currentVersion) UpdateCheck.Available(r) else UpdateCheck.UpToDate
                }
                404 -> UpdateCheck.Failed("No release published yet")
                403 -> UpdateCheck.Failed("GitHub is rate-limiting — try again in a while")
                else -> UpdateCheck.Failed("GitHub answered ${c.responseCode}")
            }
        }.getOrElse { UpdateCheck.Failed(if (it is java.net.UnknownHostException) "No internet connection" else it.message ?: "Couldn't check") }
    }

    /** Downloads the APK (following GitHub's redirect), reporting progress 0..1. */
    suspend fun download(context: Context, r: AppRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "chronora-${r.versionCode}.apk")
        var url = URL(r.apkUrl)
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            // Never follow a redirect to plain HTTP: the APK must arrive over HTTPS.
            require(url.protocol == "https") { "Insecure download link" }
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000; c.readTimeout = 60_000
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
            if (code != 200) error("Download failed ($code)")
            break
        }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        out
    }

    /** Whether Android lets Chronora install apps; if not, [unknownSourcesIntent] opens the switch. */
    fun canInstall(context: Context) = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the APK to the package installer; the app restarts on the new version when done. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("chronora.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_RESULT),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
            session.commit(pi.intentSender)
        }
    }

    fun notifyAvailable(context: Context, r: AppRelease) {
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 7601, Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_UPDATE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Chronora update ready").setContentText("${r.versionName} — tap to install")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${r.versionName}\n${r.notes.take(300)}"))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(7602, n) }
    }

    fun scheduleDailyCheck(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("app-update-check", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }

    const val EXTRA_OPEN = "chronora.open"
    const val OPEN_UPDATE = "update"
}

/** Daily: tells you once per new version that an update is out. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = (AppUpdater.check() as? UpdateCheck.Available)?.release ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("chronora_updates", Context.MODE_PRIVATE)
        if (prefs.getInt("notified", 0) < r.versionCode) {
            AppUpdater.notifyAvailable(applicationContext, r)
            prefs.edit().putInt("notified", r.versionCode).apply()
        }
        return Result.success()
    }
}

/** Result of an install: asks for confirmation when Android requires it, reports failures. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> UpdateStatus.lastError = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
        }
    }
}

object UpdateStatus { @Volatile var lastError: String? = null }

/** Opens the update dialog from anywhere (menu, notification). */
object UpdateNav { val requested = kotlinx.coroutines.flow.MutableStateFlow(false) }
