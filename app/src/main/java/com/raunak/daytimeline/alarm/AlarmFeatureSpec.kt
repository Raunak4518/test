package com.raunak.daytimeline.alarm

/** Feature contract used by the alarm UI and persistence layer. */
data class AlarmFeatureState(
    val smartWakeWindowMinutes: Int = 0,
    val gentleVolumeSeconds: Int = 0,
    val vibration: Boolean = true,
    val fullscreen: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 0,
    val longPressMs: Long = 0,
    val wakeCheckMinutes: Int = 0,
    val wakeCheckTimeoutMinutes: Int = 5,
    val backupAlarmEnabled: Boolean = false,
    val backupDelayMinutes: Int = 5,
    val bedtimeReminderMinutes: Int = 0,
    val timeoutMinutes: Int = 10,
    val flipToSnooze: Boolean = false,
    val preventOrientationChanges: Boolean = true,
    val mission: AlarmMission = AlarmChallengeEngine.default(AlarmMissionType.MATH),
    val missionChain: List<AlarmMission> = emptyList(),
    val napMode: Boolean = false,
    val deleteAfterRing: Boolean = false
)

/**
 * Offline alarm roadmap represented as concrete capabilities rather than UI placeholders:
 * exact scheduling, repeats, per-alarm overrides, gentle volume, vibration, fullscreen,
 * snooze limits, long-press dismissal, mission chains, math, typing, memory, shake,
 * squat, walking, photo/barcode registration, wake-up checks, backup alarms, bedtime
 * reminders, naps, labels, custom sounds, and recovery after reboot/time changes.
 * Smart sleep-phase wake requires actual sleep sensing data and is intentionally not
 * fabricated from a simple timer.
 */
object AlarmFeatureContract {
    val supportedOffline = listOf(
        "exact_alarm", "repeat_rules", "per_alarm_settings", "snooze_limits",
        "gentle_volume", "vibration", "fullscreen", "long_press_dismiss",
        "math_mission", "typing_mission", "memory_mission", "shake_mission",
        "squat_mission", "walking_mission", "photo_mission", "barcode_mission",
        "mission_chain", "wake_up_check", "backup_alarm", "bedtime_reminder",
        "nap_alarm", "alarm_timeout", "flip_to_snooze", "orientation_lock",
        "boot_recovery", "timezone_recovery", "custom_label", "custom_sound"
    )
}
