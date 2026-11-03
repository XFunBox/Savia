package io.github.xfunbox.savia

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SavingsGoalsTest {
    private val goal = SavingsGoal("trip", "Viaje", 200000, 25000, 15000, "EUR")
    private val start = LocalDate.of(2026, 1, 1)
    private val first = Cycle("first", start, start.plusMonths(1), income = 100000, target = 20000)
    private val second = first.copy(id = "second", start = first.end, end = first.end.plusMonths(1))

    @Test fun goalsAllowIndependentCurrenciesAndSavingBeyondTargetWithoutChangingBudget() {
        val goals = listOf(goal.copy(savedAmount = 300000), goal.copy(id = "usd", currency = "USD"),
            goal.copy(id = "gbp", currency = "GBP"), goal.copy(id = "mxn", currency = "MXN"))
        val data = AppData(cycles = listOf(first), goals = goals)
        validateData(data)
        assertEquals(80000L, summarize(first, data.movements).available)
        assertEquals(100000L, summarize(first, data.movements).forecast)
        assertEquals(goals, data.goals)
    }

    @Test fun validatesGoalIdentityNamesCurrencyAndMoneyBoundaries() {
        val invalid = listOf(goal.copy(id = ""), goal.copy(id = " "), goal.copy(id = "x".repeat(101)),
            goal.copy(name = " "), goal.copy(name = "x".repeat(101)), goal.copy(name = "bad\u0000name"),
            goal.copy(currency = "JPY"), goal.copy(targetAmount = 0), goal.copy(targetAmount = -1),
            goal.copy(targetAmount = MAX_MONEY + 1), goal.copy(savedAmount = -1),
            goal.copy(savedAmount = MAX_MONEY + 1), goal.copy(periodAmount = -1),
            goal.copy(periodAmount = MAX_MONEY + 1))
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { validateData(AppData(goals = listOf(it))) }
        }
        validateData(AppData(goals = listOf(goal.copy(id = "x".repeat(100), name = "x".repeat(100),
            targetAmount = MAX_MONEY, savedAmount = MAX_MONEY, periodAmount = MAX_MONEY))))
        validateData(AppData(goals = listOf(goal.copy(targetAmount = 1, savedAmount = 0, periodAmount = 0))))
    }

    @Test fun allowsOneHundredGoalsButRejectsMoreAndDuplicateIds() {
        val hundred = List(100) { goal.copy(id = "goal-$it") }
        validateData(AppData(goals = hundred))
        assertThrows(IllegalArgumentException::class.java) {
            validateData(AppData(goals = hundred + goal))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateData(AppData(goals = listOf(goal, goal.copy(name = "Otro"))))
        }
    }

    @Test fun deletingPeriodRemovesOnlyItsMovementsAndPreservesSettingsGoalsAndSource() {
        val settings = Settings(salary = 150000, currency = "USD")
        val movements = listOf(Movement("a", first.id, first.start, 123),
            Movement("b", second.id, second.start, 456))
        val data = AppData(settings, listOf(first, second), movements, listOf(goal))
        val result = deleteCycle(data, first.id)
        assertEquals(AppData(settings, listOf(second), listOf(movements[1]), listOf(goal)), result)
        assertEquals(listOf(first, second), data.cycles)
        assertEquals(movements, data.movements)
        validateData(result)
    }

    @Test fun deletingLastPeriodPreservesGoalsAndAllowsStartingAgain() {
        val data = AppData(Settings(salary = 120000), listOf(first),
            listOf(Movement("a", first.id, first.start, 123)), listOf(goal))
        val result = deleteCycle(data, first.id)
        assertEquals(data.copy(cycles = emptyList(), movements = emptyList()), result)
        val restarted = result.copy(cycles = listOf(newCycle(result.settings, start, first.end)))
        validateData(restarted)
        assertEquals(listOf(goal), restarted.goals)
    }

    @Test fun deletingMissingPeriodIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { deleteCycle(AppData(), "missing") }
        assertThrows(IllegalArgumentException::class.java) {
            deleteCycle(AppData(cycles = listOf(first), goals = listOf(goal)), "missing")
        }
    }
}
