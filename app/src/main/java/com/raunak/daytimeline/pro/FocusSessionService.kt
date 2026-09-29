package com.raunak.daytimeline.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.data.AppDatabase
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.domain.PomodoroEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Remembers when the current Pomodoro focus phase ends so Focus Guard can block during it. */
object PomodoroFocusFlag {
    private fun prefs(c: Context) = c.getSharedPreferences("chronora_focus_flag", Context.MODE_PRIVATE)
    fun set(c: Context, focusUntil: Long) = prefs(c).edit().putLong("until", focusUntil).apply()
    fun isFocusRunning(c: Context, now: Long) = prefs(c).getLong("until", 0) > now
}

/**
 * Keeps the Pomodoro alive with the screen off: a live countdown notification with actions,
 * phase-change alerts, and optional generated ambient sound.
 */
class FocusSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var audioJob: Job? = null
    private var playing: AmbientSound? = null
    private var lastSignature = ""
    private var soundOnly = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val prefs = FocusSoundPrefs(this)
        if (intent?.action == ACTION_SOUND_ONLY) soundOnly = true
        promote(buildNotification(PomodoroStateEntity(), prefs.sound))
        val dao = AppDatabase.get(this).pomodoroDao()
        scope.launch {
            val current = dao.current() ?: PomodoroStateEntity()
            val now = System.currentTimeMillis()
            when (intent?.action) {
                ACTION_TOGGLE -> dao.upsert(
                    when {
                        current.phase == "IDLE" -> PomodoroEngine.start(null, current)
                        current.running -> PomodoroEngine.pause(current, now)
                        else -> PomodoroEngine.resume(current)
                    }
                )
                ACTION_SKIP -> dao.upsert(PomodoroEngine.skip(current, now))
                ACTION_EXTEND -> dao.upsert(PomodoroEngine.extend(current, 5, now))
                ACTION_STOP -> {
                    GardenStore(this@FocusSessionService).onAbandon(current, now)
                    dao.upsert(PomodoroEngine.reset(current))
                    soundOnly = false
                    shutdown()
                    return@launch
                }
                ACTION_SOUND_CHANGED -> lastSignature = ""
            }
            startLoop()
        }
        return START_STICKY
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            val dao = AppDatabase.get(this@FocusSessionService).pomodoroDao()
            while (isActive) {
                val now = System.currentTimeMillis()
                val before = dao.current() ?: PomodoroStateEntity()
                val state = PomodoroEngine.tick(before, now)
                if (state != before) {
                    GardenStore(this@FocusSessionService).onTransition(before, state)
                    dao.upsert(state)
                }
                if (before.phase != state.phase && before.phase != "IDLE") phaseAlert(state)
                PomodoroFocusFlag.set(this@FocusSessionService, if (state.phase == "FOCUS" && state.running) state.targetEpochMillis else 0L)

                val prefs = FocusSoundPrefs(this@FocusSessionService)
                val wantSound = if (soundOnly || (state.running && state.phase != "IDLE")) prefs.sound else null
                if (wantSound != playing) { stopAudio(); wantSound?.let { startAudio(it) } }

                if (state.phase == "IDLE" && !soundOnly) { shutdown(); break }
                val signature = "${state.phase}|${state.running}|${state.targetEpochMillis}|${state.remainingSeconds / 60}|${prefs.sound}"
                if (signature != lastSignature) {
                    lastSignature = signature
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state, prefs.sound))
                    FocusWidget.refresh(this@FocusSessionService)
                }
                delay(1000)
            }
        }
    }

    private fun startAudio(sound: AmbientSound) {
        playing = sound
        audioJob = scope.launch(Dispatchers.IO) {
            val generator = AmbientNoiseGenerator(sound)
            val minBuffer = AudioTrack.getMinBufferSize(generator.sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(generator.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(minBuffer, 8192) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val buffer = ShortArray(4096)
            try {
                track.play()
                val prefs = FocusSoundPrefs(this@FocusSessionService)
                var volume = prefs.volume
                var counter = 0
                while (isActive) {
                    if (++counter % 20 == 0) volume = prefs.volume
                    track.write(generator.fill(buffer, volume), 0, buffer.size)
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    private fun stopAudio() {
        audioJob?.cancel()
        audioJob = null
        playing = null
    }

    private fun shutdown() {
        stopAudio()
        FocusWidget.refresh(this)
        PomodoroFocusFlag.set(this, 0L)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promote(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun phaseAlert(state: PomodoroStateEntity) {
        val title = when (state.phase) {
            "FOCUS" -> "Break over — focus #${state.cycleIndex}"
            "LONG_BREAK" -> "Great work! Long break"
            else -> "Focus session complete — take a break"
        }
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("${state.remainingSeconds / 60} min ${if (state.phase == "FOCUS") "focus" else "break"} started")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, "Skip", action(ACTION_SKIP))
            .addAction(0, "+5 min", action(ACTION_EXTEND))
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
    }

    private fun buildNotification(state: PomodoroStateEntity, sound: AmbientSound?): Notification {
        val phase = when (state.phase) {
            "FOCUS" -> "Focus ${state.cycleIndex}/${state.cyclesPerRound}"
            "SHORT_BREAK" -> "Short break"
            "LONG_BREAK" -> "Long break"
            else -> "Focus sounds"
        }
        val builder = NotificationCompat.Builder(this, SESSION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(phase + (sound?.let { " · ${it.label}" } ?: ""))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
        if (state.phase != "IDLE") {
            if (state.running) {
                builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(state.targetEpochMillis).setShowWhen(true)
                builder.setContentText("Running")
            } else {
                builder.setContentText("Paused · %02d:%02d left".format(state.remainingSeconds / 60, state.remainingSeconds % 60))
            }
            builder.addAction(0, if (state.running) "Pause" else "Resume", action(ACTION_TOGGLE))
            builder.addAction(0, "Skip", action(ACTION_SKIP))
            builder.addAction(0, "+5 min", action(ACTION_EXTEND))
        } else {
            builder.setContentText("Playing offline ambient sound")
        }
        builder.addAction(0, "Stop", action(ACTION_STOP))
        return builder.build()
    }

    private fun openApp() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun action(name: String) = PendingIntent.getService(
        this, name.hashCode(), Intent(this, FocusSessionService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "chronora.focus.START"
        const val ACTION_TOGGLE = "chronora.focus.TOGGLE"
        const val ACTION_SKIP = "chronora.focus.SKIP"
        const val ACTION_EXTEND = "chronora.focus.EXTEND"
        const val ACTION_STOP = "chronora.focus.STOP"
        const val ACTION_SOUND_ONLY = "chronora.focus.SOUND_ONLY"
        const val ACTION_SOUND_CHANGED = "chronora.focus.SOUND_CHANGED"
        private const val SESSION_CHANNEL = "chronora_focus_session"
        private const val ALERT_CHANNEL = "chronora_focus_alerts"
        private const val NOTIFICATION_ID = 7301
        private const val ALERT_ID = 7302

        fun send(context: Context, action: String = ACTION_START) {
            ContextCompat.startForegroundService(context, Intent(context, FocusSessionService::class.java).setAction(action))
        }

        private fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(SESSION_CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Focus phase alerts", NotificationManager.IMPORTANCE_HIGH))
        }
    }
}
