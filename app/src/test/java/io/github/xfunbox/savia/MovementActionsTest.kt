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

    @Test fun filtersIncomeAndExpensesTogetherWithSearchWithoutChangingOrder() {
        val income = expense.copy(id = "income", income = true, category = "Venta", note = "Café vendido")
        val rent = expense.copy(id = "rent", category = "Casa", note = "Alquiler", billId = "rent")
        val entries = listOf(income, expense, rent)
        assertEquals(entries, filterMovements(entries, "", MovementFilter.ALL))
        assertEquals(listOf(expense, rent), filterMovements(entries, "", MovementFilter.EXPENSES))
        assertEquals(listOf(income), filterMovements(entries, " CAFE ", MovementFilter.INCOME))
        assertEquals(listOf(expense), filterMovements(entries, "cafe", MovementFilter.EXPENSES))
        assertTrue(filterMovements(entries, "alquiler", MovementFilter.INCOME).isEmpty())
    }

    @Test fun editingIncomePreservesOrderBeforeWithdrawalOnSameDate() {
        val income = expense.copy(id = "income", income = true, amount = 5000)
        val data = AppData(cycles = listOf(cycle), goals = listOf(SavingsGoal("goal", "Fondo", 10000)))
        val allocated = saveMovement(data, income, IncomeAllocation("goal", 2000))
        val withdrawn = putSavingsEntry(allocated, "goal", SavingsEntry("withdrawal", income.date, 1000, withdrawal = true))
        val corrected = saveMovement(withdrawn, income.copy(note = "Corregido"), IncomeAllocation("goal", 2000))
        assertEquals(withdrawn.goals, corrected.goals)
        assertEquals(1000L, goalSaved(corrected.goals.single()))
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

    @Test fun incomeAllocationIsLinkedUpdatedAndRemovedWithItsMovement() {
        val goal = SavingsGoal("goal", "Fondo", 10000)
        val data = AppData(cycles = listOf(cycle), goals = listOf(goal))
        val income = Movement("income", cycle.id, today, 5000, income = true, category = "Venta")

        val allocated = saveMovement(data, income, IncomeAllocation(goal.id, 2000))
        val allocation = allocated.goals.single().entries.single()
        assertEquals(2000L, allocation.amount)
        assertEquals(income.id, allocation.sourceMovementId)
        assertTrue(allocation.additional)
        assertEquals(3000L, summarize(cycle, allocated.movements, allocated.goals).available)

        val corrected = saveMovement(allocated, income.copy(amount = 6000), IncomeAllocation(goal.id, 4000))
        assertEquals(1, corrected.goals.single().entries.size)
        assertEquals(allocation.id, corrected.goals.single().entries.single().id)
        assertEquals(4000L, corrected.goals.single().entries.single().amount)
        assertEquals(2000L, summarize(cycle, corrected.movements, corrected.goals).available)

        val deleted = deleteMovement(corrected, income.id)
        assertTrue(deleted.movements.isEmpty())
        assertTrue(deleted.goals.single().entries.isEmpty())
        assertEquals(0L, goalSaved(deleted.goals.single()))
    }

    @Test fun incomeAllocationRejectsExpensesAndAmountsAboveIncome() {
        val data = AppData(cycles = listOf(cycle), goals = listOf(SavingsGoal("goal", "Fondo", 10000)))
        assertThrows(IllegalArgumentException::class.java) {
            saveMovement(data, expense, IncomeAllocation("goal", 100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveMovement(data, expense.copy(id = "income", income = true), IncomeAllocation("goal", 1000))
        }
    }

    private fun expectRejected(action: () -> Unit) {
        try {
            action()
            fail("Se esperaba rechazar la repetición")
        } catch (_: IllegalArgumentException) { }
    }
}
