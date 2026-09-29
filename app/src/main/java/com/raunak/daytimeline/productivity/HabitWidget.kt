package com.raunak.daytimeline.productivity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import com.raunak.daytimeline.features.HabitEngine
import com.raunak.daytimeline.features.OfflineProductivityStore
import java.time.LocalDate

/** Home-screen habits: what's due today with streaks; tap a habit to tick or untick it. */
class HabitWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getLongExtra(EXTRA_ID, 0L)
            if (id != 0L) OfflineProductivityStore(context.applicationContext).toggleHabit(id, LocalDate.now())
            refresh(context)
        } else super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.raunak.daytimeline.HABIT_TOGGLE"
        private const val EXTRA_ID = "habitId"
        private val rows = listOf(R.id.habit_row_0, R.id.habit_row_1, R.id.habit_row_2, R.id.habit_row_3, R.id.habit_row_4, R.id.habit_row_5)

        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, HabitWidget::class.java))
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val habits = runCatching { OfflineProductivityStore(app).habits.value }.getOrDefault(emptyList())
                .filter { HabitEngine.forToday(it, today) }.sortedWith(compareBy({ HabitEngine.isDone(it, today) }, HabitEngine::order))
            val views = RemoteViews(app.packageName, R.layout.widget_habits)
            val done = habits.count { HabitEngine.isDone(it, today) }
            views.setTextViewText(R.id.habit_title, "Habits today · $done/${habits.size}")
            rows.forEachIndexed { i, row ->
                val h = habits.getOrNull(i)
                if (h == null) { views.setViewVisibility(row, View.GONE); return@forEachIndexed }
                val streak = HabitEngine.streak(h, today)
                val mark = when { HabitEngine.isDone(h, today) -> "✓"; HabitEngine.isSkipped(h, today) -> "–"; else -> "○" }
                views.setViewVisibility(row, View.VISIBLE)
                views.setTextViewText(row, "$mark  ${h.name}" + if (streak.current > 0) "  · ${streak.current}🔥" else "")
                views.setTextColor(row, if (HabitEngine.isDone(h, today)) 0xFF9FB8AC.toInt() else 0xFFFFFFFF.toInt())
                val toggle = Intent(app, HabitWidget::class.java).setAction(ACTION_TOGGLE).putExtra(EXTRA_ID, h.id)
                views.setOnClickPendingIntent(row, PendingIntent.getBroadcast(app, h.id.hashCode(), toggle, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            views.setViewVisibility(R.id.habit_empty, if (habits.isEmpty()) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.habit_title, PendingIntent.getActivity(app, 7301, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}
