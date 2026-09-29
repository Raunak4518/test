package com.raunak.daytimeline.pro

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.raunak.daytimeline.MainActivity

/**
 * Geofenced reminders using the platform LocationManager proximity alerts — no Google Play
 * services, no account, no network; GPS works offline.
 */
class LocationReminderManager(private val context: Context) {
    private val store = LocationReminderStore(context)
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission() = android.os.Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun registerAll() {
        if (!hasPermission() || manager == null) return
        store.all().forEach { reminder ->
            val pi = pending(reminder.id)
            manager.removeProximityAlert(pi)
            if (reminder.enabled) {
                runCatching { manager.addProximityAlert(reminder.latitude, reminder.longitude, reminder.radiusMeters, -1, pi) }
            }
        }
    }

    fun save(reminder: LocationReminder) {
        store.upsert(reminder)
        registerAll()
    }

    fun delete(id: Long) {
        runCatching { manager?.removeProximityAlert(pending(id)) }
        store.delete(id)
    }

    /** Best-effort current position from any enabled provider's last fix. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        if (!hasPermission() || manager == null) return null
        return manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Asks the GPS/network provider for a fresh fix; [onResult] runs on the main thread. */
    @SuppressLint("MissingPermission")
    fun currentLocation(onResult: (Location?) -> Unit) {
        if (!hasPermission() || manager == null) return onResult(null)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { manager.isProviderEnabled(it) }
            ?: return onResult(lastKnown())
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { onResult(it ?: lastKnown()) }
        } else {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, { onResult(it) }, context.mainLooper)
        }
    }

    private fun pending(id: Long) = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(),
        Intent(context, LocationReminderReceiver::class.java).putExtra(LocationReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
}

class LocationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, true)
        val store = LocationReminderStore(context)
        val reminder = store.all().firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        if (!LocationReminderRules.shouldNotify(reminder, entering, now)) return
        store.upsert(LocationReminderRules.afterFired(reminder, now))
        if (!reminder.repeat) LocationReminderManager(context).registerAll()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Place reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(
            (id % Int.MAX_VALUE).toInt(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(reminder.title)
                .setContentText((if (entering) "Arrived at " else "Left ") + reminder.placeName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val EXTRA_ID = "placeReminderId"
        private const val CHANNEL = "chronora_places"
    }
}
