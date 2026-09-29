package com.raunak.daytimeline.campus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay

/**
 * If the phone restarts overnight (update, crash, battery), normal alarms are only restored after the
 * first unlock. This keeps a copy of the next wake time in device-protected storage and rings a
 * simple, loud fallback alarm even before the phone is unlocked.
 */
object DirectBootWake {
    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext().getSharedPreferences("wake_fallback", Context.MODE_PRIVATE)

    fun save(context: Context, at: Long, label: String) = prefs(context).edit().putLong("at", at).putString("label", label).apply()
    fun target(context: Context) = prefs(context).getLong("at", 0) to (prefs(context).getString("label", "") ?: "")
    fun markDismissed(context: Context) = prefs(context).edit().putLong("dismissed", System.currentTimeMillis()).apply()
    fun dismissedAt(context: Context) = prefs(context).getLong("dismissed", 0)

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7740, Intent(context, DirectBootAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun scheduleFallback(context: Context) {
        val (at, _) = target(context)
        val now = System.currentTimeMillis()
        val am = context.getSystemService(AlarmManager::class.java)
        when {
            at <= 0 -> return
            at > now -> runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pending(context))
            }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
            now - at < 3 * 3_600_000L && dismissedAt(context) < at -> ring(context)
        }
    }

    fun cancelFallback(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(pending(context))

    fun ring(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wake_fallback", "Wake-up fallback", NotificationManager.IMPORTANCE_HIGH))
        val full = PendingIntent.getActivity(context, 7741, Intent(context, FallbackAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            7741,
            NotificationCompat.Builder(context, "wake_fallback")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Wake up")
                .setContentText(target(context).second)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(full, true)
                .setOngoing(true)
                .build()
        )
    }
}

/** Runs before the first unlock after a reboot. */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) DirectBootWake.scheduleFallback(context)
    }
}

class DirectBootAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = DirectBootWake.ring(context)
}

class FallbackAlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@FallbackAlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }
        runCatching { getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)) }
        val label = DirectBootWake.target(this).second
        setContent {
            var held by remember { mutableIntStateOf(0) }
            var pressing by remember { mutableStateOf(false) }
            LaunchedEffect(pressing) { while (pressing && held < 3) { delay(1000); held++ } ; if (!pressing) held = 0 }
            LaunchedEffect(held) { if (held >= 3) dismiss() }
            Column(Modifier.fillMaxSize().background(Color(0xFF101815)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("WAKE UP", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(label, color = Color(0xFFB9CCC2), textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                Surface(
                    color = Color(0xFF55786A), shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().height(96.dp).pointerInput(Unit) {
                        detectTapGestures(onPress = { pressing = true; tryAwaitRelease(); pressing = false })
                    }
                ) { Box(contentAlignment = Alignment.Center) { Text(if (pressing) "Keep holding… ${3 - held}" else "Hold 3 s — I'm up", color = Color.White, style = MaterialTheme.typography.titleLarge) } }
            }
        }
    }

    private fun dismiss() {
        DirectBootWake.markDismissed(this)
        getSystemService(NotificationManager::class.java).cancel(7741)
        finish()
    }

    override fun onDestroy() {
        runCatching { player?.stop(); player?.release() }
        runCatching { getSystemService(Vibrator::class.java)?.cancel() }
        super.onDestroy()
    }
}
