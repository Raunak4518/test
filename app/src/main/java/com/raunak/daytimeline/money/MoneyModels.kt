package com.raunak.daytimeline.money

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

enum class TxnType { EXPENSE, INCOME, TRANSFER }

data class MoneyCategory(val id: Long, val name: String, val emoji: String, val color: Long, val budget: Double = 0.0, val income: Boolean = false)

data class Wallet(val id: Long, val name: String, val emoji: String, val opening: Double = 0.0)

data class Txn(
    val id: Long,
    val amount: Double,
    val type: TxnType = TxnType.EXPENSE,
    val categoryId: Long = 0,
    val walletId: Long = 0,
    /** For transfers (e.g. ATM: bank → cash). */
    val toWalletId: Long = 0,
    val date: String,
    val minute: Int = 0,
    val note: String = "",
    /** Set when this spend was split with friends; the share they owe is in [Debt]s. */
    val splitWith: List<String> = emptyList()
)

/** A repeating payment or income: recharge every 28 days, mess fee monthly, allowance on the 1st. */
data class Recurring(
    val id: Long,
    val name: String,
    val amount: Double,
    val categoryId: Long,
    val walletId: Long,
    val type: TxnType = TxnType.EXPENSE,
    /** Repeat every N days, or monthly on [dayOfMonth] when everyDays == 0. */
    val everyDays: Int = 0,
    val dayOfMonth: Int = 1,
    val nextDate: String,
    /** Add it by itself on the day, or only remind. */
    val autoAdd: Boolean = false,
    val enabled: Boolean = true
)

/** Money a friend owes you (positive) or you owe them (negative). */
data class Debt(val id: Long, val person: String, val amount: Double, val note: String, val date: String, val settled: Boolean = false)

data class SavingsGoal(val id: Long, val name: String, val emoji: String, val target: Double, val saved: Double = 0.0, val deadline: String = "")

/** Something you want to buy, held for a cooling-off period before you decide. */
data class Wish(val id: Long, val name: String, val price: Double, val addedAt: Long, val decision: String = "")

/** A one-tap spend: "Chai ₹15". */
data class QuickSpend(val label: String, val amount: Double, val categoryId: Long)

/** A payment read from a UPI app notification, waiting for the user to confirm. */
data class Detected(val id: Long, val amount: Double, val payee: String, val app: String, val time: Long)

data class MoneySettings(
    val currency: String = "₹",
    val monthlyBudget: Double = 6000.0,
    /** The month resets on this day (when the allowance arrives). */
    val monthStartDay: Int = 1,
    val reminderMinute: Int = 21 * 60 + 30,
    val reminderOn: Boolean = true,
    val alertPercent: Int = 80,
    val waitHours: Int = 24,
    val detectUpi: Boolean = true,
    val quick: List<QuickSpend> = emptyList()
)

data class MoneyData(
    val categories: List<MoneyCategory> = MoneyDefaults.categories,
    val wallets: List<Wallet> = MoneyDefaults.wallets,
    val txns: List<Txn> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    val detected: List<Detected> = emptyList(),
    val settings: MoneySettings = MoneySettings(quick = MoneyDefaults.quick),
    /** Budget alerts already sent this month: "<month>:<categoryId>:<percent>". */
    val alerted: Set<String> = emptySet()
) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized() = copy(
        categories = (categories ?: emptyList()).ifEmpty { MoneyDefaults.categories }, wallets = (wallets ?: emptyList()).ifEmpty { MoneyDefaults.wallets },
        txns = (txns ?: emptyList()).map { it.copy(note = it.note ?: "", splitWith = it.splitWith ?: emptyList(), type = it.type ?: TxnType.EXPENSE) },
        recurring = recurring ?: emptyList(), debts = debts ?: emptyList(), goals = goals ?: emptyList(), wishes = wishes ?: emptyList(),
        detected = detected ?: emptyList(), settings = (settings ?: MoneySettings()).let { it.copy(quick = it.quick ?: emptyList(), currency = it.currency ?: "₹") }, alerted = alerted ?: emptySet()
    )
}

object MoneyDefaults {
    val categories = listOf(
        MoneyCategory(1, "Food", "🍔", 0xFFEF6A45, 2000.0),
        MoneyCategory(2, "Snacks & chai", "☕", 0xFFDB8F12, 600.0),
        MoneyCategory(3, "Travel", "🛺", 0xFF2F8FE0, 800.0),
        MoneyCategory(4, "Recharge & bills", "📶", 0xFF0E9F9A),
        MoneyCategory(5, "Study", "📚", 0xFF7C5CE6, 500.0),
        MoneyCategory(6, "Laundry & room", "🧺", 0xFF5B6BB0),
        MoneyCategory(7, "Fun & outings", "🎉", 0xFFE5486B, 1000.0),
        MoneyCategory(8, "Shopping", "🛍️", 0xFFE0434C, 800.0),
        MoneyCategory(9, "Health", "💊", 0xFF22A06B),
        MoneyCategory(10, "Other", "📦", 0xFF636A7E),
        MoneyCategory(101, "Allowance", "💰", 0xFF22A06B, income = true),
        MoneyCategory(102, "Earnings", "💼", 0xFF0E9F9A, income = true)
    )
    val wallets = listOf(Wallet(1, "UPI / Bank", "🏦"), Wallet(2, "Cash", "💵"))
    val quick = listOf(QuickSpend("Chai", 15.0, 2), QuickSpend("Canteen", 60.0, 1), QuickSpend("Auto", 40.0, 3), QuickSpend("Print", 10.0, 5))
}

/** Pure money logic, kept free of Android so it can be tested. */
object MoneyEngine {
    /** The budget month that contains [d], starting on [startDay] (clamped to the month's length). */
    fun monthRange(d: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        fun startIn(m: LocalDate) = m.withDayOfMonth(startDay.coerceIn(1, m.lengthOfMonth()))
        val thisStart = startIn(d)
        val start = if (d.isBefore(thisStart)) startIn(d.minusMonths(1)) else thisStart
        val next = startIn(start.plusMonths(1).withDayOfMonth(1))
        return start to next.minusDays(1)
    }

    fun inRange(t: Txn, r: Pair<LocalDate, LocalDate>): Boolean { val d = LocalDate.parse(t.date); return !d.isBefore(r.first) && !d.isAfter(r.second) }

    fun spent(data: MoneyData, r: Pair<LocalDate, LocalDate>, categoryId: Long? = null) =
        data.txns.filter { it.type == TxnType.EXPENSE && inRange(it, r) && (categoryId == null || it.categoryId == categoryId) }.sumOf { it.amount }

    fun income(data: MoneyData, r: Pair<LocalDate, LocalDate>) = data.txns.filter { it.type == TxnType.INCOME && inRange(it, r) }.sumOf { it.amount }

    fun spentOn(data: MoneyData, d: LocalDate) = data.txns.filter { it.type == TxnType.EXPENSE && it.date == d.toString() }.sumOf { it.amount }

    /** What's left of the month's budget, spread evenly over the days left including today. */
    fun safeToSpendToday(data: MoneyData, today: LocalDate): Double {
        val r = monthRange(today, data.settings.monthStartDay)
        val spentBeforeToday = spent(data, r.first to today.minusDays(1))
        val daysLeft = ChronoUnit.DAYS.between(today, r.second) + 1
        val left = data.settings.monthlyBudget - spentBeforeToday
        return (left / daysLeft - spentOn(data, today)).coerceAtLeast(0.0).let { if (left <= 0) 0.0 else it }
    }

    fun daysLeft(data: MoneyData, today: LocalDate) = ChronoUnit.DAYS.between(today, monthRange(today, data.settings.monthStartDay).second) + 1

    /** Where the month will end at today's pace. */
    fun projected(data: MoneyData, today: LocalDate): Double {
        val r = monthRange(today, data.settings.monthStartDay)
        val days = ChronoUnit.DAYS.between(r.first, today) + 1
        val total = ChronoUnit.DAYS.between(r.first, r.second) + 1
        return spent(data, r.first to today) / days * total
    }

    /** Days in a row (ending yesterday, or today once the day is over) with no spending. Today counts if nothing spent yet only when [includeToday]. */
    fun noSpendStreak(data: MoneyData, today: LocalDate, includeToday: Boolean = false): Int {
        val first = data.txns.minOfOrNull { it.date }?.let(LocalDate::parse) ?: return 0
        var d = if (includeToday && spentOn(data, today) == 0.0) today else today.minusDays(1)
        var n = 0
        while (!d.isBefore(first) && spentOn(data, d) == 0.0) { n++; d = d.minusDays(1) }
        return n
    }

    fun balance(data: MoneyData, w: Wallet): Double = w.opening + data.txns.sumOf { t ->
        when {
            t.type == TxnType.INCOME && t.walletId == w.id -> t.amount
            t.type == TxnType.EXPENSE && t.walletId == w.id -> -t.amount
            t.type == TxnType.TRANSFER && t.walletId == w.id -> -t.amount
            t.type == TxnType.TRANSFER && t.toWalletId == w.id -> t.amount
            else -> 0.0
        }
    }

    /** Net per friend: positive = they owe you. */
    fun ledger(data: MoneyData): Map<String, Double> =
        data.debts.filter { !it.settled }.groupBy { it.person.trim() }.mapValues { (_, l) -> l.sumOf { it.amount } }.filterValues { abs(it) >= 0.5 }

    /** Splits [amount] equally between you and [friends]; returns each friend's share. */
    fun share(amount: Double, friends: Int): Double = if (friends <= 0) 0.0 else (amount / (friends + 1) * 100).roundToLong() / 100.0

    fun nextAfter(r: Recurring, from: LocalDate): LocalDate =
        if (r.everyDays > 0) from.plusDays(r.everyDays.toLong())
        else from.plusMonths(1).let { it.withDayOfMonth(r.dayOfMonth.coerceIn(1, it.lengthOfMonth())) }

    /** Recurring items whose date has come, with every missed occurrence up to [today]. */
    fun due(data: MoneyData, today: LocalDate): List<Pair<Recurring, LocalDate>> = data.recurring.filter { it.enabled }.flatMap { r ->
        val out = mutableListOf<Pair<Recurring, LocalDate>>()
        var d = LocalDate.parse(r.nextDate)
        while (!d.isAfter(today) && out.size < 12) { out += r to d; d = nextAfter(r, d) }
        out
    }

    /** Budget lines crossed this month that haven't been alerted yet: category (null = whole budget) and percent. */
    fun alerts(data: MoneyData, today: LocalDate): List<Pair<MoneyCategory?, Int>> {
        val r = monthRange(today, data.settings.monthStartDay)
        val key = r.first.toString()
        val out = mutableListOf<Pair<MoneyCategory?, Int>>()
        fun check(cat: MoneyCategory?, budget: Double, spent: Double) {
            if (budget <= 0) return
            listOf(data.settings.alertPercent, 100).distinct().forEach { p ->
                if (spent >= budget * p / 100 && "$key:${cat?.id ?: 0}:$p" !in data.alerted) out += cat to p
            }
        }
        check(null, data.settings.monthlyBudget, spent(data, r))
        data.categories.filter { !it.income }.forEach { c -> check(c, c.budget, spent(data, r, c.id)) }
        return out
    }

    fun alertKey(data: MoneyData, today: LocalDate, cat: MoneyCategory?, p: Int) = "${monthRange(today, data.settings.monthStartDay).first}:${cat?.id ?: 0}:$p"

    fun format(v: Double, currency: String = "₹"): String {
        val neg = v < 0; val a = abs(v)
        val whole = a.roundToLong()
        val body = if (abs(a - whole) < 0.005) indian(whole) else indian(a.toLong()) + "." + "%02d".format(((a - a.toLong()) * 100).roundToLong().coerceAtMost(99))
        return (if (neg) "-" else "") + currency + body
    }

    /** Indian digit grouping: 1,23,456. */
    private fun indian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3); var rest = s.dropLast(3)
        val parts = mutableListOf<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }
}

/** Reads a payment out of a UPI / bank notification ("Paid ₹120 to Swiggy", "Rs. 50.00 debited…"). */
object UpiParser {
    val apps = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "GPay", "com.phonepe.app" to "PhonePe", "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM", "com.dreamplug.androidapp" to "CRED", "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.mobikwik_new" to "MobiKwik", "com.whatsapp" to "WhatsApp Pay"
    )
    private val amount = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val spendWords = Regex("""\b(paid|sent|debited|payment of|spent|transferred|paying)\b""", RegexOption.IGNORE_CASE)
    private val incomeWords = Regex("""\b(received|credited|cashback|refund)\b""", RegexOption.IGNORE_CASE)
    private val payee = Regex("""\bto\s+([A-Za-z0-9 .&'_-]{2,40}?)(?:\s+(?:on|via|using|from|for|ref|upi|successfully)\b|[.,!]|$)""", RegexOption.IGNORE_CASE)

    /** Amount and payee of an outgoing payment, or null when the text isn't one. */
    fun parse(text: String): Pair<Double, String>? {
        if (!spendWords.containsMatchIn(text)) return null
        // "Received ₹50 …" / "Cashback of ₹10 for payment of …" are money coming in.
        val firstIn = incomeWords.find(text)?.range?.first ?: Int.MAX_VALUE
        if (firstIn < (spendWords.find(text)?.range?.first ?: 0)) return null
        val a = amount.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 && it < 1_000_000 } ?: return null
        val who = payee.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: ""
        return a to who
    }
}

class MoneyStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_money", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<MoneyData> = _data

    private fun load(): MoneyData = runCatching { prefs.getString("data", null)?.let { gson.fromJson<MoneyData>(it, object : TypeToken<MoneyData>() {}.type) } }.getOrNull()?.normalized() ?: MoneyData()

    @Synchronized fun update(f: (MoneyData) -> MoneyData) { val next = f(_data.value); _data.value = next; prefs.edit().putString("data", gson.toJson(next)).apply() }

    fun nextId() = System.currentTimeMillis() * 10 + (System.nanoTime() % 10).let { abs(it) }

    fun add(t: Txn) = update { it.copy(txns = it.txns + t) }
    fun remove(id: Long) = update { it.copy(txns = it.txns.filterNot { t -> t.id == id }) }

    /** Adds every recurring item that's due and set to add itself; returns the ones only waiting for a reminder. */
    fun processRecurring(today: LocalDate): List<Pair<Recurring, LocalDate>> {
        val due = MoneyEngine.due(_data.value, today)
        if (due.isEmpty()) return emptyList()
        val remind = due.filter { !it.first.autoAdd }
        update { d ->
            val adds = due.filter { it.first.autoAdd }.mapIndexed { i, (r, day) -> Txn(nextId() + i, r.amount, r.type, r.categoryId, r.walletId, date = day.toString(), minute = 9 * 60, note = r.name) }
            val moved = d.recurring.map { r ->
                val last = due.filter { it.first.id == r.id && it.first.autoAdd }.maxOfOrNull { it.second }
                if (last != null) r.copy(nextDate = MoneyEngine.nextAfter(r, last).toString()) else r
            }
            d.copy(txns = d.txns + adds, recurring = moved)
        }
        return remind
    }

    /** Pays a reminded recurring item now and moves it to its next date. */
    fun payRecurring(r: Recurring, day: LocalDate) = update { d ->
        d.copy(txns = d.txns + Txn(nextId(), r.amount, r.type, r.categoryId, r.walletId, date = LocalDate.now().toString(), minute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }, note = r.name),
            recurring = d.recurring.map { if (it.id == r.id) it.copy(nextDate = MoneyEngine.nextAfter(it, day).toString()) else it })
    }

    fun skipRecurring(r: Recurring, day: LocalDate) = update { d -> d.copy(recurring = d.recurring.map { if (it.id == r.id) it.copy(nextDate = MoneyEngine.nextAfter(it, day).toString()) else it }) }

    fun detect(amount: Double, payee: String, app: String, time: Long) = update { d ->
        // The same payment often posts twice (app + bank); keep one.
        if (d.detected.any { abs(it.amount - amount) < .01 && abs(it.time - time) < 5 * 60_000 } ||
            d.txns.any { abs(it.amount - amount) < .01 && it.date == LocalDate.now().toString() && it.note.contains(payee, true) && payee.isNotBlank() }) d
        else d.copy(detected = (d.detected + Detected(nextId(), amount, payee, app, time)).takeLast(30))
    }

    companion object {
        @Volatile private var instance: MoneyStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: MoneyStore(context).also { instance = it } }
    }
}
