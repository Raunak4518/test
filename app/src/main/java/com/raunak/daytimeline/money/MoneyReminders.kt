package com.raunak.daytimeline.money

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Lets a notification open the Money page. */
object MoneyNav { val open = kotlinx.coroutines.flow.MutableStateFlow(false) }

/** Morning check (recurring payments, budget alerts) and the evening "log today" nudge. */
object MoneyReminders {
    private const val CHANNEL = "money"
    const val ACTION_MORNING = "com.raunak.daytimeline.MONEY_MORNING"
    const val ACTION_EVENING = "com.raunak.daytimeline.MONEY_EVENING"
    const val ACTION_PAY = "com.raunak.daytimeline.MONEY_PAY"
    const val ACTION_SKIP = "com.raunak.daytimeline.MONEY_SKIP"
    const val ACTION_ADD_DETECTED = "com.raunak.daytimeline.MONEY_ADD_DETECTED"
    const val ACTION_IGNORE_DETECTED = "com.raunak.daytimeline.MONEY_IGNORE_DETECTED"

    private fun pi(context: Context, code: Int, action: String, extras: Intent.() -> Unit = {}) = PendingIntent.getBroadcast(context, code,
        Intent(context, MoneyReceiver::class.java).setAction(action).apply(extras), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun scheduleAll(context: Context) {
        val s = MoneyStore.get(context).data.value.settings
        at(context, 9 * 60, pi(context, 5101, ACTION_MORNING))
        if (s.reminderOn) at(context, s.reminderMinute, pi(context, 5102, ACTION_EVENING))
        else runCatching { context.getSystemService(AlarmManager::class.java).cancel(pi(context, 5102, ACTION_EVENING)) }
    }

    private fun at(context: Context, minute: Int, p: PendingIntent) {
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!t.isAfter(now)) t = t.plusDays(1)
        runCatching { context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), p) }
    }

    private fun channel(context: Context) = context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Money", NotificationManager.IMPORTANCE_DEFAULT))

    private fun open(context: Context) = PendingIntent.getActivity(context, 5199, Intent(context, MainActivity::class.java).putExtra("chronora.open", "money")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun post(context: Context, id: Int, title: String, text: String, build: NotificationCompat.Builder.() -> Unit = {}) {
        channel(context)
        val b = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open(context)).setAutoCancel(true).apply(build)
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    /** Sends any budget alert that's newly crossed and remembers it so it's sent once a month. */
    fun checkAlerts(context: Context) {
        val store = MoneyStore.get(context)
        val today = LocalDate.now()
        val d = store.data.value
        val alerts = MoneyEngine.alerts(d, today)
        if (alerts.isEmpty()) return
        alerts.forEach { (cat, p) ->
            val r = MoneyEngine.monthRange(today, d.settings.monthStartDay)
            val spent = MoneyEngine.spent(d, r, cat?.id); val budget = cat?.budget ?: d.settings.monthlyBudget
            val name = cat?.let { "${it.emoji} ${it.name}" } ?: "Monthly budget"
            post(context, 5300 + (cat?.id?.toInt() ?: 0) * 2 + if (p >= 100) 1 else 0,
                if (p >= 100) "$name: budget used up" else "$name: $p% used",
                "${MoneyEngine.format(spent, d.settings.currency)} of ${MoneyEngine.format(budget, d.settings.currency)} · ${MoneyEngine.daysLeft(d, today)} days left. " +
                    if (p >= 100) "Time to slow down." else "You can still stay on track.")
        }
        store.update { x -> x.copy(alerted = x.alerted + alerts.map { (c, p) -> MoneyEngine.alertKey(x, today, c, p) }) }
    }

    fun morning(context: Context) {
        val store = MoneyStore.get(context)
        val remind = store.processRecurring(LocalDate.now())
        val cur = store.data.value.settings.currency
        remind.forEach { (r, day) ->
            val code = (r.id % 100_000).toInt()
            post(context, 5400 + code % 500, "${if (r.type == TxnType.INCOME) "💰" else "🔁"} ${r.name} · ${MoneyEngine.format(r.amount, cur)}",
                if (r.type == TxnType.INCOME) "Did it arrive?" else "Due ${if (day == LocalDate.now()) "today" else day.toString()}. Paid it?") {
                addAction(0, if (r.type == TxnType.INCOME) "Got it" else "Paid", pi(context, 6000 + code % 900, ACTION_PAY) { putExtra("id", r.id); putExtra("day", day.toString()) })
                addAction(0, "Skip", pi(context, 7000 + code % 900, ACTION_SKIP) { putExtra("id", r.id); putExtra("day", day.toString()) })
            }
        }
        checkAlerts(context)
    }

    fun evening(context: Context) {
        val d = MoneyStore.get(context).data.value
        val today = LocalDate.now()
        val spent = MoneyEngine.spentOn(d, today)
        val streak = MoneyEngine.noSpendStreak(d, today)
        val safe = MoneyEngine.safeToSpendToday(d, today)
        post(context, 5500, if (spent == 0.0) "💸 Spent anything today?" else "💸 ${MoneyEngine.format(spent, d.settings.currency)} spent today",
            if (spent == 0.0) (if (streak > 0) "No spends logged. That would make ${streak + 1} no-spend days in a row 🔥" else "Log it now so your budget stays right.")
            else "Anything else to add? Tomorrow's safe-to-spend: ${MoneyEngine.format(MoneyEngine.safeToSpendToday(d, today.plusDays(1)).coerceAtLeast(safe), d.settings.currency)}")
    }

    /** A payment spotted in a UPI app: ask to add it. */
    fun detected(context: Context, det: Detected) {
        val cur = MoneyStore.get(context).data.value.settings.currency
        val code = (det.id % 500).toInt()
        post(context, 5600 + code, "Paid ${MoneyEngine.format(det.amount, cur)}" + if (det.payee.isNotBlank()) " to ${det.payee}" else "",
            "Seen in ${det.app}. Add it to your spending?") {
            addAction(0, "Add", pi(context, 8000 + code, ACTION_ADD_DETECTED) { putExtra("id", det.id) })
            addAction(0, "Ignore", pi(context, 8500 + code, ACTION_IGNORE_DETECTED) { putExtra("id", det.id) })
        }
    }

    /** Category this payee was filed under last time, else Other. */
    fun guessCategory(d: MoneyData, payee: String): Long =
        d.txns.lastOrNull { payee.isNotBlank() && it.note.equals(payee, true) && it.type == TxnType.EXPENSE }?.categoryId
            ?: d.categories.firstOrNull { it.name == "Other" }?.id ?: d.categories.first { !it.income }.id

    fun addDetected(context: Context, id: Long) {
        val store = MoneyStore.get(context)
        val det = store.data.value.detected.firstOrNull { it.id == id } ?: return
        val time = java.time.Instant.ofEpochMilli(det.time).atZone(ZoneId.systemDefault())
        store.update { d ->
            d.copy(txns = d.txns + Txn(store.nextId(), det.amount, TxnType.EXPENSE, guessCategory(d, det.payee), d.wallets.first().id, date = time.toLocalDate().toString(),
                minute = time.hour * 60 + time.minute, note = det.payee), detected = d.detected.filterNot { it.id == id })
        }
        NotificationManagerCompat.from(context).cancel(5600 + (id % 500).toInt())
        checkAlerts(context)
    }
}

class MoneyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = MoneyStore.get(context)
        when (intent.action) {
            MoneyReminders.ACTION_MORNING -> { MoneyReminders.morning(context); MoneyReminders.scheduleAll(context) }
            MoneyReminders.ACTION_EVENING -> { MoneyReminders.evening(context); MoneyReminders.scheduleAll(context) }
            MoneyReminders.ACTION_PAY, MoneyReminders.ACTION_SKIP -> {
                val r = store.data.value.recurring.firstOrNull { it.id == intent.getLongExtra("id", -1) } ?: return
                val day = runCatching { LocalDate.parse(intent.getStringExtra("day")) }.getOrDefault(LocalDate.now())
                if (intent.action == MoneyReminders.ACTION_PAY) store.payRecurring(r, day) else store.skipRecurring(r, day)
                NotificationManagerCompat.from(context).cancel(5400 + ((r.id % 100_000).toInt()) % 500)
                MoneyReminders.checkAlerts(context)
            }
            MoneyReminders.ACTION_ADD_DETECTED -> MoneyReminders.addDetected(context, intent.getLongExtra("id", -1))
            MoneyReminders.ACTION_IGNORE_DETECTED -> {
                val id = intent.getLongExtra("id", -1)
                store.update { d -> d.copy(detected = d.detected.filterNot { it.id == id }) }
                NotificationManagerCompat.from(context).cancel(5600 + (id % 500).toInt())
            }
        }
    }
}

/** Called by the notification listener for every notification: picks out UPI payments. */
object MoneyCapture {
    fun onPosted(context: Context, pkg: String, extras: android.os.Bundle, time: Long) {
        val app = UpiParser.apps[pkg] ?: return
        val store = MoneyStore.get(context)
        if (!store.data.value.settings.detectUpi) return
        val text = listOfNotNull(extras.getCharSequence("android.title"), extras.getCharSequence("android.text"), extras.getCharSequence("android.bigText")).joinToString(" ")
        val (amount, payee) = UpiParser.parse(text) ?: return
        if (store.data.value.detected.any { it.time == time }) return
        store.detect(amount, payee, app, time)
        store.data.value.detected.firstOrNull { it.time == time }?.let { MoneyReminders.detected(context, it) }
    }
}
