package com.raunak.daytimeline.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.SystemClock
import kotlin.math.sqrt

/** Offline runtime primitives used by the ringing alarm activity. */
class AlarmMissionRuntime(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var shakeListener: SensorEventListener? = null
    private var stepListener: SensorEventListener? = null
    private var ringtone: Ringtone? = null
    private var baselineSteps = -1
    private var shakes = 0
    private var lastShake = 0L

    fun startDefaultAlarmSound() {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(context, uri)?.also { it.play() }
    }
    fun stopSound() { ringtone?.runCatching { stop() }; ringtone = null }

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
    fun stopSteps() { stepListener?.let(sensorManager::unregisterListener); stepListener = null }
    fun cameraAvailable(): Boolean = context.packageManager.hasSystemFeature("android.hardware.camera.any")
    fun release() { stopShake(); stopSteps(); stopSound() }
}
