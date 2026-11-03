package io.github.xfunbox.savia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.*
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun Screens(data: AppData, page: Int, today: LocalDate, busy: Boolean,
    save: (AppData) -> Unit, onSettings: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val cycle = data.cycles.maxByOrNull { it.start }
    var movementPage by remember(cycle?.id) { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<Movement?>(null) }
    var delete by remember { mutableStateOf<Movement?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    fun change(next: AppData) {
        try { validateData(next); save(next); failure = null }
        catch (e: Exception) { failure = e.message ?: "Revisa los datos." }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (page) {
            0 -> {
                Title("Tu ciclo")
                if (cycle == null) {
                    Text("Empieza configurando tu ingreso y tus gastos fijos. Después abre tu primer ciclo de cobro.")
                    OutlinedButton(onClick = onSettings) { Text("Configurar ajustes") }
                } else {
                    CycleLabel(cycle)
                    val summary = summarize(cycle, data.movements)
                    Amount("Disponible tras reservar fijos y meta", summary.available, cycle.currency)
                    Amount("Meta del ciclo", summary.target, cycle.currency)
                    Amount("Previsión al final del ciclo", summary.forecast, cycle.currency)
                    Text("La previsión estima ingresos menos fijos y el mayor entre tu presupuesto variable y lo gastado. No confirma un ahorro real.", style = MaterialTheme.typography.bodySmall)
                    Title("Gastos fijos")
                    if (cycle.bills.isEmpty()) Text("Este ciclo no tiene gastos fijos.")
                    val canPayToday = !today.isBefore(cycle.start) && today.isBefore(cycle.end)
                    if (!canPayToday && cycle.bills.isNotEmpty()) Text("Solo puedes marcar pagos durante las fechas del ciclo.")
                    cycle.bills.forEach { bill ->
                        val paid = data.movements.firstOrNull { it.cycleId == cycle.id && it.billId == bill.id }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text(bill.name, style = MaterialTheme.typography.titleMedium)
                            Text(formatMoney(bill.amount, cycle.currency))
                            if (paid != null) {
                                Text("Pagado · ${paid.date}")
                                TextButton(enabled = !busy, onClick = { delete = paid }) { Text("Desmarcar pago") }
                            } else Button(enabled = !busy && canPayToday, onClick = {
                                change(data.copy(movements = data.movements + Movement(UUID.randomUUID().toString(), cycle.id, today, bill.amount, category = "Fijos", billId = bill.id)))
                            }) { Text("Marcar pagado") }
                        } }
                    }
                }
                if (cycle == null || !today.isBefore(cycle.end)) Button(enabled = !busy, onClick = { dialog = "cycle" }) { Text(if (cycle == null) "Abrir primer ciclo" else "Abrir siguiente ciclo") }
                else Text("Podrás abrir el siguiente ciclo a partir del ${cycle.end}.")
            }
            1 -> {
                Title("Movimientos")
                if (cycle == null) Text("Abre un ciclo en Inicio para registrar movimientos.")
                else {
                    CycleLabel(cycle)
                    Button(enabled = !busy, onClick = { edit = null; dialog = "movement" }) { Text("Añadir movimiento") }
                    val movements = remember(data.movements, cycle.id) { data.movements.filter { it.cycleId == cycle.id }.sortedByDescending { it.date } }
                    val currentPage = movementPage.coerceIn(0, lastPage(movements.size))
                    if (movements.isEmpty()) Text("Todavía no hay movimientos.")
                    Pages(currentPage, movements.size) { movementPage = it }
                    movements.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE).forEach { movement ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text("${if (movement.income) "+" else "−"}${formatMoney(movement.amount, cycle.currency)}", style = MaterialTheme.typography.titleMedium)
                            Text("${movement.category} · ${movement.date}")
                            if (movement.note.isNotBlank()) Text(movement.note)
                            Row {
                                if (movement.billId == null) TextButton(enabled = !busy, onClick = { edit = movement; dialog = "movement" }) { Text("Editar") }
                                TextButton(enabled = !busy, onClick = { delete = movement }) { Text(if (movement.billId == null) "Eliminar" else "Desmarcar pago") }
                            }
                        } }
                    }
                    Pages(currentPage, movements.size) { movementPage = it }
                }
            }
            2 -> {
                Title("Plan de ahorro")
                val settings = data.settings
                Amount("Sugerencia para próximos ciclos", suggestedTarget(settings), settings.currency)
                Text("Sugerencia = máximo de cero e ingreso − gastos fijos − presupuesto variable.")
                Amount("Ingreso configurado", settings.salary, settings.currency)
                Amount("Fijos configurados", settings.fixedExpenses.sumOf { it.amount }, settings.currency)
                Amount("Presupuesto variable", settings.variableBudget, settings.currency)
                if (cycle != null) {
                    HorizontalDivider()
                    CycleLabel(cycle)
                    Amount("Meta de este ciclo", cycle.target, cycle.currency)
                    val capacity = (cycle.income - cycle.bills.sumOf { it.amount } - cycle.variableBudget).coerceAtLeast(0)
                    if (cycle.target > capacity) Text("La meta supera la capacidad prevista en ${formatMoney(cycle.target - capacity, cycle.currency)}.", color = MaterialTheme.colorScheme.secondary)
                    Button(enabled = !busy, onClick = { dialog = "target" }) { Text("Cambiar meta de este ciclo") }
                }
                Text("Los ajustes afectan a próximos ciclos. Puedes cambiar expresamente la meta del ciclo mostrado.")
            }
            3 -> History(data)
            4 -> SettingsScreen(data.settings, busy, onSave = { change(data.copy(settings = it)) }, onExport = onExport, onImport = onImport)
        }
        Spacer(Modifier.height(16.dp))
    }
    if (dialog == "cycle") CycleForm(data.settings, cycle, today, onDismiss = { dialog = null }) { start, end, income ->
        val next = data.copy(cycles = data.cycles + newCycle(data.settings, start, end, income))
        validateData(next)
        save(next)
        dialog = null
    }
    if (dialog == "movement" && cycle != null) MovementForm(cycle, today, edit, onDismiss = { dialog = null }) { movement ->
        val next = data.copy(movements = data.movements.filterNot { it.id == movement.id } + movement)
        validateData(next)
        save(next)
        dialog = null
    }
    if (dialog == "target" && cycle != null) MoneyForm("Meta del ciclo", cycle.target, cycle.currency, { dialog = null }) { amount ->
        val next = data.copy(cycles = data.cycles.map { if (it.id == cycle.id) it.copy(target = amount) else it })
        validateData(next)
        save(next)
        dialog = null
    }
    delete?.let { movement -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("¿Eliminar este movimiento?") },
        text = { Text(if (movement.billId != null) "El gasto fijo volverá a estar pendiente." else "Se eliminará el movimiento del ciclo.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { change(data.copy(movements = data.movements.filterNot { it.id == movement.id })); delete = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } }) }
}

@Composable private fun History(data: AppData) {
    var selected by remember { mutableStateOf<String?>(null) }
    var historyPage by remember { mutableIntStateOf(0) }
    val cycles = remember(data.cycles) { data.cycles.sortedByDescending { it.start } }
    val currentPage = historyPage.coerceIn(0, lastPage(cycles.size))
    Title("Resúmenes")
    if (data.cycles.isEmpty()) Text("Los ciclos aparecerán aquí cuando abras el primero.")
    Pages(currentPage, cycles.size) { historyPage = it }
    cycles.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE).forEach { cycle ->
        OutlinedButton(onClick = { selected = cycle.id }, modifier = Modifier.fillMaxWidth()) { Text("${cycle.start} · ${cycle.end.minusDays(1)} (${cycle.currency})") }
    }
    Pages(currentPage, cycles.size) { historyPage = it }
    val cycle = data.cycles.firstOrNull { it.id == selected } ?: data.cycles.maxByOrNull { it.start }
    if (cycle != null) {
        CycleLabel(cycle)
        val summary = summarize(cycle, data.movements)
        Amount("Ingresos registrados", summary.income, cycle.currency)
        Amount("Gastos pagados", summary.spent, cycle.currency)
        Amount("Saldo registrado", summary.balance, cycle.currency)
        Amount("Fijos pendientes", summary.pendingBills, cycle.currency)
        Text("El saldo registrado no implica una transferencia real a una cuenta de ahorro.", style = MaterialTheme.typography.bodySmall)
        Title("Gastos por categoría")
        val categories = remember(data.movements, cycle.id) {
            data.movements.filter { it.cycleId == cycle.id && !it.income }.groupBy { it.category }
                .map { (category, movements) -> category to movements.sumOf { it.amount } }.sortedBy { it.first }
        }
        var categoryPage by remember(cycle.id) { mutableIntStateOf(0) }
        val currentCategoryPage = categoryPage.coerceIn(0, lastPage(categories.size))
        if (categories.isEmpty()) Text("Sin gastos registrados.")
        Pages(currentCategoryPage, categories.size) { categoryPage = it }
        categories.drop(currentCategoryPage * PAGE_SIZE).take(PAGE_SIZE).forEach { (category, total) -> Amount(category, total, cycle.currency) }
        Pages(currentCategoryPage, categories.size) { categoryPage = it }
    }
}

private const val PAGE_SIZE = 50
private fun lastPage(count: Int) = ((count - 1) / PAGE_SIZE).coerceAtLeast(0)
@Composable private fun Pages(page: Int, count: Int, onPage: (Int) -> Unit) {
    if (count > PAGE_SIZE) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = page > 0, onClick = { onPage(page - 1) }) { Text("Anterior") }
            Text("${page + 1} / ${lastPage(count) + 1}", modifier = Modifier.padding(top = 12.dp))
            TextButton(enabled = page < lastPage(count), onClick = { onPage(page + 1) }) { Text("Siguiente") }
        }
    }
}

@Composable internal fun Title(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }
@Composable private fun CycleLabel(cycle: Cycle) { Text("${cycle.start} a ${cycle.end.minusDays(1)} · siguiente cobro ${cycle.end}", style = MaterialTheme.typography.bodyMedium) }
@Composable internal fun Amount(label: String, amount: Long, currency: String) {
    Column { Text(label, style = MaterialTheme.typography.bodyMedium); Text(formatMoney(amount, currency), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
}
