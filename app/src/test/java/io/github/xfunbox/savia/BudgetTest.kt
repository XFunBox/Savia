package io.github.xfunbox.savia

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BudgetTest {
    private val start = LocalDate.of(2024, 1, 31)
    private val end = LocalDate.of(2024, 2, 29)
    private val bill = FixedExpense("rent", "Alquiler", 50000)
    private fun cycle() = Cycle("cycle", start, end, 100000, 20000, 30000, "EUR", listOf(bill))

    @Test fun moneyUsesExactCentsAndRejectsAmbiguity() {
        assertEquals(12345L, parseMoney("123,45"))
        assertEquals(10L, parseMoney("0.1"))
        assertEquals(99999999999L, parseMoney("999999999,99"))
        listOf("-1", "1.234", "1,000.00", "1e2", "NaN", "1000000000", "").forEach { assertNull(parseMoney(it)) }
    }

    @Test fun paydayClampsAndRemainsStrictlyAfter() {
        assertEquals(end, nextPayday(start, 31))
        assertEquals(LocalDate.of(2024, 3, 31), nextPayday(end, 31))
        assertEquals(LocalDate.of(2023, 2, 28), nextPayday(LocalDate.of(2023, 1, 31), 31))
    }

    @Test fun fixedPaymentIsNotReservedTwice() {
        val unpaid = summarize(cycle(), emptyList())
        val paid = summarize(cycle(), listOf(Movement("paid", "cycle", start, 50000, billId = bill.id)))
        assertEquals(20000, unpaid.available)
        assertEquals(unpaid.available, paid.available)
        assertEquals(0, paid.pendingBills)
        assertEquals(unpaid.forecast, paid.forecast)
    }

    @Test fun deficitsStayNegativeAndForecastUsesActualVariableSpend() {
        val summary = summarize(cycle(), listOf(Movement("food", "cycle", start, 60000)))
        assertEquals(-40000, summary.available)
        assertEquals(-10000, summary.forecast)
        assertEquals(0, suggestedTarget(Settings(salary = 100, variableBudget = 200)))
    }

    @Test fun newCyclePreservesSnapshots() {
        val settings = Settings(salary = 100000, variableBudget = 20000, fixedExpenses = listOf(bill))
        val created = newCycle(settings, start, end)
        assertEquals(30000, created.target)
        assertEquals("EUR", created.currency)
        assertEquals(listOf(bill), created.bills)
        assertNotEquals(created.target, settings.copy(salary = 900000).salary)
    }

    @Test fun manualTargetAboveCapacityIsPreservedAndOtherCyclesAreIgnored() {
        val settings = Settings(salary = 10000, manualTarget = 50000, automaticTarget = false)
        val created = newCycle(settings, start, end)
        val summary = summarize(created, listOf(Movement("unrelated", "another", start, 99999)))
        assertEquals(50000L, created.target)
        assertEquals(-40000L, summary.available)
    }

    @Test fun importsAllowFutureDatesWithinCycleAndAdjacentCycles() {
        val future = LocalDate.of(2090, 1, 1)
        val c = cycle().copy(start = future, end = future.plusMonths(1))
        validateData(AppData(cycles = listOf(c, c.copy(id = "next", start = c.end, end = c.end.plusMonths(1))),
            movements = listOf(Movement("future", c.id, future.plusDays(3), 200))))
    }

    @Test fun rejectsInvalidReferencesDuplicateBillsOverlapAndEndDate() {
        val c = cycle()
        val paid = Movement("paid", c.id, start, 50000, billId = bill.id)
        val invalid = listOf(
            AppData(cycles = listOf(c), movements = listOf(paid.copy(cycleId = "missing"))),
            AppData(cycles = listOf(c), movements = listOf(paid, paid.copy(id = "twice"))),
            AppData(cycles = listOf(c, c.copy(id = "overlap"))),
            AppData(cycles = listOf(c), movements = listOf(paid.copy(date = end))),
            AppData(cycles = listOf(c), movements = listOf(paid.copy(amount = -1))),
            AppData(cycles = listOf(c.copy(currency = "BAD")))
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { validateData(it) } }
    }
}
