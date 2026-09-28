package com.raunak.daytimeline.alarm

object AlarmMissionCatalog {
    val all = AlarmMissionType.entries.toList()

    fun default(type: AlarmMissionType, difficulty: Int = 2): AlarmMission = when (type) {
        AlarmMissionType.MATH -> AlarmMission(type, difficulty)
        AlarmMissionType.TYPING -> AlarmMission(type, difficulty, payload = AlarmChallengeEngine.typing(difficulty))
        AlarmMissionType.MEMORY -> AlarmMission(type, difficulty, payload = AlarmChallengeEngine.memorySequence(difficulty).joinToString(","))
        AlarmMissionType.SHAKE -> AlarmMission(type, difficulty, AlarmChallengeEngine.shakeTarget(difficulty))
        AlarmMissionType.SQUAT -> AlarmMission(type, difficulty, AlarmChallengeEngine.squatTarget(difficulty))
        AlarmMissionType.WALK -> AlarmMission(type, difficulty, AlarmChallengeEngine.walkTarget(difficulty))
        AlarmMissionType.PHOTO -> AlarmMission(type, difficulty, payload = "registered_photo")
        AlarmMissionType.BARCODE -> AlarmMission(type, difficulty, payload = "registered_barcode")
        AlarmMissionType.MULTI -> AlarmMission(type, difficulty)
    }
}

/** Local-only safety policy: the app never blocks Android emergency controls,
 * never disables uninstall globally, and never uses money/penalties. */
object AlarmSafetyPolicy {
    const val MAX_MISSION_MINUTES = 15
    const val MAX_SNOOZES = 20
    const val MAX_WAKE_CHECK_RETRIES = 10

    fun validate(profile: AlarmProfile): AlarmProfile = profile.copy(
        snoozeMinutes = profile.snoozeMinutes.coerceIn(1, 60),
        maxSnoozes = profile.maxSnoozes.coerceIn(0, MAX_SNOOZES),
        wakeCheckMinutes = profile.wakeCheckMinutes.coerceIn(0, 120),
        wakeCheckTimeoutMinutes = profile.wakeCheckTimeoutMinutes.coerceIn(1, 30),
        timeoutMinutes = profile.timeoutMinutes.coerceIn(1, 60),
        backupDelayMinutes = profile.backupDelayMinutes.coerceIn(0, 60)
    )
}
