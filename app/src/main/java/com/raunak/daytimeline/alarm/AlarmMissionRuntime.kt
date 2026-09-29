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

    fun startAlarmSound(config: AlarmPersistentConfig) {
        stopSound()
        val uri = config.soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(context, uri)?.also { tone ->
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            tone.play()
            if (Build.VERSION.SDK_INT >= 28 && config.gentleVolumeSeconds > 0) {
                val start = SystemClock.elapsedRealtime()
                val duration = config.gentleVolumeSeconds.coerceAtMost(300) * 1000L
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val tick = object : Runnable {
                    override fun run() {
                        val elapsed = SystemClock.elapsedRealtime() - start
                        val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                        tone.volume = 0.12f + 0.88f * fraction
                        if (fraction < 1f) {
                            handler.postDelayed(this, 250L)
                        }
                    }
                }
                volumeRunnable = tick
                handler.post(tick)
            }
        }
        if (config.vibration) vibratePulse()
    }

    fun startDefaultAlarmSound() {
        startAlarmSound(AlarmPersistentConfig(0, 0, 0))
    }

    private fun vibratePulse() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 250, 450), -1))
        else @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 450, 250, 450), -1)
    }

    fun stopSound() {
        volumeRunnable = null
        ringtone?.runCatching { stop() }
        ringtone = null
        if (vibrator.hasVibrator()) vibrator.cancel()
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
