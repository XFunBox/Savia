package io.github.xfunbox.savia

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduledPaymentsTest {
    @Test fun shortMonthsAndAnnualBillsBelongOnlyToTheirDueCycle() {
        val monthly = FixedExpense("rent", "Alquiler", 50000, dueDay = 31)
        val annual = FixedExpense("annual", "Seguro anual", 12000, dueDay = 29, dueMonth = 2)
        val bills = billsForCycle(listOf(monthly, annual), LocalDate.parse("2028-02-01"), LocalDate.parse("2028-03-01"))
        assertEquals(2, bills.size)
        assertTrue(bills.all { it.dueDate == LocalDate.parse("2028-02-29") })
        assertEquals(1, billsForCycle(listOf(monthly, annual), LocalDate.parse("2028-03-01"), LocalDate.parse("2028-04-01")).size)
    }
    @Test fun longCycleHasEachMonthlyOccurrenceAndPaymentIsIdempotent() {
        val start = LocalDate.parse("2026-01-01")
        val end = LocalDate.parse("2026-03-01")
        val bills = billsForCycle(listOf(FixedExpense("rent", "Alquiler", 50000, dueDay = 15)), start, end)
        assertEquals(2, bills.map { it.id }.distinct().size)
        val cycle = Cycle(id = "c", start = start, end = end, bills = bills)
        val data = AppData(cycles = listOf(cycle))
        val paid = payScheduledBill(data, "c", bills.first().id, start.plusDays(14))
        assertEquals(paid, payScheduledBill(paid, "c", bills.first().id, start.plusDays(14)))
        assertEquals(1, upcomingPayments(paid, start).count { !it.paid })
        assertEquals(100000, summarize(cycle, paid.movements).spent + summarize(cycle, paid.movements).pendingBills)
    }
    @Test fun unscheduledLegacyBillRemainsPerCycle() {
        val bill = FixedExpense("old", "Luz", 100)
        assertEquals(listOf(bill), billsForCycle(listOf(bill), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-01")))
    }
}
