package com.raunak.daytimeline.pro

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Procedurally generated focus sounds: no downloads, no subscriptions, works fully offline. */
enum class AmbientSound(val label: String) {
    WHITE("White noise"),
    PINK("Pink noise"),
    BROWN("Brown noise"),
    RAIN("Rain"),
    OCEAN("Ocean waves"),
    FAN("Fan hum"),
    BINAURAL_FOCUS("40 Hz focus tone")
}

/**
 * Streams 16-bit stereo PCM. The generator is stateful so consecutive buffers join seamlessly.
 */
class AmbientNoiseGenerator(
    val sound: AmbientSound,
    val sampleRate: Int = 44_100,
    seed: Int = 7
) {
    private val random = Random(seed)
    private var brown = 0.0
    private val pink = DoubleArray(7)
    private var sampleIndex = 0L
    private var lowpass = 0.0
    private var dropEnvelope = 0.0

    /** Fills an interleaved stereo buffer and returns it; [volume] is 0..1. */
    fun fill(buffer: ShortArray, volume: Float): ShortArray {
        val gain = volume.coerceIn(0f, 1f).toDouble()
        var i = 0
        while (i + 1 < buffer.size) {
            val (l, r) = next()
            buffer[i] = toPcm(l * gain)
            buffer[i + 1] = toPcm(r * gain)
            i += 2
        }
        return buffer
    }

    private fun toPcm(v: Double): Short = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

    private fun white() = random.nextDouble() * 2 - 1

    private fun pinkSample(): Double {
        // Paul Kellet's economy pink-noise filter
        val w = white()
        pink[0] = 0.99886 * pink[0] + w * 0.0555179
        pink[1] = 0.99332 * pink[1] + w * 0.0750759
        pink[2] = 0.96900 * pink[2] + w * 0.1538520
        pink[3] = 0.86650 * pink[3] + w * 0.3104856
        pink[4] = 0.55000 * pink[4] + w * 0.5329522
        pink[5] = -0.7616 * pink[5] - w * 0.0168980
        val out = pink.sum() + pink[6] + w * 0.5362
        pink[6] = w * 0.115926
        return out * 0.11
    }

    private fun brownSample(): Double {
        brown = (brown + 0.02 * white()) / 1.02
        return brown * 3.5
    }

    private fun next(): Pair<Double, Double> {
        val t = sampleIndex.toDouble() / sampleRate
        sampleIndex++
        return when (sound) {
            AmbientSound.WHITE -> white() * 0.35 to white() * 0.35
            AmbientSound.PINK -> pinkSample() to pinkSample()
            AmbientSound.BROWN -> brownSample().let { it to it }
            AmbientSound.RAIN -> {
                val bed = pinkSample() * 0.6
                if (random.nextDouble() < 0.0009) dropEnvelope = 0.5 + random.nextDouble() * 0.5
                dropEnvelope *= 0.992
                val drop = white() * dropEnvelope * 0.35
                (bed + drop) to (bed * 0.9 + drop * 0.7)
            }
            AmbientSound.OCEAN -> {
                val swell = 0.35 + 0.65 * (0.5 + 0.5 * sin(2 * PI * t / 9.0)).let { it * it }
                val n = brownSample() * 0.7 + pinkSample() * 0.3
                (n * swell) to (n * (0.35 + 0.65 * (0.5 + 0.5 * sin(2 * PI * (t + 1.3) / 9.0)).let { it * it }))
            }
            AmbientSound.FAN -> {
                lowpass += 0.08 * (white() - lowpass)
                val hum = 0.08 * sin(2 * PI * 60.0 * t) + 0.04 * sin(2 * PI * 120.0 * t)
                (lowpass * 0.9 + hum).let { it to it }
            }
            AmbientSound.BINAURAL_FOCUS -> {
                // 200 Hz left, 240 Hz right → 40 Hz beat, softly layered over brown noise
                val bed = brownSample() * 0.4
                (bed + 0.18 * sin(2 * PI * 200.0 * t)) to (bed + 0.18 * sin(2 * PI * 240.0 * t))
            }
        }
    }
}
