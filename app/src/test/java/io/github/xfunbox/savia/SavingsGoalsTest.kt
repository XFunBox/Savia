package io.github.xfunbox.savia

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SavingsGoalsTest {
    private val goal = SavingsGoal("trip", "Viaje", 200000, 25000, 15000, "EUR")
    private val start = LocalDate.of(2026, 1, 1)
    private val first = Cycle("first", start, start.plusMonths(1), income = 100000, target = 20000)
    private val second = first.copy(id = "second", start = first.end, end = first.end.plusMonths(1))

    @Test fun entriesAreIdempotentEditableAndNeverInventOpeningHistory() {
        val data = AppData(goals = listOf(goal))
        val entry = SavingsEntry("deposit", start, 10000, note = "Apartado")
        val added = putSavingsEntry(data, goal.id, entry)
        assertEquals(35000L, goalSaved(added.goals.single()))
        assertEquals(25000L, added.goals.single().savedAmount)
        assertEquals(listOf(entry), added.goals.single().entries)
        assertEquals(added, putSavingsEntry(added, goal.id, entry))
        val corrected = putSavingsEntry(added, goal.id, entry.copy(amount = 5000))
        assertEquals(30000L, goalSaved(corrected.goals.single()))
        assertEquals(data, removeSavingsEntry(corrected, goal.id, entry.id))
    }

    @Test fun withdrawalsAndCorrectionsMustPreserveEveryHistoricalBalance() {
        val deposit = SavingsEntry("deposit", start, 10000)
        val withdrawal = SavingsEntry("withdrawal", start.plusDays(1), 30000, withdrawal = true)
        val data = AppData(goals = listOf(goal.copy(entries = listOf(deposit, withdrawal))))
        validateData(data)
        assertEquals(5000L, goalSaved(data.goals.single()))
        assertThrows(IllegalArgumentException::class.java) { removeSavingsEntry(data, goal.id, deposit.id) }
        assertThrows(IllegalArgumentException::class.java) {
            putSavingsEntry(data, goal.id, deposit.copy(date = start.plusDays(2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            putSavingsEntry(data, goal.id, withdrawal.copy(amount = 35001))
        }
        assertThrows(IllegalArgumentException::class.java) { putSavingsEntry(data, "missing", deposit) }
        assertThrows(IllegalArgumentException::class.java) { removeSavingsEntry(data, goal.id, "missing") }
    }

    @Test fun validatesSavingsAmountsDatesIdsNotesAndCycleAttribution() {
        val entry = SavingsEntry("e", start, 1, cycleId = first.id)
        val invalid = listOf(entry.copy(id = ""), entry.copy(id = "x".repeat(101)), entry.copy(amount = 0),
            entry.copy(amount = -1), entry.copy(amount = MAX_MONEY + 1), entry.copy(note = "x".repeat(1001)),
            entry.copy(note = "bad\u0000note"), entry.copy(date = first.end), entry.copy(cycleId = "missing"),
            entry.copy(date = LocalDate.of(1899, 1, 1)), entry.copy(date = LocalDate.of(10000, 1, 1)))
        val data = AppData(cycles = listOf(first), goals = listOf(goal))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { putSavingsEntry(data, goal.id, it) } }
        assertThrows(IllegalArgumentException::class.java) {
            putSavingsEntry(data.copy(goals = listOf(goal.copy(currency = "USD"))), goal.id, entry)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateData(data.copy(goals = listOf(goal.copy(entries = listOf(entry, entry)))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateData(data.copy(goals = listOf(goal.copy(entries = listOf(entry)), goal.copy(id = "other", entries = listOf(entry)))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            putSavingsEntry(data.copy(goals = listOf(goal.copy(savedAmount = MAX_MONEY))), goal.id, entry)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateData(data.copy(goals = listOf(goal.copy(targetDate = LocalDate.of(10000, 1, 1)))))
        }
        val external = putSavingsEntry(AppData(goals = listOf(goal.copy(currency = "USD"))), goal.id,
            entry.copy(cycleId = null, additional = true))
        assertNull(external.goals.single().entries.single().cycleId)
        validateData(external)
    }

    @Test fun deletingCyclePreservesSavingsAndDetachesTheirAttribution() {
        val entry = SavingsEntry("e", start, 10000, cycleId = first.id, additional = true)
        val income = Movement("income", first.id, start, 20000, income = true)
        val linked = entry.copy(id = "linked", amount = 5000, sourceMovementId = income.id)
        val data = AppData(cycles = listOf(first), movements = listOf(income),
            goals = listOf(goal.copy(entries = listOf(entry, linked))))
        val deleted = deleteCycle(data, first.id)
        assertEquals(listOf(entry.copy(cycleId = null), linked.copy(cycleId = null, sourceMovementId = null)), deleted.goals.single().entries)
        assertEquals(goalSaved(data.goals.single()), goalSaved(deleted.goals.single()))
        assertEquals(listOf(entry, linked), data.goals.single().entries)
        assertTrue(deleted.movements.isEmpty())
        validateData(deleted)
    }

    @Test fun suggestionUsesPaydaysInclusiveOfTodayAndRoundsUpCents() {
        val settings = Settings(salary = 100000, payday = 31)
        val dated = goal.copy(targetAmount = 35001, targetDate = LocalDate.of(2024, 3, 31))
        val suggestion = suggestGoalContribution(dated, settings, LocalDate.of(2024, 1, 31))
        assertEquals(GoalSuggestion(GoalSuggestionStatus.READY, 3334, 3, LocalDate.of(2024, 1, 31)), suggestion)
        assertEquals(GoalSuggestion(GoalSuggestionStatus.READY, 5001, 2, LocalDate.of(2024, 2, 29)),
            suggestGoalContribution(dated, settings, LocalDate.of(2024, 2, 1)))
        assertEquals(LocalDate.of(2023, 2, 28), suggestGoalContribution(
            dated.copy(targetDate = LocalDate.of(2023, 2, 28)), settings, LocalDate.of(2023, 2, 1)).firstPayday)
        assertEquals(LocalDate.of(2024, 2, 15), suggestGoalContribution(
            dated, settings.copy(payday = 15), LocalDate.of(2024, 2, 1)).firstPayday)
        assertEquals(LocalDate.of(2027, 1, 1), suggestGoalContribution(
            dated.copy(targetDate = LocalDate.of(2027, 1, 1)), settings.copy(payday = 1), LocalDate.of(2026, 12, 31)).firstPayday)
    }

    @Test fun suggestionExplainsCompletedMissingOverdueIncompatibleAndUnconfiguredDates() {
        val today = LocalDate.of(2026, 1, 15)
        val settings = Settings(salary = 10000, payday = 31)
        val dated = goal.copy(targetDate = today)
        assertEquals(GoalSuggestionStatus.NO_DATE, suggestGoalContribution(goal, settings, today).status)
        assertEquals(GoalSuggestionStatus.COMPLETED, suggestGoalContribution(dated.copy(savedAmount = goal.targetAmount), settings, today).status)
        assertEquals(GoalSuggestionStatus.OVERDUE, suggestGoalContribution(dated.copy(targetDate = today.minusDays(1)), settings, today).status)
        assertEquals(GoalSuggestionStatus.CURRENCY_MISMATCH, suggestGoalContribution(dated, settings.copy(currency = "USD"), today).status)
        assertEquals(GoalSuggestionStatus.INVALID_CALENDAR, suggestGoalContribution(dated, settings, today).status)
        assertEquals(GoalSuggestionStatus.INVALID_CALENDAR, suggestGoalContribution(dated, settings.copy(salary = 0), today).status)
        assertEquals(GoalSuggestionStatus.INVALID_CALENDAR, suggestGoalContribution(dated, settings.copy(payday = 0), today).status)
        assertEquals(175000L, suggestGoalContribution(dated, settings.copy(payday = 15), today).amountPerPayday)
        assertEquals(1, suggestGoalContribution(dated, settings.copy(payday = 15), today).paydays)
        val last = LocalDate.of(9999, 12, 31)
        assertEquals(1, suggestGoalContribution(dated.copy(targetDate = last), settings, last).paydays)
    }

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
