package com.raunak.daytimeline.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.sqrt

/** Offline runtime primitives used by the ringing alarm activity. */
class AlarmMissionRuntime(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var shakeListener: SensorEventListener? = null
    private var stepListener: SensorEventListener? = null
    private var squatListener: SensorEventListener? = null
    private var ringtone: Ringtone? = null
    private var volumeRunnable: Runnable? = null
    private var baselineSteps = -1
    private var detectedSteps = 0
    private var shakes = 0
    private var squats = 0
    private var squatLow = false
    private var lastShake = 0L
    private var lastSquat = 0L

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var savedAlarmVolume = -1
    private var baseVolume = 1f

    private var player: android.media.MediaPlayer? = null
    private var enhancer: android.media.audiofx.LoudnessEnhancer? = null
    private val lockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lockedLevel = -1

    /** Puts the alarm stream back to the locked level whenever anything lowers it. */
    private val volumeGuard = object : Runnable {
        override fun run() {
            if (lockedLevel < 0) return
            runCatching { if (audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM) < lockedLevel) audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, lockedLevel, 0) }
            lockHandler.postDelayed(this, 300L)
        }
    }

    /** Re-applies the locked volume right away (called when a volume key is pressed). */
    fun enforceVolume() { if (lockedLevel >= 0) runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, lockedLevel, 0) } }

    private fun setLevel(f: Float) {
        val v = (f * quietFactor).coerceIn(0f, 1f)
        player?.let { runCatching { it.setVolume(v, v) } } ?: run { if (Build.VERSION.SDK_INT >= 28) runCatching { ringtone?.volume = v } }
    }

    fun startAlarmSound(config: AlarmPersistentConfig) {
        stopSound()
        // Ring at the alarm's own volume on the alarm stream (works in silent mode), restored afterwards.
        runCatching {
            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            savedAlarmVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            val level = (max * config.volume / 100f).toInt().coerceIn(1, max)
            audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, level, 0)
            if (config.volumeLock) { lockedLevel = level; lockHandler.post(volumeGuard) }
        }
        val uri = config.soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        // A MediaPlayer lets a LoudnessEnhancer push the sound past 100 %; the Ringtone path is the fallback.
        player = runCatching {
            android.media.MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        player?.let { mp ->
            val gain = when (config.boost) { 1 -> 800; 2 -> 1500; 3 -> 2400; else -> 0 }
            if (gain > 0) enhancer = runCatching { android.media.audiofx.LoudnessEnhancer(mp.audioSessionId).apply { setTargetGain(gain); enabled = true } }.getOrNull()
        }
        if (player == null) ringtone = (RingtoneManager.getRingtone(context, uri) ?: RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))?.also { tone ->
            tone.audioAttributes = attrs
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            tone.play()
        }
        baseVolume = 1f
        if (config.gentleVolumeSeconds > 0) {
            val start = SystemClock.elapsedRealtime()
            val duration = config.gentleVolumeSeconds.coerceAtMost(300) * 1000L
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val tick = object : Runnable {
                override fun run() {
                    if (volumeRunnable !== this) return
                    val fraction = ((SystemClock.elapsedRealtime() - start).toFloat() / duration).coerceIn(0f, 1f)
                    baseVolume = 0.12f + 0.88f * fraction
                    setLevel(baseVolume)
                    if (fraction < 1f) handler.postDelayed(this, 250L)
                }
            }
            volumeRunnable = tick
            handler.post(tick)
        }
        if (config.vibration && config.vibrationPattern != "OFF") vibrate(config.vibrationPattern)
    }

    private var quietFactor = 1f

    /** Quieter while you're actively doing a mission; full volume again when you stop. */
    fun setQuiet(quiet: Boolean) {
        quietFactor = if (quiet) 0.25f else 1f
        setLevel(baseVolume)
        if (quiet) runCatching { vibrator.cancel() }
    }

    fun resumeVibration(config: AlarmPersistentConfig) { if (config.vibration && config.vibrationPattern != "OFF") vibrate(config.vibrationPattern) }

    fun startDefaultAlarmSound() {
        startAlarmSound(AlarmPersistentConfig(0, 0, 0))
    }

    /** Repeating patterns (the old one buzzed once and stopped). */
    private fun vibrate(pattern: String) {
        if (!vibrator.hasVibrator()) return
        val wave = when (pattern) {
            "HEARTBEAT" -> longArrayOf(0, 120, 120, 120, 800)
            "STRONG" -> longArrayOf(0, 900, 300)
            else -> longArrayOf(0, 450, 250, 450, 700)
        }
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(wave, 0))
        else @Suppress("DEPRECATION") vibrator.vibrate(wave, 0)
    }

    fun stopSound() {
        volumeRunnable = null
        lockedLevel = -1
        lockHandler.removeCallbacks(volumeGuard)
        enhancer?.runCatching { release() }
        enhancer = null
        player?.runCatching { stop(); release() }
        player = null
        ringtone?.runCatching { stop() }
        ringtone = null
        if (vibrator.hasVibrator()) vibrator.cancel()
        if (savedAlarmVolume >= 0) { runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, savedAlarmVolume, 0) }; savedAlarmVolume = -1 }
    }

    fun startShake(target: Int, onProgress: (Int) -> Unit, onComplete: () -> Unit) {
        shakes = 0
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val g = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                val now = SystemClock.elapsedRealtime()
                if (g > 14f && now - lastShake > 250) {
                    lastShake = now; shakes++; onProgress(shakes)
                    if (shakes >= target) { stopShake(); onComplete() }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopShake() { shakeListener?.let(sensorManager::unregisterListener); shakeListener = null }

    fun startSteps(target: Int, onProgress: (Int) -> Unit, onComplete: () -> Unit) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        baselineSteps = -1
        detectedSteps = 0
        stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val progress = if (sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                    detectedSteps++
                    detectedSteps
                } else {
                    val total = event.values.firstOrNull()?.toInt() ?: return
                    if (baselineSteps < 0) baselineSteps = total
                    (total - baselineSteps).coerceAtLeast(0)
                }
                onProgress(progress)
                if (progress >= target) { stopSteps(); onComplete() }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopSteps() { stepListener?.let(sensorManager::unregisterListener); stepListener = null }

    /** Heuristic on-device squat detector using linear acceleration. It requires a down/up movement cycle. */
    fun startSquats(target: Int, onProgress: (Int) -> Unit, onComplete: () -> Unit) {
        squats = 0
        squatLow = false
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        squatListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val a = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                val now = SystemClock.elapsedRealtime()
                if (!squatLow && a < 2.2f) squatLow = true
                if (squatLow && a > 3.8f && now - lastSquat > 700L) {
                    squatLow = false
                    lastSquat = now
                    squats++
                    onProgress(squats)
                    if (squats >= target) { stopSquats(); onComplete() }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(squatListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopSquats() { squatListener?.let(sensorManager::unregisterListener); squatListener = null }

    fun cameraAvailable(): Boolean = context.packageManager.hasSystemFeature("android.hardware.camera.any")

    fun release() {
        stopShake()
        stopSteps()
        stopSquats()
        stopSound()
    }
}
