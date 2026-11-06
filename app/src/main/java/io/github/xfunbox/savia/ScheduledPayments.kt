package io.github.xfunbox.savia

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// Scheduled snapshots contain the full payment, without a second annual provision.
fun billsForCycle(bills: List<FixedExpense>, start: LocalDate, end: LocalDate): List<FixedExpense> = bills.flatMap { bill ->
    val day = bill.dueDay
    if (day == null) listOf(bill.copy(dueDate = null)) else {
        val result = mutableListOf<FixedExpense>()
        require(end > start) { "Periodo inválido." }
        var month = YearMonth.from(start)
        val last = YearMonth.from(end.minusDays(1))
        while (month <= last) {
            if (bill.dueMonth == null || bill.dueMonth == month.monthValue) {
                val date = month.atDay(minOf(day, month.lengthOfMonth()))
                if (date >= start && date < end) result += bill.copy(
                    id = UUID.nameUUIDFromBytes("${bill.id}:$date".toByteArray(Charsets.UTF_8)).toString(), dueDate = date)
            }
            require(result.size <= 100) { "El periodo contiene demasiados vencimientos (máximo 100)." }
            if (month == last) break
            month = month.plusMonths(1)
        }
        result
    }
}

data class ScheduledPayment(val cycleId: String, val bill: FixedExpense, val date: LocalDate, val paid: Boolean)

fun upcomingPayments(data: AppData, today: LocalDate): List<ScheduledPayment> = data.cycles.flatMap { cycle ->
    cycle.bills.mapNotNull { bill -> bill.dueDate?.let { date ->
        ScheduledPayment(cycle.id, bill, date, data.movements.any { it.cycleId == cycle.id && it.billId == bill.id })
    } }
}.sortedWith(compareBy<ScheduledPayment> { it.paid }.thenBy { it.date })

fun payScheduledBill(data: AppData, cycleId: String, billId: String, date: LocalDate): AppData {
    val cycle = requireNotNull(data.cycles.find { it.id == cycleId }) { "El periodo ya no existe." }
    val bill = requireNotNull(cycle.bills.find { it.id == billId }) { "El pago ya no existe." }
    if (data.movements.any { it.cycleId == cycleId && it.billId == billId }) return data
    return data.copy(movements = data.movements + Movement(UUID.randomUUID().toString(), cycleId, date,
        bill.amount, category = "Fijos", billId = billId)).also(::validateData)
}

fun fixedBudgetPerPeriod(bills: List<FixedExpense>): Long = bills.sumOf { bill ->
    if (bill.dueMonth == null) bill.amount else (bill.amount + 11) / 12
}
