package com.raunak.daytimeline.alarm

import kotlin.math.max
import kotlin.math.min

data class AlarmSoundPolicy(
    val startVolume: Float = 0.15f,
    val endVolume: Float = 1f,
    val rampSeconds: Int = 0,
    val vibration: Boolean = true,
    val vibrationDelaySeconds: Int = 0,
    val repeatSound: Boolean = true
) {
    fun normalized() = copy(
        startVolume = startVolume.coerceIn(0f, 1f),
        endVolume = endVolume.coerceIn(0f, 1f),
        rampSeconds = rampSeconds.coerceIn(0, 1440),
        vibrationDelaySeconds = vibrationDelaySeconds.coerceIn(0, 600)
    )

    fun volumeAt(elapsedSeconds: Int): Float {
        val p = normalized()
        if (p.rampSeconds <= 0) return p.endVolume
        val fraction = (elapsedSeconds.toFloat() / p.rampSeconds).coerceIn(0f, 1f)
        return p.startVolume + (p.endVolume - p.startVolume) * fraction
    }

    fun vibrationShouldStart(elapsedSeconds: Int): Boolean = normalized().vibration && elapsedSeconds >= vibrationDelaySeconds
}
