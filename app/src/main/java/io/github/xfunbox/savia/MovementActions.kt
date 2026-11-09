package io.github.xfunbox.savia

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

data class IncomeAllocation(val goalId: String, val amount: Long)
enum class MovementFilter { ALL, EXPENSES, INCOME }

fun filterMovements(movements: List<Movement>, query: String, type: MovementFilter = MovementFilter.ALL): List<Movement> {
    val needle = query.trim().searchText()
    val amount = parseMoney(query)
    return movements.filter {
        (type == MovementFilter.ALL || it.income == (type == MovementFilter.INCOME)) &&
            (needle.isEmpty() || it.category.searchText().contains(needle) || it.note.searchText().contains(needle) ||
                (amount != null && it.amount == amount))
    }
}

private fun String.searchText(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun saveMovement(data: AppData, movement: Movement, allocation: IncomeAllocation? = null): AppData {
    val cycle = requireNotNull(data.cycles.find { it.id == movement.cycleId }) { "El periodo ya no existe." }
    val goal = allocation?.let {
        require(movement.income && movement.billId == null) { "Solo puedes asignar un ingreso extra a un objetivo." }
        require(it.amount in 1..movement.amount) { "La aportación debe ser mayor que cero y no superar el ingreso." }
        requireNotNull(data.goals.find { goal -> goal.id == it.goalId }) { "El objetivo ya no existe." }.also {
            require(it.currency == cycle.currency) { "El objetivo y el ingreso deben tener la misma moneda." }
        }
    }
    val oldEntryIds = data.goals.flatMap { it.entries }
        .filter { it.sourceMovementId == movement.id }.map { it.id }.toSet()
    val goals = data.goals.map { current ->
        val entries = current.entries.filterNot { it.id in oldEntryIds }
        if (goal?.id != current.id) current.copy(entries = entries)
        else {
            val previous = current.entries.firstOrNull { it.sourceMovementId == movement.id }
            val replacement = SavingsEntry(
                id = previous?.id ?: oldEntryIds.firstOrNull() ?: UUID.randomUUID().toString(),
                date = movement.date,
                amount = requireNotNull(allocation).amount,
                note = "Asignado desde ingreso extra",
                cycleId = cycle.id,
                additional = true,
                sourceMovementId = movement.id
            )
            current.copy(entries = if (previous == null) entries + replacement else
                current.entries.map { if (it.id == previous.id) replacement else it })
        }
    }
    return data.copy(
        movements = data.movements.filterNot { it.id == movement.id } + movement,
        goals = goals
    ).also(::validateData)
}

fun deleteMovement(data: AppData, movementId: String): AppData {
    require(data.movements.any { it.id == movementId }) { "El movimiento ya no existe." }
    return data.copy(
        movements = data.movements.filterNot { it.id == movementId },
        goals = data.goals.map { goal -> goal.copy(entries = goal.entries.filterNot { it.sourceMovementId == movementId }) }
    ).also(::validateData)
}

fun repeatExpense(source: Movement, cycle: Cycle, today: LocalDate): Movement {
    require(!source.income && source.billId == null) { "Solo puedes repetir gastos variables." }
    require(source.cycleId == cycle.id) { "El gasto debe pertenecer al periodo actual." }
    require(today >= cycle.start && today < cycle.end) { "El periodo debe estar activo." }
    return source.copy(id = UUID.randomUUID().toString(), date = today)
}
