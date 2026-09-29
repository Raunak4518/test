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

    fun startAlarmSound(config: AlarmPersistentConfig) {
        stopSound()
        // Ring at the alarm's own volume on the alarm stream (works in silent mode), restored afterwards.
        runCatching {
            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            savedAlarmVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, (max * config.volume / 100f).toInt().coerceIn(1, max), 0)
        }
        val uri = config.soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = (RingtoneManager.getRingtone(context, uri) ?: RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))?.also { tone ->
            tone.audioAttributes = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            tone.play()
            if (Build.VERSION.SDK_INT >= 28 && config.gentleVolumeSeconds > 0) {
                val start = SystemClock.elapsedRealtime()
                val duration = config.gentleVolumeSeconds.coerceAtMost(300) * 1000L
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val tick = object : Runnable {
                    override fun run() {
                        if (volumeRunnable !== this) return
                        val elapsed = SystemClock.elapsedRealtime() - start
                        val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                        baseVolume = 0.12f + 0.88f * fraction
                        runCatching { tone.volume = baseVolume * quietFactor }
                        if (fraction < 1f) handler.postDelayed(this, 250L)
                    }
                }
                volumeRunnable = tick
                handler.post(tick)
            }
        }
        if (config.vibration && config.vibrationPattern != "OFF") vibrate(config.vibrationPattern)
    }

    private var quietFactor = 1f

    /** Quieter while you're actively doing a mission; full volume again when you stop. */
    fun setQuiet(quiet: Boolean) {
        quietFactor = if (quiet) 0.25f else 1f
        if (Build.VERSION.SDK_INT >= 28) runCatching { ringtone?.volume = baseVolume * quietFactor }
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
