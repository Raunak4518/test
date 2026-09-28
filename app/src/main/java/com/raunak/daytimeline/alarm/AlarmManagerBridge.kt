package com.raunak.daytimeline.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Android scheduling boundary. All alarm decisions come from the persistent domain model. */
class AlarmManagerBridge(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(config: AlarmPersistentConfig) {
        if (!config.enabled) { cancel(config.id); return }
        val primary = AlarmSchedulePlanner.nextOccurrence(config)
        set(primary, requestCode(config.id, 0), config.id, AlarmKind.PRIMARY)
        AlarmSchedulePlanner.backupAt(primary, config)?.let { set(it, requestCode(config.id, 1), config.id, AlarmKind.BACKUP) }
        AlarmSchedulePlanner.wakeCheckAt(primary, config)?.let { set(it, requestCode(config.id, 2), config.id, AlarmKind.WAKE_CHECK) }
        AlarmSchedulePlanner.bedtimeAt(primary, config)?.let { if (it > System.currentTimeMillis()) set(it, requestCode(config.id, 3), config.id, AlarmKind.BEDTIME) }
    }

    fun scheduleSnooze(config: AlarmPersistentConfig) {
        val at = System.currentTimeMillis() + config.snoozeMinutes * 60_000L
        set(at, requestCode(config.id, 10), config.id, AlarmKind.SNOOZE)
    }

    fun cancel(id: Long) {
        listOf(0,1,2,3,10).forEach { code ->
            val pi = pending(id, code)
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun set(at: Long, requestCode: Int, alarmId: Long, kind: AlarmKind) {
        val pi = pending(alarmId, requestCode, kind)
        if (Build.VERSION.SDK_INT >= 23) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarmManager.setExact(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pending(id: Long, code: Int, kind: AlarmKind = AlarmKind.PRIMARY): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, id)
            putExtra(AlarmTriggerReceiver.EXTRA_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(context, requestCode(id, code), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun requestCode(id: Long, slot: Int): Int = ((id xor (id ushr 32)).toInt() * 31) + slot
}

enum class AlarmKind { PRIMARY, BACKUP, WAKE_CHECK, BEDTIME, SNOOZE }
