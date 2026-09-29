package com.raunak.daytimeline.pro

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import com.raunak.daytimeline.data.AppDatabase
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.features.OfflineProductivityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen Pomodoro: live countdown, start/pause, quick add, garden level and today's habits. */
class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        const val EXTRA_OPEN = "chronora.open"
        const val OPEN_QUICK_ADD = "quickadd"

        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, FocusWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val state = AppDatabase.get(app).pomodoroDao().current() ?: PomodoroStateEntity()
                val garden = FocusGarden.summarize(GardenStore(app).sessions(), LocalDate.now())
                val habits = runCatching { OfflineProductivityStore(app).habits.value }.getOrDefault(emptyList())
                val today = LocalDate.now().toString()
                val views = RemoteViews(app.packageName, R.layout.widget_focus)
                val phase = when (state.phase) {
                    "FOCUS" -> "Focus ${state.cycleIndex}/${state.cyclesPerRound}"
                    "SHORT_BREAK" -> "Short break"
                    "LONG_BREAK" -> "Long break"
                    else -> "Ready to focus"
                }
                views.setTextViewText(R.id.focus_phase, phase + if (state.phase != "IDLE" && !state.running) " · paused" else "")
                val remainingMs = when {
                    state.phase == "IDLE" -> state.focusMinutes * 60_000L
                    state.running -> (state.targetEpochMillis - System.currentTimeMillis()).coerceAtLeast(0)
                    else -> state.remainingSeconds * 1000
                }
                views.setChronometer(R.id.focus_timer, SystemClock.elapsedRealtime() + remainingMs, null, state.running && state.phase != "IDLE")
                views.setChronometerCountDown(R.id.focus_timer, true)
                val habitsDone = habits.count { today in it.completedDates }
                views.setTextViewText(R.id.focus_meta, "Garden lvl ${garden.level} · ${garden.todayMinutes}m today · habits $habitsDone/${habits.size}")
                views.setTextViewText(R.id.focus_toggle, if (state.running) "Pause" else if (state.phase == "IDLE") "Start" else "Resume")
                views.setOnClickPendingIntent(
                    R.id.focus_toggle,
                    PendingIntent.getForegroundService(app, 41, Intent(app, FocusSessionService::class.java).setAction(FocusSessionService.ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
                views.setOnClickPendingIntent(
                    R.id.focus_add,
                    PendingIntent.getActivity(app, 42, Intent(app, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_QUICK_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
                views.setOnClickPendingIntent(R.id.focus_root, PendingIntent.getActivity(app, 43, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(ids, views)
            }
        }
    }
}
