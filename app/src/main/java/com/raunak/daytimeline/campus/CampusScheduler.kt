package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.alarm.AlarmManagerBridge
import com.raunak.daytimeline.alarm.AlarmMissionCatalog
import com.raunak.daytimeline.alarm.AlarmMissionType
import com.raunak.daytimeline.alarm.AlarmNotificationHelper
import com.raunak.daytimeline.alarm.AlarmPersistentConfig
import com.raunak.daytimeline.alarm.AlarmPersistentStore
import com.raunak.daytimeline.alarm.AlarmRuntimeStore
import com.raunak.daytimeline.alarm.AlarmScheduleMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns the timetable into reminders: class alerts, "did you attend?" prompts, the first-class wake
 * alarm with night battery checks, deadline and interview reminders, library closing and the daily
 * check-in. Recomputed whenever data changes, at 00:05, 20:00 and after reboot.
 */
object CampusScheduler {
    const val WAKE_ALARM_ID = 770_001L
    private const val PREFS = "chronora_campus_sched"

    data class WakePlan(val at: LocalDateTime, val label: String, val firstClass: ClassOccurrence?)

    fun nextWake(data: CampusData, now: LocalDateTime): WakePlan? {
        val w = data.wake
        if (!w.enabled) return null
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val first = AttendanceEngine.firstClass(data, date)
            val base = when {
                first != null -> first.start - w.minutesBeforeFirstClass
                w.freeDayWake != null -> w.freeDayWake
                else -> continue
            }
            // Leave time for breakfast at the mess when it's served before class.
            val breakfast = Mess.breakfastWake(data, date, first?.let { it.start - w.leaveMinutes })
            val minute = (if (breakfast != null && breakfast.second < base) breakfast.second else base).coerceIn(0, 24 * 60 - 1)
            val at = date.atStartOfDay().plusMinutes(minute.toLong())
            if (at.isAfter(now)) {
                val meal = breakfast?.takeIf { it.second <= base }?.first?.let { " · ${it.name} ${clock(it.start)}–${clock(it.end)}" } ?: ""
                val label = (first?.let { o -> "First class: ${data.subjects.firstOrNull { it.id == o.subjectId }?.name ?: "Class"} at ${clock(o.start)}" + if (o.room.isNotBlank()) " · ${o.room}" else "" }
                    ?: "Free day — get to the library") + meal
                return WakePlan(at, label, first)
            }
        }
        return null
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        runCatching { scheduleWake(app) }
        runCatching { scheduleReminders(app) }
        runCatching { CampusWidget.refresh(app) }
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun scheduleWake(context: Context) {
        val data = CampusStore.get(context).data.value
        val store = AlarmPersistentStore(context)
        val bridge = AlarmManagerBridge(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val plan = nextWake(data, LocalDateTime.now())
        if (plan == null) {
            store.find(WAKE_ALARM_ID)?.let { bridge.cancel(WAKE_ALARM_ID); store.save(it.copy(enabled = false)) }
            prefs.edit().remove("wake_at").apply()
            DirectBootWake.save(context, 0, "")
            return
        }
        val at = millis(plan.at)
        val w = data.wake
        val missions = usableMissions(context, w.missions.mapNotNull { runCatching { AlarmMissionType.valueOf(it) }.getOrNull() })
        val config = (store.find(WAKE_ALARM_ID) ?: AlarmPersistentConfig(WAKE_ALARM_ID, 7, 0)).copy(
            hour = plan.at.hour,
            minute = plan.at.minute,
            label = plan.label,
            enabled = true,
            repeatDays = emptySet(),
            scheduleMode = AlarmScheduleMode.ONE_SHOT.name,
            anchorDate = plan.at.toLocalDate().toString(),
            missionChain = missions.map { AlarmMissionCatalog.default(it) },
            backupAlarmEnabled = w.backupMinutes > 0,
            backupDelayMinutes = w.backupMinutes.coerceAtLeast(1),
            wakeCheckMinutes = w.wakeCheckMinutes,
            bedtimeReminderMinutes = (w.sleepHours * 60).coerceIn(0, 720),
            maxSnoozes = data.settings.wakeSnoozes,
            longPressMs = data.settings.wakeHoldToDismissSeconds * 1000L,
            snoozeMinutes = data.settings.wakeSnoozeMinutes,
            fullscreen = true,
            deleteAfterRinging = false
        )
        store.save(config)
        // Rescheduling cancels pending wake-checks, so only do it when the target actually changes.
        if (prefs.getLong("wake_at", 0) != at || store.find(WAKE_ALARM_ID)?.label != config.label) {
            bridge.schedule(config)
            prefs.edit().putLong("wake_at", at).putString("wake_label", plan.label).apply()
            // Keep the "are you really awake?" checks from a wake-up that was just dismissed.
            val dismissed = AlarmRuntimeStore(context).dismissedAt(WAKE_ALARM_ID)
            if (config.wakeCheckMinutes > 0 && System.currentTimeMillis() - dismissed < 60 * 60_000L) bridge.scheduleWakeChecksAfterDismissal(config, dismissed)
        }
        DirectBootWake.save(context, at, plan.label, data.settings.missedAlarmRecoveryHours)
    }

    /**
     * Never arm a challenge that can't be completed on this phone: walking needs a step sensor and
     * the activity permission, photo/barcode need a registered reference. Anything else falls back.
     */
    fun usableMissions(context: Context, wanted: List<AlarmMissionType>): List<AlarmMissionType> {
        val sensors = context.getSystemService(android.hardware.SensorManager::class.java)
        val hasSteps = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR) != null || sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
        val stepPermission = Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasAccel = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null
        val refs = com.raunak.daytimeline.alarm.AlarmReferenceStore(context)
        return wanted.mapNotNull { m ->
            when (m) {
                AlarmMissionType.WALK -> if (hasSteps && stepPermission) m else if (hasAccel) AlarmMissionType.SQUAT else AlarmMissionType.TYPING
                AlarmMissionType.SQUAT, AlarmMissionType.SHAKE -> if (hasAccel) m else AlarmMissionType.TYPING
                AlarmMissionType.PHOTO -> m.takeIf { refs.hasPhoto(WAKE_ALARM_ID) }
                AlarmMissionType.BARCODE -> m.takeIf { refs.barcode(WAKE_ALARM_ID) != null }
                AlarmMissionType.MULTI -> null
                else -> m
            }
        }.distinct().ifEmpty { listOf(AlarmMissionType.MATH) }
    }

    /** Called after the wake alarm is dismissed: log the wake time and arm the next day. */
    fun onWakeDismissed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = prefs.getLong("wake_at", 0)
        if (target > 0) {
            val date = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            val store = CampusStore.get(context)
            store.update { d -> d.copy(wakeLogs = (d.wakeLogs.filterNot { it.date == date } + WakeLog(date, target, System.currentTimeMillis())).takeLast(120)) }
        }
        prefs.edit().remove("wake_at").apply()
        scheduleWake(context)
    }

    /** After boot: ring immediately if the phone was off when the wake alarm was due. */
    fun onBoot(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = prefs.getLong("wake_at", 0)
        val now = System.currentTimeMillis()
        val dismissed = AlarmRuntimeStore(context).dismissedAt(WAKE_ALARM_ID)
        val recovery = CampusStore.get(context).data.value.settings.missedAlarmRecoveryHours * 3_600_000L
        if (target in 1 until now && now - target < recovery && dismissed < target && DirectBootWake.dismissedAt(context) < target) {
            AlarmPersistentStore(context).find(WAKE_ALARM_ID)?.let {
                AlarmNotificationHelper.showAlarm(context, it.copy(label = "Missed while your phone was off! " + it.label))
            }
        }
        DirectBootWake.cancelFallback(context)
        rescheduleAll(context)
    }

    // ------------------------------------------------------------------ reminders

    private fun scheduleReminders(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet("codes", emptySet())!!.forEach { entry ->
            val code = entry.substringBefore('|').toIntOrNull() ?: return@forEach
            val action = entry.substringAfter('|', "")
            am.cancel(PendingIntent.getBroadcast(context, code, Intent(context, CampusReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val codes = mutableSetOf<String>()
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        val store = CampusStore.get(context)
        val data = store.data.value
        val w = data.wake
        fun set(at: LocalDateTime, action: String, key: String, extras: Intent.() -> Unit = {}) {
            val ms = millis(at)
            if (ms <= nowMs) return
            val code = (action + key).hashCode()
            val pi = PendingIntent.getBroadcast(context, code, Intent(context, CampusReceiver::class.java).setAction(action).putExtra("key", key).apply(extras), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            codes += "$code|$action"
        }

        for (date in listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))) {
            for (o in AttendanceEngine.occurrences(data, date)) {
                val start = date.atStartOfDay().plusMinutes(o.start.toLong())
                val lead = maxOf(w.classReminderMinutes, w.leaveMinutes)
                if (lead > 0) set(start.minusMinutes(lead.toLong()), ACTION_CLASS, o.key)
                if (w.askAttendanceAfterClass && data.marks[o.key] == null) set(date.atStartOfDay().plusMinutes(o.end.toLong() + 2), ACTION_ASK, o.key)
            }
            if (!data.settings.mealReminderOff) Mess.meals(data, date).forEach { m ->
                val lead = data.settings.mealReminderMinutes.coerceAtMost(m.end - m.start)
                set(date.atStartOfDay().plusMinutes((m.end - lead).toLong()), ACTION_MEAL, "$date|${m.name}")
            }
        }
        data.deadlines.filter { !it.done }.forEach { d ->
            val due = runCatching { LocalDate.parse(d.date).atStartOfDay().plusMinutes(d.minute.toLong()) }.getOrNull() ?: return@forEach
            data.settings.deadlineReminderHours.forEach { h -> set(due.minusHours(h.toLong()), ACTION_DEADLINE, "${d.id}|$h") }
        }
        store.companies.value.forEach { c ->
            val at = c.nextDate?.let { runCatching { LocalDate.parse(it).atStartOfDay().plusMinutes(c.nextMinute.toLong()) }.getOrNull() } ?: return@forEach
            data.settings.companyReminderHours.forEach { h -> set(at.minusHours(h.toLong()), ACTION_COMPANY, "${c.id}|$h") }
        }
        store.companies.value.filter { data.settings.placementStages.indexOfFirst { s -> s.equals(it.stageName, true) } <= 0 }.forEach { c ->
            val by = c.applyBy?.let { runCatching { LocalDate.parse(it).atTime(9, 0) }.getOrNull() } ?: return@forEach
            listOf(48, 0).forEach { h -> set(by.minusHours(h.toLong()), ACTION_COMPANY, "${c.id}|apply$h") }
        }
        if (data.librarySessions.lastOrNull()?.end == null && data.librarySessions.isNotEmpty()) {
            LibraryPlanner.openWindow(data.library, now.toLocalDate())?.let { set(now.toLocalDate().atStartOfDay().plusMinutes(it.end - data.settings.libraryCloseReminderMinutes.toLong()), ACTION_LIBRARY, now.toLocalDate().toString()) }
        }
        // Night battery guard: from the sleep reminder until the alarm, at the configured interval.
        nextWake(data, now)?.let { plan ->
            val from = plan.at.minusHours(w.sleepHours.toLong()).minusMinutes(60)
            var t = if (from.isAfter(now)) from else now.plusMinutes(1)
            var n = 0
            val every = data.settings.batteryCheckEveryMinutes.coerceAtLeast(5).toLong()
            while (t.isBefore(plan.at) && n < 48) { set(t, ACTION_BATTERY, "b$n"); t = t.plusMinutes(every); n++ }
        }
        val disc = DisciplineStore.get(context).state.value
        val checkIn = disc.settings.checkInMinute
        if (disc.started > 0 && disc.dailyCheckIn) set(now.toLocalDate().atStartOfDay().plusMinutes(checkIn.toLong()).let { if (it.isAfter(now)) it else it.plusDays(1) }, ACTION_CHECKIN, "daily")
        // Recompute daily so tomorrow's classes and alarm are always armed.
        set(now.toLocalDate().plusDays(1).atTime(0, 5), ACTION_RECOMPUTE, "midnight")
        set(now.toLocalDate().atTime(20, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }, ACTION_RECOMPUTE, "evening")
        prefs.edit().putStringSet("codes", codes).apply()
    }

    const val ACTION_CLASS = "chronora.campus.CLASS"
    const val ACTION_ASK = "chronora.campus.ASK"
    const val ACTION_MARK = "chronora.campus.MARK"
    const val ACTION_DEADLINE = "chronora.campus.DEADLINE"
    const val ACTION_COMPANY = "chronora.campus.COMPANY"
    const val ACTION_LIBRARY = "chronora.campus.LIBRARY"
    const val ACTION_MEAL = "chronora.campus.MEAL"
    const val ACTION_BATTERY = "chronora.campus.BATTERY"
    const val ACTION_CHECKIN = "chronora.campus.CHECKIN"
    const val ACTION_CHECKIN_ANSWER = "chronora.campus.CHECKIN_ANSWER"
    const val ACTION_RECOMPUTE = "chronora.campus.RECOMPUTE"
    const val ACTION_CANCEL_CLASS = "chronora.campus.CANCEL_CLASS"
}

class CampusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = CampusStore.get(context)
        val data = store.data.value
        val key = intent.getStringExtra("key").orEmpty()
        val nm = context.getSystemService(NotificationManager::class.java)
        channels(context)
        when (intent.action) {
            CampusScheduler.ACTION_CLASS -> occurrence(data, key)?.let { (o, s) ->
                val minutes = maxOf(data.wake.classReminderMinutes, data.wake.leaveMinutes)
                val b = builder(context, CH_CLASS, "${s.name} in $minutes min — leave now", listOf(o.type.label, o.room.ifBlank { null }, "${clock(o.start)}–${clock(o.end)}", o.note.ifBlank { null }).filterNotNull().joinToString(" · "))
                b.addAction(0, "Cancelled", PendingIntent.getBroadcast(context, (key + "cancel").hashCode(), Intent(context, CampusReceiver::class.java).setAction(CampusScheduler.ACTION_CANCEL_CLASS).putExtra("key", key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                nm.notify(key.hashCode(), b.build())
            }
            CampusScheduler.ACTION_CANCEL_CLASS -> occurrence(data, key)?.let { (o, _) ->
                if (o.source == "Regular" && o.slotId != null) store.update { d -> d.copy(exceptions = d.exceptions + ScheduleException(store.nextId(), ExceptionKind.CANCEL, o.date.toString(), slotId = o.slotId, note = "Cancelled from reminder")) }
                else store.mark(key, Mark.NO_CLASS)
                nm.cancel(key.hashCode())
            }
            CampusScheduler.ACTION_ASK -> occurrence(data, key)?.let { (o, s) ->
                if (data.marks[key] != null) return@let
                val stats = AttendanceEngine.subjectStats(data, s, o.date)
                val b = builder(context, CH_ATTEND, "Did you attend ${s.name}?", "Now ${"%.1f".format(stats.percent)}% · ${stats.status}")
                listOf(Mark.PRESENT, Mark.ABSENT, Mark.NO_CLASS).forEach { m ->
                    b.addAction(0, m.label, PendingIntent.getBroadcast(context, (key + m.name).hashCode(), Intent(context, CampusReceiver::class.java).setAction(CampusScheduler.ACTION_MARK).putExtra("key", key).putExtra("mark", m.name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
                nm.notify(key.hashCode(), b.build())
            }
            CampusScheduler.ACTION_MARK -> {
                val mark = runCatching { Mark.valueOf(intent.getStringExtra("mark") ?: "") }.getOrNull() ?: return
                store.mark(key, mark)
                nm.cancel(key.hashCode())
            }
            CampusScheduler.ACTION_DEADLINE -> {
                val (id, lead) = key.split('|').let { it[0].toLongOrNull() to it.getOrNull(1) }
                data.deadlines.firstOrNull { it.id == id && !it.done }?.let { d ->
                    val hours = lead?.toIntOrNull() ?: 0
                    notify(context, CH_DEADLINE, key.hashCode(), "${d.label} due in ${if (hours >= 24 && hours % 24 == 0) "${hours / 24} day(s)" else "$hours hour(s)"}: ${d.title}", "${d.date} ${clock(d.minute)}" + (d.subjectId?.let { sid -> " · " + (data.subjects.firstOrNull { it.id == sid }?.name ?: "") } ?: ""))
                }
            }
            CampusScheduler.ACTION_COMPANY -> {
                val id = key.substringBefore('|').toLongOrNull()
                store.companies.value.firstOrNull { it.id == id }?.let { c ->
                    val tail = key.substringAfter('|')
                    if (tail.startsWith("apply")) notify(context, CH_DEADLINE, key.hashCode(), "Apply to ${c.name}" + (if (tail == "apply0") " today" else " — closes ${c.applyBy}"), c.role.ifBlank { "Placement application" })
                    else {
                        val hours = tail.toIntOrNull() ?: 0
                        notify(context, CH_DEADLINE, key.hashCode(), "${c.name}: ${c.nextEvent.ifBlank { c.stageName }} in $hours hour(s)", "${c.nextDate} ${clock(c.nextMinute)} · ${c.role}")
                    }
                }
            }
            CampusScheduler.ACTION_MEAL -> {
                val name = key.substringAfter('|')
                data.settings.meals.firstOrNull { it.name == name }?.let { m ->
                    notify(context, CH_CLASS, key.hashCode(), "Mess: $name closes at ${clock(m.end)}", "${data.settings.mealReminderMinutes} minutes left — go eat now.")
                }
            }
            CampusScheduler.ACTION_LIBRARY -> notify(context, CH_CLASS, 7710, "Library closes in ${data.settings.libraryCloseReminderMinutes} minutes", "Wrap up and note where to resume tomorrow.")
            CampusScheduler.ACTION_BATTERY -> batteryCheck(context)
            CampusScheduler.ACTION_CHECKIN -> {
                val b = builder(context, CH_CHECKIN, "Daily check-in", "How did today go?")
                listOf("kept" to "Stayed on track", "reset" to "Slipped").forEach { (v, label) ->
                    b.addAction(0, label, PendingIntent.getBroadcast(context, ("ci$v").hashCode(), Intent(context, CampusReceiver::class.java).setAction(CampusScheduler.ACTION_CHECKIN_ANSWER).putExtra("v", v), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
                nm.notify(7720, b.build())
            }
            CampusScheduler.ACTION_CHECKIN_ANSWER -> {
                val ds = DisciplineStore.get(context)
                if (intent.getStringExtra("v") == "reset") ds.update { DisciplineEngine.reset(it, System.currentTimeMillis(), "", "") }
                ds.checkIn(LocalDate.now(), intent.getStringExtra("v") != "reset")
                nm.cancel(7720)
            }
        }
        CampusScheduler.rescheduleAll(context)
    }

    private fun occurrence(data: CampusData, key: String): Pair<ClassOccurrence, Subject>? {
        val date = runCatching { LocalDate.parse(key.substringBefore('|')) }.getOrNull() ?: return null
        val o = AttendanceEngine.occurrences(data, date).firstOrNull { it.key == key } ?: return null
        val s = data.subjects.firstOrNull { it.id == o.subjectId } ?: return null
        return o to s
    }

    private fun batteryCheck(context: Context) {
        val data = CampusStore.get(context).data.value
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        if (!charging && level in 0 until data.wake.batteryThreshold) {
            val plan = CampusScheduler.nextWake(data, LocalDateTime.now())
            val b = builder(context, CH_BATTERY, "Plug in your phone — $level% battery", "Your wake alarm at ${plan?.at?.toLocalTime()?.withSecond(0) ?: "tomorrow"} won't ring if the phone dies overnight.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
            context.getSystemService(NotificationManager::class.java).notify(7730, b.build())
        }
    }

    private fun builder(context: Context, channel: String, title: String, text: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_menu_agenda)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String) =
        context.getSystemService(NotificationManager::class.java).notify(id, builder(context, channel, title, text).build())

    companion object {
        const val CH_CLASS = "campus_classes"
        const val CH_ATTEND = "campus_attendance"
        const val CH_DEADLINE = "campus_deadlines"
        const val CH_BATTERY = "campus_battery"
        const val CH_CHECKIN = "campus_checkin"

        fun channels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_CLASS, "Class reminders", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_ATTEND, "Attendance prompts", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_DEADLINE, "Deadlines & interviews", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_BATTERY, "Night battery guard", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_CHECKIN, "Daily check-in", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
