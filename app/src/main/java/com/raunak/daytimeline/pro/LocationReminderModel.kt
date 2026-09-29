package com.raunak.daytimeline.pro

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlaceTrigger(val label: String) { ARRIVE("When I arrive"), LEAVE("When I leave"), BOTH("Arrive or leave") }

/** "When I arrive at college → submit assignment", stored and evaluated on-device. */
data class LocationReminder(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val trigger: PlaceTrigger = PlaceTrigger.ARRIVE,
    val enabled: Boolean = true,
    /** Deliver again on every visit; otherwise the reminder disables itself after firing. */
    val repeat: Boolean = true,
    val lastFiredAt: Long = 0
)

data class SavedPlace(val name: String, val latitude: Double, val longitude: Double)

object LocationReminderRules {
    /** Minimum gap between two notifications for the same reminder, to absorb GPS jitter at the boundary. */
    const val COOLDOWN_MILLIS = 10 * 60_000L

    fun shouldNotify(reminder: LocationReminder, entering: Boolean, nowMillis: Long): Boolean {
        if (!reminder.enabled) return false
        if (nowMillis - reminder.lastFiredAt < COOLDOWN_MILLIS) return false
        return when (reminder.trigger) {
            PlaceTrigger.ARRIVE -> entering
            PlaceTrigger.LEAVE -> !entering
            PlaceTrigger.BOTH -> true
        }
    }

    fun afterFired(reminder: LocationReminder, nowMillis: Long) =
        reminder.copy(lastFiredAt = nowMillis, enabled = reminder.repeat)

    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun isInside(reminder: LocationReminder, lat: Double, lon: Double) =
        distanceMeters(reminder.latitude, reminder.longitude, lat, lon) <= reminder.radiusMeters
}
