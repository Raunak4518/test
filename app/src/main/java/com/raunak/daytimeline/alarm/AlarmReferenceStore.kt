package com.raunak.daytimeline.alarm

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/** Stores only local wake-up verification material; nothing is uploaded. */
class AlarmReferenceStore(private val context: Context) {
    private val dir = File(context.filesDir, "alarm_references").apply { mkdirs() }

    fun savePhoto(alarmId: Long, bitmap: Bitmap): String? {
        val file = File(dir, "photo_$alarmId.jpg")
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun hasPhoto(alarmId: Long): Boolean = File(dir, "photo_$alarmId.jpg").exists()

    fun photoHash(alarmId: Long): LongArray? =
        runCatching { android.graphics.BitmapFactory.decodeFile(File(dir, "photo_$alarmId.jpg").absolutePath)?.let(::averageHash) }.getOrNull()

    fun verifyPhoto(alarmId: Long, candidate: Bitmap, maxDistance: Int = 18): Boolean {
        val expected = photoHash(alarmId) ?: return false
        val actual = averageHash(candidate)
        val distance = expected.indices.sumOf { java.lang.Long.bitCount(expected[it] xor actual[it]) }
        return distance <= maxDistance
    }

    fun saveBarcode(alarmId: Long, value: String) {
        context.getSharedPreferences("alarm_references", Context.MODE_PRIVATE)
            .edit().putString("barcode_$alarmId", value.trim()).apply()
    }

    fun barcode(alarmId: Long): String? =
        context.getSharedPreferences("alarm_references", Context.MODE_PRIVATE)
            .getString("barcode_$alarmId", null)?.takeIf { it.isNotBlank() }

    fun clear(alarmId: Long) {
        File(dir, "photo_$alarmId.jpg").delete()
        context.getSharedPreferences("alarm_references", Context.MODE_PRIVATE).edit().remove("barcode_$alarmId").apply()
    }

    private fun averageHash(bitmap: Bitmap): LongArray {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val gray = pixels.map { p -> (0.299 * ((p shr 16) and 0xff) + 0.587 * ((p shr 8) and 0xff) + 0.114 * (p and 0xff)).toInt() }
        val avg = gray.average()
        var bits = 0L
        gray.forEachIndexed { i, v -> if (v >= avg) bits = bits or (1L shl i) }
        return longArrayOf(bits)
    }
}
