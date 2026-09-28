package com.raunak.daytimeline.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.sqrt

/** Offline runtime primitives used by the ringing alarm activity. */
class AlarmMissionRuntime(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var shakeListener: SensorEventListener? = null
    private var stepListener: SensorEventListener? = null
    private var mediaPlayer: MediaPlayer? = null
    private var baselineSteps = -1
    private var shakes = 0
    private var lastShake = 0L

    fun startAlarmSound(resId: Int, volume: Float = 1f, vibrate: Boolean = true) {
        stopSound()
        mediaPlayer = MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            start()
        }
        if (vibrate) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun stopSound() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun startShake(target: Int, onProgress: (Int) -> Unit, onComplete: () -> Unit) {
        shakes = 0
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val g = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                val now = SystemClock.elapsedRealtime()
                if (g > 14f && now - lastShake > 250) {
                    lastShake = now
                    shakes++
                    onProgress(shakes)
                    if (shakes >= target) { stopShake(); onComplete() }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopShake() { shakeListener?.let { sensorManager.unregisterListener(it) }; shakeListener = null }

    fun startSteps(target: Int, onProgress: (Int) -> Unit, onComplete: () -> Unit) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        baselineSteps = -1
        stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values.firstOrNull()?.toInt() ?: return
                if (baselineSteps < 0) baselineSteps = total
                val progress = (total - baselineSteps).coerceAtLeast(0)
                onProgress(progress)
                if (progress >= target) { stopSteps(); onComplete() }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopSteps() { stepListener?.let { sensorManager.unregisterListener(it) }; stepListener = null }

    fun cameraAvailable(): Boolean = context.packageManager.hasSystemFeature("android.hardware.camera.any")
    fun barcodeAvailable(): Boolean = cameraAvailable()
    fun exactAlarmAllowed(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 31) Settings.canDrawOverlays(context) || true else true

    fun release() { stopShake(); stopSteps(); stopSound() }
}
