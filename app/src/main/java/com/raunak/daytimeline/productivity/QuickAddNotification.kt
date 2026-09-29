package com.raunak.daytimeline.productivity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.raunak.daytimeline.AppContainer
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.domain.QuickAddParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A quiet, pinned notification with a text box: type "DSA revision tomorrow 7pm #study" to add a task from anywhere. */
object QuickAddNotification {
    private const val CHANNEL = "quick_add"
    private const val ID = 7_310_001
    const val KEY = "quick_add_text"

    fun show(context: Context, last: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Quick add", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val input = RemoteInput.Builder(KEY).setLabel("e.g. ML notes tomorrow 6pm #study !2").build()
        val reply = PendingIntent.getBroadcast(context, ID, Intent(context, QuickAddReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
        val open = PendingIntent.getActivity(context, ID + 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("Add a task")
            .setContentText(last ?: "Type in plain words — date, time, #tags, !priority, repeat")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .addAction(NotificationCompat.Action.Builder(0, "Add task", reply).addRemoteInput(input).setAllowGeneratedReplies(false).build())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }

    fun hide(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}

class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(QuickAddNotification.KEY)?.toString()?.trim().orEmpty()
        if (text.isBlank()) { QuickAddNotification.show(context); return }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parsed = QuickAddParser.parse(text, LocalDate.now())
                AppContainer(context).repository.quickAdd(text, LocalDate.now())
                QuickAddNotification.show(context, parsed?.let { "Added: ${it.title} · ${it.date} %02d:%02d".format(it.startMinute / 60, it.startMinute % 60) } ?: "Added")
            } finally { pending.finish() }
        }
    }
}
