package io.github.xfunbox.savia

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MovementActionsTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val cycle = Cycle(id = "current", start = today.minusDays(2), end = today.plusDays(28))
    private val expense = Movement(id = "original", cycleId = cycle.id, date = today.minusDays(1),
        amount = 250, category = "Alimentación", note = "Café con leche")

    @Test fun searchesCategoryAndNoteIgnoringCaseAccentsAndOuterSpaces() {
        val entries = listOf(expense, expense.copy(id = "other", category = "Ocio", note = "Cine"))
        assertEquals(listOf(expense), filterMovements(entries, " ALIMENTACION "))
        assertEquals(listOf(expense), filterMovements(entries, "cAFE"))
        assertEquals(entries, filterMovements(entries, "   "))
        assertTrue(filterMovements(entries, "transporte").isEmpty())
    }

    @Test fun amountSearchMatchesExactCentsWithEitherDecimalSeparator() {
        val two = expense.copy(id = "two", amount = 200, category = "Otros", note = "")
        val twoHundred = two.copy(id = "twoHundred", amount = 20000)
        val entries = listOf(expense, two, twoHundred)
        assertEquals(listOf(expense), filterMovements(entries, " 2,50 "))
        assertEquals(listOf(expense), filterMovements(entries, "2.5"))
        assertEquals(listOf(two), filterMovements(entries, "2"))
        assertTrue(filterMovements(entries, "2.501").isEmpty())
    }

    @Test fun numericQueriesAlsoSearchTextAndPreserveOrder() {
        val note = expense.copy(id = "note", note = "Compra 12", amount = 500)
        val amount = expense.copy(id = "amount", note = "", amount = 1200)
        assertEquals(listOf(note, amount), filterMovements(listOf(note, amount), "12"))
    }

    @Test fun repeatedExpenseHasNewIdentityAndTodayWithoutChangingOriginal() {
        val repeated = repeatExpense(expense, cycle, today)
        assertNotEquals(expense.id, repeated.id)
        assertEquals(expense.copy(id = repeated.id, date = today), repeated)
        assertEquals(today.minusDays(1), expense.date)
        assertEquals("original", expense.id)
        assertNotEquals(repeated.id, repeatExpense(expense, cycle, today).id)
        validateData(AppData(cycles = listOf(cycle), movements = listOf(expense, repeated)))
    }

    @Test fun refusesIncomeFixedPaymentsAndDifferentPeriods() {
        expectRejected { repeatExpense(expense.copy(income = true), cycle, today) }
        expectRejected { repeatExpense(expense.copy(billId = "rent"), cycle, today) }
        expectRejected { repeatExpense(expense.copy(cycleId = "old"), cycle, today) }
    }

    @Test fun repeatsOnFirstDayButNotBeforeOrOnExclusiveEnd() {
        assertEquals(cycle.start, repeatExpense(expense, cycle, cycle.start).date)
        expectRejected { repeatExpense(expense, cycle, cycle.start.minusDays(1)) }
        expectRejected { repeatExpense(expense, cycle, cycle.end) }
    }

    private fun expectRejected(action: () -> Unit) {
        try {
            action()
            fail("Se esperaba rechazar la repetición")
        } catch (_: IllegalArgumentException) { }
    }
}
