package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import java.time.LocalDate
import java.time.LocalTime

/** Home-screen card: next class with room, overall attendance, today's practice and the next deadline. */
class CampusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, CampusWidget::class.java))
            if (ids.isEmpty()) return
            val store = CampusStore.get(app)
            val data = store.data.value
            val today = LocalDate.now()
            val minute = LocalTime.now().let { it.hour * 60 + it.minute }
            val next = AttendanceEngine.occurrences(data, today).firstOrNull { it.end > minute }
                ?: AttendanceEngine.occurrences(data, today.plusDays(1)).firstOrNull()
            val subject = next?.let { o -> data.subjects.firstOrNull { it.id == o.subjectId } }
            val views = RemoteViews(app.packageName, R.layout.widget_campus)
            views.setTextViewText(R.id.campus_title, when {
                next == null -> "No classes today or tomorrow"
                next.date != today -> "Tomorrow"
                next.start <= minute -> "Now · until ${clock(next.end)}"
                else -> "Next · in ${hm(next.start - minute)}"
            })
            views.setTextViewText(R.id.campus_next, next?.let { "${clock(it.start)} ${subject?.name ?: ""}" + if (it.room.isNotBlank()) " · ${it.room}" else "" } ?: "Free — plan library time")
            val stats = data.subjects.map { AttendanceEngine.subjectStats(data, it, today, minute) }
            val low = stats.filter { it.mustAttend > 0 }
            val sheets = store.sheets.value
            val due = data.deadlines.filter { !it.done && (daysUntil(it.date, today) ?: -1) >= 0 }.minByOrNull { it.date + clock(it.minute) }
            views.setTextViewText(
                R.id.campus_meta,
                listOfNotNull(
                    if (stats.isEmpty()) null else "Attendance ${"%.0f".format(AttendanceEngine.overall(stats))}%" + if (low.isNotEmpty()) " · ${low.size} below" else "",
                    if (sheets.isEmpty()) null else "Solved today ${SheetEngine.doneOn(sheets, today)}",
                    due?.let { "Due: ${it.title} (${dueLabel(it, today)})" }
                ).joinToString("\n")
            )
            views.setOnClickPendingIntent(R.id.campus_root, PendingIntent.getActivity(app, 44, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}
