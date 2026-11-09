package io.github.xfunbox.savia

import java.time.LocalDate

data class SavingsEntry(
    val id: String, val date: LocalDate, val amount: Long, val withdrawal: Boolean = false,
    val note: String = "", val cycleId: String? = null, val additional: Boolean = false,
    val sourceMovementId: String? = null
)

data class SavingsGoal(
    val id: String = "", val name: String = "", val targetAmount: Long = 0,
    val savedAmount: Long = 0, val periodAmount: Long = 0, val currency: String = "EUR",
    val entries: List<SavingsEntry> = emptyList(), val targetDate: LocalDate? = null
)

/** savedAmount is the undated opening balance, including balances imported from v1/v2. */
fun goalSaved(goal: SavingsGoal): Long = goal.savedAmount + goal.entries.sumOf {
    if (it.withdrawal) -it.amount else it.amount
}

fun putSavingsEntry(data: AppData, goalId: String, entry: SavingsEntry): AppData {
    require(data.goals.any { it.id == goalId }) { "El objetivo ya no existe." }
    require(entry.sourceMovementId == null) { "Las aportaciones de un ingreso extra se registran desde Movimientos." }
    require(data.goals.flatMap { it.entries }.none { it.id == entry.id && it.sourceMovementId != null }) {
        "Corrige la aportación vinculada desde el ingreso extra."
    }
    return data.copy(goals = data.goals.map { goal ->
        if (goal.id != goalId) goal else {
            val index = goal.entries.indexOfFirst { it.id == entry.id }
            goal.copy(entries = if (index < 0) goal.entries + entry else
                goal.entries.mapIndexed { i, old -> if (i == index) entry else old })
        }
    }).also(::validateData)
}

fun removeSavingsEntry(data: AppData, goalId: String, entryId: String): AppData {
    val goal = requireNotNull(data.goals.find { it.id == goalId }) { "El objetivo ya no existe." }
    val entry = requireNotNull(goal.entries.find { it.id == entryId }) { "La entrada ya no existe." }
    require(entry.sourceMovementId == null) { "Elimina el ingreso extra vinculado desde Movimientos." }
    return data.copy(goals = data.goals.map {
        if (it.id == goalId) it.copy(entries = it.entries.filterNot { entry -> entry.id == entryId }) else it
    }).also(::validateData)
}

enum class GoalSuggestionStatus { NO_DATE, COMPLETED, OVERDUE, INVALID_CALENDAR, CURRENCY_MISMATCH, READY }
data class GoalSuggestion(
    val status: GoalSuggestionStatus, val amountPerPayday: Long = 0,
    val paydays: Int = 0, val firstPayday: LocalDate? = null
)

/** Counts monthly paydays from today (inclusive) through the deadline, clamping short months.
 * This assumes the configured salary/payday continue; it never changes the budget or savings.
 */
fun suggestGoalContribution(goal: SavingsGoal, settings: Settings, today: LocalDate = LocalDate.now()): GoalSuggestion {
    val remaining = (goal.targetAmount - goalSaved(goal)).coerceAtLeast(0)
    if (remaining == 0L) return GoalSuggestion(GoalSuggestionStatus.COMPLETED)
    val deadline = goal.targetDate ?: return GoalSuggestion(GoalSuggestionStatus.NO_DATE)
    if (deadline < today) return GoalSuggestion(GoalSuggestionStatus.OVERDUE)
    if (settings.currency != goal.currency) return GoalSuggestion(GoalSuggestionStatus.CURRENCY_MISMATCH)
    if (settings.payday !in 1..31 || settings.salary <= 0 || today.year !in 1900..9999 || deadline.year !in 1900..9999)
        return GoalSuggestion(GoalSuggestionStatus.INVALID_CALENDAR)
    val first = nextPayday(today.minusDays(1), settings.payday)
    if (first > deadline) return GoalSuggestion(GoalSuggestionStatus.INVALID_CALENDAR)
    var payday = first
    var count = 0
    while (payday <= deadline) {
        count++
        if (payday.year == 9999 && payday.monthValue == 12) break
        payday = nextPayday(payday, settings.payday)
    }
    return GoalSuggestion(GoalSuggestionStatus.READY, (remaining + count - 1) / count, count, first)
}
