package com.raunak.daytimeline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import com.raunak.daytimeline.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DayTimelineWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    override fun onEnabled(context: Context) = Unit

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DayTimelineWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_day_timeline)
            views.setTextViewText(R.id.widget_date, LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")))
            views.setTextViewText(R.id.widget_time, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            val launch = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launch)
            views.setOnClickPendingIntent(R.id.widget_open, launch)
            manager.updateAppWidget(id, views)
            CoroutineScope(Dispatchers.IO).launch {
                val tasks = AppDatabase.get(context).taskDao().forExactDate(LocalDate.now().toEpochDay())
                val minute = LocalTime.now().hour * 60 + LocalTime.now().minute
                val current = tasks.firstOrNull { minute in it.startMinute until it.endMinute }
                    ?: tasks.firstOrNull { it.startMinute > minute }
                val title = current?.let { "${it.title} · ${it.startMinute / 60}:${(it.startMinute % 60).toString().padStart(2, '0')}" }
                    ?: "No upcoming task"
                val fresh = RemoteViews(context.packageName, R.layout.widget_day_timeline)
                fresh.setTextViewText(R.id.widget_date, LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")))
                fresh.setTextViewText(R.id.widget_time, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                fresh.setTextViewText(R.id.widget_task, title)
                fresh.setOnClickPendingIntent(R.id.widget_root, launch)
                fresh.setOnClickPendingIntent(R.id.widget_open, launch)
                manager.updateAppWidget(id, fresh)
            }
        }
    }
}
