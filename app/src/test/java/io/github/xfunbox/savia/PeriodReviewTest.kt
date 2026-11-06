package io.github.xfunbox.savia

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PeriodReviewTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val cycle = Cycle("c", start, start.plusMonths(1), income = 100000, target = 20000,
        categoryLimits = mapOf("Comida" to 10000))
    private val data = AppData(cycles = listOf(cycle), goals = listOf(SavingsGoal("g", "Meta", 100000)))

    @Test fun limitsAreSnapshotsAndNeverDeductTwice() {
        val settings = Settings(salary = 100000, categoryLimits = mapOf("Comida" to 10000))
        val c = newCycle(settings, start, start.plusMonths(1))
        val movement = Movement("m", c.id, start, 12000, category = " comida ")
        assertEquals(-2000L, categorySpending(c, listOf(movement)).single().remaining)
        assertEquals(summarize(c.copy(categoryLimits = emptyMap()), listOf(movement)), summarize(c, listOf(movement)))
        assertEquals(10000L, c.categoryLimits["Comida"])
    }

    @Test fun repeatedClosureDoesNotDuplicateAndChangedMovementsInvalidateReview() {
        val closed = closePeriod(data, "c", mapOf("g" to 25000L), start.plusMonths(1))
        assertEquals(25000L, goalSaved(closed.goals.single()))
        assertEquals(75000L, summarize(cycle, closed.movements, closed.goals).available)
        assertEquals(closed, closePeriod(closed, "c", mapOf("g" to 25000L), start.plusMonths(1)))
        assertFalse(periodReviewChanged(closed, cycle))
        val changed = closed.copy(movements = listOf(Movement("m", "c", start, 1000)))
        assertTrue(periodReviewChanged(changed, cycle))
        assertEquals(closed, Backup.decode(Backup.encode(closed)))
        val deleted = deleteCycle(closed, "c")
        assertEquals(25000L, goalSaved(deleted.goals.single()))
        assertTrue(deleted.closures.isEmpty())
        assertTrue(deleted.goals.single().entries.all { it.cycleId == null })
    }

    @Test fun existingSavingsAreNotAddedAgainAndCorrectionReplacesOnlyClosureEntries() {
        val manual = putSavingsEntry(data, "g", SavingsEntry("manual", start, 10000, cycleId = "c"))
        val closed = closePeriod(manual, "c", mapOf("g" to 20000L), start.plusMonths(1))
        assertEquals(30000L, goalSaved(closed.goals.single()))
        val corrected = closePeriod(closed, "c", mapOf("g" to 5000L), start.plusMonths(1))
        assertEquals(15000L, goalSaved(corrected.goals.single()))
        assertTrue(corrected.goals.single().entries.any { it.id == "manual" })
        assertFalse(periodReviewChanged(corrected, cycle))
    }

    @Test fun pendingBillsAndCurrencyPreventInventingSavings() {
        val pending = data.copy(cycles = listOf(cycle.copy(bills = listOf(FixedExpense("b", "Pendiente", 90000)))))
        assertThrows(IllegalArgumentException::class.java) { closePeriod(pending, "c", mapOf("g" to 20000L), start.plusMonths(1)) }
        assertThrows(IllegalArgumentException::class.java) { closePeriod(data, "c", mapOf("g" to 1L), start) }
        assertThrows(IllegalArgumentException::class.java) {
            closePeriod(data.copy(goals = listOf(data.goals.single().copy(currency = "USD"))), "c", mapOf("g" to 1L), start.plusMonths(1))
        }
    }
    @Test fun closureCannotClaimSavingsFromAnotherPeriodOrAWithdrawal() {
        val second = cycle.copy(id = "c2", start = cycle.end, end = cycle.end.plusMonths(1))
        val entry = SavingsEntry("foreign", second.start, 1000, cycleId = second.id)
        val base = data.copy(cycles = listOf(cycle, second), goals = listOf(data.goals.single().copy(savedAmount = 5000, entries = listOf(entry))))
        val forged = PeriodClosure("c", cycle.end, "a".repeat(64), listOf("foreign"))
        assertThrows(IllegalArgumentException::class.java) { validateData(base.copy(closures = listOf(forged))) }
        val withdrawal = base.copy(goals = listOf(base.goals.single().copy(entries = listOf(entry.copy(cycleId = "c", date = start, withdrawal = true)))))
        assertThrows(IllegalArgumentException::class.java) { validateData(withdrawal.copy(closures = listOf(forged))) }
    }
}
