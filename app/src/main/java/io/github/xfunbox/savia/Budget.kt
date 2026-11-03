package io.github.xfunbox.savia

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale
import java.util.UUID

internal const val MAX_MONEY = 99_999_999_999L
private val currencies = setOf("EUR", "USD", "GBP", "MXN")

data class FixedExpense(val id: String = "", val name: String = "", val amount: Long = 0)
data class Settings(
    val salary: Long = 0, val payday: Int = 1, val variableBudget: Long = 0,
    val manualTarget: Long = 0, val automaticTarget: Boolean = true,
    val currency: String = "EUR", val fixedExpenses: List<FixedExpense> = emptyList()
)
data class Cycle(
    val id: String = "", val start: LocalDate = LocalDate.now(), val end: LocalDate = start.plusMonths(1),
    val income: Long = 0, val variableBudget: Long = 0, val target: Long = 0,
    val currency: String = "EUR", val bills: List<FixedExpense> = emptyList()
)
data class Movement(
    val id: String = "", val cycleId: String = "", val date: LocalDate = LocalDate.now(),
    val amount: Long = 0, val income: Boolean = false, val category: String = "Otros",
    val note: String = "", val billId: String? = null
)
data class AppData(
    val settings: Settings = Settings(), val cycles: List<Cycle> = emptyList(),
    val movements: List<Movement> = emptyList(), val goals: List<SavingsGoal> = emptyList()
)
data class BudgetSummary(
    val income: Long = 0, val spent: Long = 0, val pendingBills: Long = 0,
    val variableSpent: Long = 0, val available: Long = 0, val forecast: Long = 0,
    val target: Long = 0, val balance: Long = 0
)

fun parseMoney(text: String): Long? {
    val cleaned = text.trim()
    if (!Regex("[0-9]{1,9}([.,][0-9]{1,2})?").matches(cleaned)) return null
    return cleaned.replace(',', '.').toBigDecimal().movePointRight(2).longValueExact()
        .takeIf { it <= MAX_MONEY }
}

fun formatMoney(amount: Long, currency: String = "EUR"): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).apply {
        this.currency = Currency.getInstance(currency)
    }.format(BigDecimal.valueOf(amount, 2))

fun suggestedTarget(settings: Settings): Long =
    (settings.salary - settings.fixedExpenses.sumOf { it.amount } - settings.variableBudget).coerceAtLeast(0)

fun nextPayday(after: LocalDate, payday: Int): LocalDate {
    require(payday in 1..31) { "El día de cobro debe estar entre 1 y 31." }
    val month = YearMonth.from(after)
    val current = month.atDay(minOf(payday, month.lengthOfMonth()))
    if (current > after) return current
    val next = month.plusMonths(1)
    return next.atDay(minOf(payday, next.lengthOfMonth()))
}

fun newCycle(settings: Settings, start: LocalDate, end: LocalDate, income: Long = settings.salary): Cycle {
    val snapshot = settings.copy(salary = income)
    val cycle = Cycle(UUID.randomUUID().toString(), start, end, income, settings.variableBudget,
        if (settings.automaticTarget) suggestedTarget(snapshot) else settings.manualTarget,
        settings.currency, settings.fixedExpenses.map { it.copy() })
    validateData(AppData(settings, listOf(cycle)))
    return cycle
}

fun summarize(cycle: Cycle, movements: List<Movement>): BudgetSummary {
    val entries = movements.filter { it.cycleId == cycle.id }
    val income = cycle.income + entries.filter { it.income }.sumOf { it.amount }
    val expenses = entries.filterNot { it.income }
    val spent = expenses.sumOf { it.amount }
    val paid = expenses.mapNotNull { it.billId }.toSet()
    val pending = cycle.bills.filterNot { it.id in paid }.sumOf { it.amount }
    val variable = expenses.filter { it.billId == null }.sumOf { it.amount }
    val fixedPaid = expenses.filter { it.billId != null }.sumOf { it.amount }
    return BudgetSummary(income, spent, pending, variable, income - spent - pending - cycle.target,
        income - fixedPaid - pending - maxOf(cycle.variableBudget, variable), cycle.target, income - spent)
}

fun deleteCycle(data: AppData, cycleId: String): AppData {
    require(data.cycles.any { it.id == cycleId }) { "El periodo ya no existe." }
    return data.copy(cycles = data.cycles.filterNot { it.id == cycleId },
        movements = data.movements.filterNot { it.cycleId == cycleId })
}

fun validateData(data: AppData) {
    fun money(value: Long) = require(value in 0..MAX_MONEY) { "Importe fuera de límite (0 a 999999999,99)." }
    fun text(value: String, max: Int, label: String, blank: Boolean = false) {
        require(value.length <= max && (blank || value.isNotBlank())) { "$label vacío o demasiado largo (máximo $max caracteres)." }
        require(value.none { it == '\u0000' }) { "$label contiene caracteres inválidos." }
    }
    fun id(value: String) = text(value, 100, "Identificador")
    fun bills(values: List<FixedExpense>) {
        require(values.size <= 100) { "Máximo 100 gastos fijos." }
        require(values.map { it.id }.toSet().size == values.size) { "Identificadores de gastos fijos duplicados." }
        values.forEach { id(it.id); text(it.name, 100, "Nombre del gasto fijo"); money(it.amount) }
    }
    val s = data.settings
    money(s.salary); money(s.variableBudget); money(s.manualTarget)
    require(s.payday in 1..31) { "El día de cobro debe estar entre 1 y 31." }
    require(s.currency in currencies) { "Moneda no admitida." }
    bills(s.fixedExpenses)
    require(data.goals.size <= 100) { "Máximo 100 objetivos." }
    require(data.goals.map { it.id }.toSet().size == data.goals.size) { "Identificadores de objetivos duplicados." }
    data.goals.forEach {
        id(it.id); text(it.name, 100, "Nombre del objetivo")
        money(it.targetAmount); money(it.savedAmount); money(it.periodAmount)
        require(it.targetAmount > 0) { "La cantidad total del objetivo debe ser mayor que cero." }
        require(it.currency in currencies) { "Moneda del objetivo no admitida." }
    }
    require(data.cycles.size <= 1000 && data.movements.size <= 50000) { "Máximo 1000 ciclos y 50000 movimientos." }
    require(data.cycles.map { it.id }.toSet().size == data.cycles.size) { "Identificadores de ciclos duplicados." }
    require(data.movements.map { it.id }.toSet().size == data.movements.size) { "Identificadores de movimientos duplicados." }
    data.cycles.forEach {
        id(it.id)
        require(it.end > it.start) { "La fecha final debe ser posterior al inicio del ciclo." }
        require(it.start.year in 1900..9999 && it.end.year in 1900..9999) { "Fecha de ciclo fuera de límite (1900 a 9999)." }
        money(it.income); money(it.variableBudget); money(it.target); bills(it.bills)
        require(it.currency in currencies) { "Moneda del ciclo no admitida." }
    }
    data.cycles.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
        require(a.end <= b.start) { "Los ciclos no pueden solaparse." }
    }
    val cycles = data.cycles.associateBy { it.id }
    val fixedPayments = mutableSetOf<Pair<String, String>>()
    data.movements.forEach {
        id(it.id); id(it.cycleId); money(it.amount)
        text(it.category, 100, "Categoría"); text(it.note, 1000, "Nota", blank = true)
        val c = requireNotNull(cycles[it.cycleId]) { "Movimiento con ciclo inexistente." }
        require(it.date >= c.start && it.date < c.end) { "La fecha del movimiento debe estar dentro del ciclo." }
        it.billId?.let { billId ->
            id(billId)
            require(!it.income && c.bills.any { bill -> bill.id == billId }) { "Pago fijo con referencia inválida." }
            require(fixedPayments.add(c.id to billId)) { "Un gasto fijo no puede pagarse dos veces en el mismo ciclo." }
        }
    }
}
