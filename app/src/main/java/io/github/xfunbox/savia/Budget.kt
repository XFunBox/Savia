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

data class FixedExpense(val id: String = "", val name: String = "", val amount: Long = 0,
    val dueDay: Int? = null, val dueMonth: Int? = null, val dueDate: LocalDate? = null)
data class Settings(
    val salary: Long = 0, val payday: Int = 1, val variableBudget: Long = 0,
    val manualTarget: Long = 0, val automaticTarget: Boolean = true,
    val currency: String = "EUR", val fixedExpenses: List<FixedExpense> = emptyList(),
    val categoryLimits: Map<String, Long> = emptyMap()
)
data class Cycle(
    val id: String = "", val start: LocalDate = LocalDate.now(), val end: LocalDate = start.plusMonths(1),
    val income: Long = 0, val variableBudget: Long = 0, val target: Long = 0,
    val currency: String = "EUR", val bills: List<FixedExpense> = emptyList(),
    val categoryLimits: Map<String, Long> = emptyMap()
)
data class Movement(
    val id: String = "", val cycleId: String = "", val date: LocalDate = LocalDate.now(),
    val amount: Long = 0, val income: Boolean = false, val category: String = "Otros",
    val note: String = "", val billId: String? = null
)
data class AppData(
    val settings: Settings = Settings(), val cycles: List<Cycle> = emptyList(),
    val movements: List<Movement> = emptyList(), val goals: List<SavingsGoal> = emptyList(),
    val closures: List<PeriodClosure> = emptyList()
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
    (settings.salary - fixedBudgetPerPeriod(settings.fixedExpenses) - settings.variableBudget).coerceAtLeast(0)

fun nextPayday(after: LocalDate, payday: Int): LocalDate {
    require(payday in 1..31) { "El día de cobro debe estar entre 1 y 31." }
    val month = YearMonth.from(after)
    val current = month.atDay(minOf(payday, month.lengthOfMonth()))
    if (current > after) return current
    val next = month.plusMonths(1)
    return next.atDay(minOf(payday, next.lengthOfMonth()))
}

fun newCycle(settings: Settings, start: LocalDate, end: LocalDate, income: Long = settings.salary): Cycle {
    require(end > start) { "La fecha final debe ser posterior al inicio del ciclo." }
    val bills = billsForCycle(settings.fixedExpenses, start, end)
    val snapshot = settings.copy(salary = income, fixedExpenses = bills.map { it.copy(dueDay = null, dueMonth = null) })
    val cycle = Cycle(UUID.randomUUID().toString(), start, end, income, settings.variableBudget,
        if (settings.automaticTarget) suggestedTarget(snapshot) else settings.manualTarget,
        settings.currency, bills, settings.categoryLimits.toMap())
    validateData(AppData(settings, listOf(cycle)))
    return cycle
}

fun summarize(cycle: Cycle, movements: List<Movement>, goals: List<SavingsGoal> = emptyList()): BudgetSummary {
    val entries = movements.filter { it.cycleId == cycle.id }
    val income = cycle.income + entries.filter { it.income }.sumOf { it.amount }
    val expenses = entries.filterNot { it.income }
    val spent = expenses.sumOf { it.amount }
    val paid = expenses.mapNotNull { it.billId }.toSet()
    val pending = cycle.bills.filterNot { it.id in paid }.sumOf { it.amount }
    val variable = expenses.filter { it.billId == null }.sumOf { it.amount }
    val fixedPaid = expenses.filter { it.billId != null }.sumOf { it.amount }
    // Reserved contributions are already included in target. Only net additional savings reduce
    // available; withdrawals cannot increase it above the amount before additional contributions.
    val additional = goals.filter { it.currency == cycle.currency }.flatMap { it.entries }
        .filter { it.cycleId == cycle.id && it.additional }
        .sumOf { if (it.withdrawal) -it.amount else it.amount }.coerceAtLeast(0)
    return BudgetSummary(income, spent, pending, variable, income - spent - pending - cycle.target - additional,
        income - fixedPaid - pending - maxOf(cycle.variableBudget, variable), cycle.target, income - spent)
}

fun deleteCycle(data: AppData, cycleId: String): AppData {
    require(data.cycles.any { it.id == cycleId }) { "El periodo ya no existe." }
    return data.copy(cycles = data.cycles.filterNot { it.id == cycleId },
        closures = data.closures.filterNot { it.cycleId == cycleId },
        movements = data.movements.filterNot { it.cycleId == cycleId },
        goals = data.goals.map { goal -> goal.copy(entries = goal.entries.map {
            if (it.cycleId == cycleId) it.copy(cycleId = null) else it
        }) })
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
        values.forEach {
            id(it.id); text(it.name, 100, "Nombre del gasto fijo"); money(it.amount)
            require(it.dueDay == null || it.dueDay in 1..31) { "Día de vencimiento inválido." }
            require(it.dueMonth == null || (it.dueDay != null && it.dueMonth in 1..12)) { "Mes de vencimiento inválido." }
            it.dueDate?.let { date -> require(date.year in 1900..9999) { "Fecha de vencimiento inválida." } }
        }
    }
    fun limits(values: Map<String, Long>) {
        require(values.size <= 100) { "Máximo 100 límites por categoría." }
        require(values.keys.map(::categoryKey).toSet().size == values.size) { "Categorías duplicadas en los límites." }
        values.forEach { (category, amount) -> text(category, 100, "Categoría"); money(amount) }
    }
    val s = data.settings
    money(s.salary); money(s.variableBudget); money(s.manualTarget)
    require(s.payday in 1..31) { "El día de cobro debe estar entre 1 y 31." }
    require(s.currency in currencies) { "Moneda no admitida." }
    bills(s.fixedExpenses); limits(s.categoryLimits)
    require(s.fixedExpenses.none { it.dueDate != null }) { "El presupuesto contiene un vencimiento de periodo." }
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
        money(it.income); money(it.variableBudget); money(it.target); bills(it.bills); limits(it.categoryLimits)
        val cycle = it
        require(it.bills.all { bill -> bill.dueDate == null || (bill.dueDate >= cycle.start && bill.dueDate < cycle.end) }) { "Vencimiento fuera del periodo." }
        require(it.currency in currencies) { "Moneda del ciclo no admitida." }
    }
    data.cycles.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
        require(a.end <= b.start) { "Los ciclos no pueden solaparse." }
    }
    val cycles = data.cycles.associateBy { it.id }
    require(data.goals.sumOf { it.entries.size } <= 50000) { "Máximo 50000 entradas de ahorro." }
    val savingsIds = mutableSetOf<String>()
    data.goals.forEach { goal ->
        goal.targetDate?.let { require(it.year in 1900..9999) { "Fecha objetivo fuera de límite (1900 a 9999)." } }
        var balance = goal.savedAmount
        // Stable date order: entries on the same date retain their stored order.
        goal.entries.sortedBy { it.date }.forEach {
            id(it.id)
            require(savingsIds.add(it.id)) { "Identificadores de entradas de ahorro duplicados." }
            money(it.amount)
            require(it.amount > 0) { "La entrada de ahorro debe ser mayor que cero." }
            text(it.note, 1000, "Nota", blank = true)
            require(it.date.year in 1900..9999) { "Fecha de ahorro fuera de límite (1900 a 9999)." }
            it.cycleId?.let { cycleId ->
                id(cycleId)
                val cycle = requireNotNull(cycles[cycleId]) { "Entrada de ahorro con periodo inexistente." }
                require(cycle.currency == goal.currency) { "La moneda del objetivo y del periodo deben coincidir." }
                require(it.date >= cycle.start && it.date < cycle.end) { "La fecha del ahorro debe estar dentro del periodo." }
            }
            balance += if (it.withdrawal) -it.amount else it.amount
            require(balance in 0..MAX_MONEY) { "El saldo del objetivo queda fuera de límite en su historial." }
        }
    }
    require(data.closures.size <= 1000 && data.closures.map { it.cycleId }.toSet().size == data.closures.size) { "Cierres duplicados o demasiados cierres." }
    val closingIds = mutableSetOf<String>()
    val savingsById = data.goals.flatMap { it.entries }.associateBy { it.id }
    data.closures.forEach {
        require(it.cycleId in cycles) { "Cierre con periodo inexistente." }
        require(it.reviewedOn.year in 1900..9999 && it.reviewedOn >= cycles.getValue(it.cycleId).end) { "Fecha de revisión inválida." }
        require(Regex("[a-f0-9]{64}").matches(it.fingerprint)) { "Huella de cierre inválida." }
        require(it.entryIds.size <= 200 && it.entryIds.toSet().size == it.entryIds.size) { "Entradas de cierre inválidas." }
        val closing = it
        it.entryIds.forEach { entryId ->
            id(entryId)
            require(closingIds.add(entryId)) { "Una aportación no puede pertenecer a dos cierres." }
            savingsById[entryId]?.let { entry ->
                require(entry.cycleId == closing.cycleId && !entry.withdrawal) {
                    "Una aportación del cierre debe conservar su periodo y ser una aportación. Corrige el cierre o registra una retirada aparte."
                }
            }
        }
    }
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
