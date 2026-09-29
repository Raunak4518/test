package com.raunak.daytimeline.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmManagerBridge(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val skipStore = AlarmSkipStore(context)

    fun schedule(config: AlarmPersistentConfig) {
        if (!config.enabled) {
            cancel(config.id)
            return
        }
        cancelScheduledCycle(config.id)
        cancelWakeChecks(config.id)
        if (skipStore.consumeIfSkipped(config.id)) return

        val primary = AlarmSchedulePlanner.nextOccurrence(config)
        set(primary, requestCode(config.id, 0), config.id, AlarmKind.PRIMARY)
        AlarmSchedulePlanner.backupAt(primary, config)?.let {
            set(it, requestCode(config.id, 1), config.id, AlarmKind.BACKUP)
        }
        AlarmSchedulePlanner.bedtimeAt(primary, config)?.let {
            if (it > System.currentTimeMillis()) set(it, requestCode(config.id, 3), config.id, AlarmKind.BEDTIME)
        }
    }

    fun scheduleWakeChecksAfterDismissal(config: AlarmPersistentConfig, dismissedAt: Long = System.currentTimeMillis()) {
        cancelWakeChecks(config.id)
        AlarmSchedulePlanner.wakeChecksAfterDismissal(dismissedAt, config).forEachIndexed { index, at ->
            set(at, requestCode(config.id, 20 + index), config.id, AlarmKind.WAKE_CHECK, index)
        }
    }

    fun scheduleSnooze(config: AlarmPersistentConfig, minutes: Int = config.snoozeMinutes) {
        val delay = minutes.coerceIn(1, 60)
        set(
            System.currentTimeMillis() + delay * 60_000L,
            requestCode(config.id, 10),
            config.id,
            AlarmKind.SNOOZE
        )
    }

    fun cancel(id: Long) {
        cancelScheduledCycle(id)
        cancelSnooze(id)
        cancelWakeChecks(id)
        AlarmRuntimeStore(context).clear(id)
    }

    fun cancelScheduledCycle(id: Long) {
        listOf(0, 1, 3).forEach { cancelSlot(id, it) }
    }

    fun cancelSnooze(id: Long) = cancelSlot(id, 10)

    fun cancelWakeChecks(id: Long) {
        (20..30).forEach { cancelSlot(id, it) }
    }

    fun skipNext(config: AlarmPersistentConfig) {
        skipStore.setSkipNext(config.id)
        cancelScheduledCycle(config.id)
    }

    private fun cancelSlot(id: Long, slot: Int) {
        val p = pending(id, slot)
        alarmManager.cancel(p)
        p.cancel()
    }

    private fun set(at: Long, code: Int, id: Long, kind: AlarmKind, attempt: Int = 0) {
        if (at <= System.currentTimeMillis()) return
        val p = pending(id, code, kind, attempt)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else if (kind == AlarmKind.PRIMARY || kind == AlarmKind.BACKUP || kind == AlarmKind.SNOOZE) {
                // Alarm-clock alarms are the most reliable kind: exempt from Doze and shown in the status bar.
                val show = PendingIntent.getActivity(context, 0, Intent(context, com.raunak.daytimeline.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p)
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private fun pending(id: Long, code: Int, kind: AlarmKind = AlarmKind.PRIMARY, attempt: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(id, code),
            Intent(context, AlarmTriggerReceiver::class.java).apply {
                putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, id)
                putExtra(AlarmTriggerReceiver.EXTRA_KIND, kind.name)
                putExtra(AlarmTriggerReceiver.EXTRA_ATTEMPT, attempt)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(id: Long, slot: Int) = ((id xor (id ushr 32)).toInt() * 31) + slot
}

enum class AlarmKind { PRIMARY, BACKUP, WAKE_CHECK, BEDTIME, SNOOZE }
