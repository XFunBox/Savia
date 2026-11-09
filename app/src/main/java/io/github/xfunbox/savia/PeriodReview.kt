package io.github.xfunbox.savia

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

internal fun categoryKey(category: String): String = category.trim().lowercase(Locale.ROOT)
data class CategoryBudget(val category: String, val limit: Long, val spent: Long) {
    val remaining: Long get() = limit - spent
    val nearLimit: Boolean get() = limit > 0 && spent < limit && spent * 100 >= limit * 80
}
fun categorySpending(cycle: Cycle, movements: List<Movement>): List<CategoryBudget> = cycle.categoryLimits.map { (category, limit) ->
    CategoryBudget(category, limit, movements.filter {
        it.cycleId == cycle.id && !it.income && it.billId == null && categoryKey(it.category) == categoryKey(category)
    }.sumOf { it.amount })
}

data class PeriodClosure(val cycleId: String, val reviewedOn: LocalDate, val fingerprint: String,
    val entryIds: List<String> = emptyList())

private fun reviewFingerprint(data: AppData, cycle: Cycle): String {
    val relevant = buildString {
        append(cycle.copy(categoryLimits = cycle.categoryLimits.toSortedMap()))
        data.movements.filter { it.cycleId == cycle.id }.sortedBy { it.id }.forEach { append(it) }
        data.goals.sortedBy { it.id }.forEach { goal ->
            val entries = goal.entries.filter { it.cycleId == cycle.id }.sortedBy { it.id }
            if (entries.isNotEmpty()) {
                append(goal.id); append(goal.currency)
                entries.forEach { entry ->
                    append("SavingsEntry(id=${entry.id}, date=${entry.date}, amount=${entry.amount}, withdrawal=${entry.withdrawal}, note=${entry.note}, cycleId=${entry.cycleId}, additional=${entry.additional}")
                    entry.sourceMovementId?.let { append(", sourceMovementId=$it") }
                    append(")")
                }
            }
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(relevant.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
fun periodReviewChanged(data: AppData, cycle: Cycle): Boolean = data.closures.find { it.cycleId == cycle.id }
    ?.let { it.fingerprint != reviewFingerprint(data, cycle) } ?: false

/** Amounts replace only the contributions made by the previous closing; manual savings remain intact. */
fun closePeriod(data: AppData, cycleId: String, amounts: Map<String, Long>, today: LocalDate): AppData {
    validateData(data)
    val cycle = requireNotNull(data.cycles.find { it.id == cycleId }) { "El periodo ya no existe." }
    require(today >= cycle.end) { "Espera al final del periodo para confirmar el cierre." }
    val previous = data.closures.find { it.cycleId == cycleId }
    val oldIds = previous?.entryIds.orEmpty().toSet()
    val base = data.copy(goals = data.goals.map { goal ->
        goal.copy(entries = goal.entries.filterNot { it.id in oldIds })
    }, closures = data.closures.filterNot { it.cycleId == cycleId })
    amounts.forEach { (id, amount) ->
        require(amount in 0..MAX_MONEY) { "Importe del cierre fuera de límite." }
        require(base.goals.any { it.id == id && it.currency == cycle.currency }) { "El objetivo debe existir y tener la moneda del periodo." }
    }
    val existing = base.goals.filter { it.currency == cycle.currency }.flatMap { it.entries }.filter { it.cycleId == cycleId }
    val alreadySaved = existing.sumOf { if (it.withdrawal) -it.amount else it.amount }
    val summary = summarize(cycle, base.movements, base.goals)
    require(amounts.values.sum() + alreadySaved <= (summary.balance - summary.pendingBills).coerceAtLeast(0)) {
        "El ahorro confirmado supera el saldo tras los pagos pendientes. Revisa los importes y las aportaciones anteriores."
    }
    var reservation = (cycle.target - existing.filterNot { it.additional }
        .sumOf { if (it.withdrawal) -it.amount else it.amount }).coerceAtLeast(0)
    val ids = mutableListOf<String>()
    val goals = base.goals.map { goal ->
        val amount = amounts[goal.id] ?: 0L
        if (amount == 0L) goal else {
            val reserved = minOf(amount, reservation)
            reservation -= reserved
            val entries = listOf(false to reserved, true to (amount - reserved)).mapNotNull { (additional, value) ->
                if (value == 0L) null else {
                    val id = UUID.nameUUIDFromBytes("closure:$cycleId:${goal.id}:$additional".toByteArray(Charsets.UTF_8)).toString()
                    ids += id
                    SavingsEntry(id, cycle.end.minusDays(1), value, note = "Ahorro confirmado al cerrar el periodo",
                        cycleId = cycleId, additional = additional)
                }
            }
            goal.copy(entries = goal.entries + entries)
        }
    }
    val next = base.copy(goals = goals)
    val closure = PeriodClosure(cycleId, today, reviewFingerprint(next, cycle), ids)
    return next.copy(closures = next.closures + closure).also(::validateData)
}

/** A review may be updated without modifying any savings entries. */
fun reviewPeriod(data: AppData, cycle: Cycle, today: LocalDate): AppData {
    require(today >= cycle.end) { "El periodo todavía no ha terminado." }
    val previous = data.closures.find { it.cycleId == cycle.id }
    return data.copy(closures = data.closures.filterNot { it.cycleId == cycle.id } +
        PeriodClosure(cycle.id, today, reviewFingerprint(data, cycle), previous?.entryIds.orEmpty())).also(::validateData)
}
