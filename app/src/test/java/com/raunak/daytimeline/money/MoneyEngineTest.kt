package com.raunak.daytimeline.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class MoneyEngineTest {
    private val today = LocalDate.of(2026, 10, 10)
    private fun spend(amount: Double, d: LocalDate, cat: Long = 1, wallet: Long = 1) = Txn(d.toEpochDay() * 1000 + amount.toLong(), amount, TxnType.EXPENSE, cat, wallet, date = d.toString())

    @Test
    fun month_follows_the_allowance_day() {
        assertThat(MoneyEngine.monthRange(today, 1)).isEqualTo(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31))
        assertThat(MoneyEngine.monthRange(today, 15)).isEqualTo(LocalDate.of(2026, 9, 15) to LocalDate.of(2026, 10, 14))
        // A start day past the month's end clamps to its last day.
        assertThat(MoneyEngine.monthRange(LocalDate.of(2027, 2, 28), 31).first).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun safe_to_spend_spreads_what_is_left() {
        // Budget 3100, 22 days left (10–31 Oct), 900 spent before today, 50 today: (3100-900)/22 - 50 = 50.
        val d = MoneyData(txns = listOf(spend(900.0, today.minusDays(3)), spend(50.0, today)), settings = MoneySettings(monthlyBudget = 3100.0))
        assertThat(MoneyEngine.safeToSpendToday(d, today)).isWithin(0.01).of(50.0)
        val over = d.copy(txns = d.txns + spend(5000.0, today.minusDays(1)))
        assertThat(MoneyEngine.safeToSpendToday(over, today)).isEqualTo(0.0)
    }

    @Test
    fun balances_streaks_splits_and_ledger() {
        val atm = Txn(1, 500.0, TxnType.TRANSFER, walletId = 1, toWalletId = 2, date = today.toString())
        val pay = Txn(2, 3000.0, TxnType.INCOME, 101, 1, date = today.minusDays(5).toString())
        val d = MoneyData(txns = listOf(pay, atm, spend(40.0, today, wallet = 2), spend(100.0, today.minusDays(4))))
        assertThat(MoneyEngine.balance(d, d.wallets[0])).isEqualTo(3000.0 - 500 - 100)
        assertThat(MoneyEngine.balance(d, d.wallets[1])).isEqualTo(460.0)
        // Nothing spent on the 3 days before today (6th was a spend day).
        assertThat(MoneyEngine.noSpendStreak(d, today)).isEqualTo(3)
        assertThat(MoneyEngine.share(300.0, 2)).isEqualTo(100.0)
        val debts = MoneyData(debts = listOf(Debt(1, "Aman", 100.0, "Dinner", today.toString()), Debt(2, "Aman ", -40.0, "Chai", today.toString()), Debt(3, "Riya", 50.0, "", today.toString(), settled = true)))
        assertThat(MoneyEngine.ledger(debts)).containsExactly("Aman", 60.0)
    }

    @Test
    fun recurring_and_alerts() {
        val r = Recurring(1, "Recharge", 299.0, 4, 1, everyDays = 28, nextDate = today.minusDays(30).toString())
        val due = MoneyEngine.due(MoneyData(recurring = listOf(r)), today)
        assertThat(due.map { it.second }).containsExactly(today.minusDays(30), today.minusDays(2))
        val monthly = r.copy(everyDays = 0, dayOfMonth = 31)
        assertThat(MoneyEngine.nextAfter(monthly, LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 28))
        val d = MoneyData(txns = listOf(spend(1700.0, today, cat = 1)), settings = MoneySettings(monthlyBudget = 10000.0, alertPercent = 80))
        // Food's default budget is 2000: 85% crosses the 80% line only.
        val alerts = MoneyEngine.alerts(d, today)
        assertThat(alerts.map { it.first?.id to it.second }).containsExactly(1L to 80)
        val sent = d.copy(alerted = setOf(MoneyEngine.alertKey(d, today, alerts[0].first, 80)))
        assertThat(MoneyEngine.alerts(sent, today)).isEmpty()
    }

    @Test
    fun upi_notifications_and_formatting() {
        assertThat(UpiParser.parse("Paid ₹120 to Swiggy")).isEqualTo(120.0 to "Swiggy")
        assertThat(UpiParser.parse("You paid Rs. 1,250.50 to Ramesh Kirana via UPI")).isEqualTo(1250.5 to "Ramesh Kirana")
        assertThat(UpiParser.parse("₹45 sent to Aman successfully")?.first).isEqualTo(45.0)
        assertThat(UpiParser.parse("Received ₹500 from Papa")).isNull()
        assertThat(UpiParser.parse("Your order is on the way")).isNull()
        assertThat(MoneyEngine.format(123456.0)).isEqualTo("₹1,23,456")
        assertThat(MoneyEngine.format(15.5)).isEqualTo("₹15.50")
    }
}
