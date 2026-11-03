package io.github.xfunbox.savia

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

fun filterMovements(movements: List<Movement>, query: String): List<Movement> {
    val needle = query.trim().searchText()
    if (needle.isEmpty()) return movements
    val amount = parseMoney(query)
    return movements.filter {
        it.category.searchText().contains(needle) || it.note.searchText().contains(needle) ||
            (amount != null && it.amount == amount)
    }
}

private fun String.searchText(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun repeatExpense(source: Movement, cycle: Cycle, today: LocalDate): Movement {
    require(!source.income && source.billId == null) { "Solo puedes repetir gastos variables." }
    require(source.cycleId == cycle.id) { "El gasto debe pertenecer al periodo actual." }
    require(today >= cycle.start && today < cycle.end) { "El periodo debe estar activo." }
    return source.copy(id = UUID.randomUUID().toString(), date = today)
}
