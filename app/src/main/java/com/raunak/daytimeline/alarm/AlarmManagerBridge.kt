package com.raunak.daytimeline.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

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
    fun scheduleSnooze(config: AlarmPersistentConfig) { set(System.currentTimeMillis()+config.snoozeMinutes*60_000L,requestCode(config.id,10),config.id,AlarmKind.SNOOZE) }
    fun cancel(id: Long) { listOf(0,1,2,3,10).forEach { cancelSlot(id,it) } }
    fun cancelScheduledCycle(id: Long) { listOf(0,1,2,3).forEach { cancelSlot(id,it) } }
    fun cancelSnooze(id: Long) { cancelSlot(id,10) }
    private fun cancelSlot(id: Long, slot: Int) { val p=pending(id,slot); alarmManager.cancel(p); p.cancel() }
    private fun set(at: Long, code: Int, id: Long, kind: AlarmKind) {
        val p=pending(id,code,kind)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p)
            else if (Build.VERSION.SDK_INT >= 23) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p) else alarmManager.setExact(AlarmManager.RTC_WAKEUP,at,p)
        } catch (_: SecurityException) { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p) }
    }
    private fun pending(id: Long, code: Int, kind: AlarmKind = AlarmKind.PRIMARY): PendingIntent = PendingIntent.getBroadcast(context,requestCode(id,code),Intent(context,AlarmTriggerReceiver::class.java).apply { putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID,id);putExtra(AlarmTriggerReceiver.EXTRA_KIND,kind.name) },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun requestCode(id: Long, slot: Int)=((id xor(id ushr 32)).toInt()*31)+slot
}

enum class AlarmKind { PRIMARY, BACKUP, WAKE_CHECK, BEDTIME, SNOOZE }
