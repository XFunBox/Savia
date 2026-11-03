package io.github.xfunbox.savia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

private val shortDate = DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("es-ES"))
private fun LocalDate.readable() = format(shortDate)

@Composable
internal fun Screens(data: AppData, page: Int, today: LocalDate, busy: Boolean,
    save: (AppData) -> Unit, onSettings: () -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    addRequest: Int = 0, onPlan: () -> Unit = {}) {
    val cycle = data.cycles.maxByOrNull { it.start }
    var movementPage by remember(cycle?.id) { mutableIntStateOf(0) }
    var movementQuery by remember(cycle?.id) { mutableStateOf("") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingDialogSave by remember { mutableStateOf<AppData?>(null) }
    var edit by remember { mutableStateOf<Movement?>(null) }
    var repeating by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Movement?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    fun change(next: AppData) {
        try { validateData(next); save(next); failure = null }
        catch (e: Exception) { failure = e.message ?: "Revisa los datos." }
    }
    LaunchedEffect(addRequest) {
        if (addRequest > 0 && cycle != null) { edit = null; repeating = false; dialog = "movement" }
    }
    LaunchedEffect(data, pendingDialogSave) {
        if (pendingDialogSave != null && data == pendingDialogSave) {
            dialog = null
            pendingDialogSave = null
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (page) {
            0 -> {
                if (cycle == null) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Savings, null, Modifier.size(40.dp))
                            Text("Tu dinero, con un plan", style = MaterialTheme.typography.headlineMedium)
                            Text(if (data.settings.salary == 0L) "Empieza por tu ingreso y tus gastos habituales. Después verás cuánto puedes gastar y reservar."
                                else "Ya tienes tu plan. Confirma el ingreso recibido para empezar tu primer periodo.")
                            Button(enabled = !busy, onClick = { if (data.settings.salary == 0L) onSettings() else dialog = "cycle" }) {
                                Text(if (data.settings.salary == 0L) "Preparar mi presupuesto" else "Empezar mi periodo")
                            }
                        }
                    }
                } else {
                    val summary = summarize(cycle, data.movements)
                    val active = today >= cycle.start && today < cycle.end
                    val remaining = ChronoUnit.DAYS.between(maxOf(today, cycle.start), cycle.end).coerceAtLeast(0)
                    CycleLabel(cycle)
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, null)
                                Text("Disponible para gastar", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(formatMoney(summary.available, cycle.currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                                color = if (summary.available < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Tras descontar gastos pagados, fijos pendientes y ahorro reservado", style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .18f))
                            Text(if (remaining > 0) "${formatMoney(summary.available / remaining, cycle.currency)} al día · estimación para $remaining días" else "Este periodo ha terminado",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Siguiente cobro · ${cycle.end.readable()}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    BudgetCard(summary.variableSpent, cycle.variableBudget, cycle.currency)
                    Amount("Ahorro previsto al terminar", summary.forecast, cycle.currency)
                    Text("Estimación tras tus gastos previstos; aún no es dinero ahorrado.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !busy, onClick = { dialog = "target" }) { Text("Cambiar ahorro reservado") }
                    Card(onClick = onPlan, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.Savings, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Ahorro reservado este periodo", style = MaterialTheme.typography.labelLarge)
                                Text(formatMoney(cycle.target, cycle.currency), style = MaterialTheme.typography.headlineSmall)
                                Text("Repartir entre mis objetivos", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, "Ver objetivos")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Title("Gastos fijos")
                        Text("${cycle.bills.count { bill -> data.movements.any { it.cycleId == cycle.id && it.billId == bill.id } }} / ${cycle.bills.size} pagados", style = MaterialTheme.typography.labelMedium)
                    }
                    if (cycle.bills.isEmpty()) Text("No tienes gastos fijos en este periodo.", style = MaterialTheme.typography.bodyMedium)
                    if (!active && cycle.bills.isNotEmpty()) Text("Los pagos se marcan durante el periodo.", style = MaterialTheme.typography.bodySmall)
                    Column {
                        cycle.bills.forEach { bill ->
                            val paid = data.movements.firstOrNull { it.cycleId == cycle.id && it.billId == bill.id }
                            Row(Modifier.fillMaxWidth().toggleable(value = paid != null, enabled = !busy && (paid != null || active), role = Role.Checkbox,
                                onValueChange = { checked ->
                                    if (!checked) delete = paid
                                    else change(data.copy(movements = data.movements + Movement(UUID.randomUUID().toString(), cycle.id, today, bill.amount, category = "Fijos", billId = bill.id)))
                                }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = paid != null, onCheckedChange = null, enabled = !busy && (paid != null || active))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(bill.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(paid?.let { "Pagado · ${it.date.readable()}" } ?: "Pendiente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatMoney(bill.amount, cycle.currency), style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        }
                    }
                    if (!today.isBefore(cycle.end)) Button(enabled = !busy, onClick = { dialog = "cycle" }, modifier = Modifier.fillMaxWidth()) { Text("Empezar siguiente periodo") }
                }
            }
            1 -> {
                if (cycle == null) EmptyMessage(Icons.AutoMirrored.Filled.ReceiptLong, "Tu primer movimiento", "Empieza un periodo desde Inicio para añadir ingresos y gastos.")
                else {
                    CycleLabel(cycle)
                    val movements = remember(data.movements, cycle.id) { data.movements.filter { it.cycleId == cycle.id }.sortedByDescending { it.date } }
                    val filtered = remember(movements, movementQuery) { filterMovements(movements, movementQuery) }
                    val currentPage = movementPage.coerceIn(0, lastPage(filtered.size))
                    OutlinedTextField(value = movementQuery, onValueChange = { movementQuery = it; movementPage = 0 },
                        label = { Text("Buscar en este periodo") }, placeholder = { Text("Categoría, nota o importe") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (movementQuery.isNotEmpty()) IconButton(onClick = { movementQuery = ""; movementPage = 0 }) { Icon(Icons.Default.Close, "Limpiar búsqueda") } },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (movements.isEmpty()) EmptyMessage(Icons.Default.AddCircleOutline, "Todo empieza aquí", "Pulsa + para anotar tu primer gasto o ingreso.")
                    else {
                        Text(if (movementQuery.isBlank()) "${movements.size} movimientos" else "${filtered.size} de ${movements.size} movimientos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (filtered.isEmpty()) Text("No hay movimientos que coincidan. Prueba otra categoría, nota o importe.")
                        Column {
                            filtered.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE).forEach { movement ->
                                key(movement.id) {
                                    MovementRow(movement, cycle.currency, busy,
                                        onEdit = { edit = movement; repeating = false; dialog = "movement" },
                                        onDelete = { delete = movement },
                                        onRepeat = if (!movement.income && movement.billId == null && today >= cycle.start && today < cycle.end) {
                                            { edit = repeatExpense(movement, cycle, today); repeating = true; dialog = "movement" }
                                        } else null)
                                }
                            }
                        }
                        Pages(currentPage, filtered.size) { movementPage = it }
                    }
                }
            }
            2 -> GoalsScreen(data, cycle, busy, save)
            3 -> History(data, busy, save)
            4 -> SettingsScreen(data.settings, busy, onSave = { change(data.copy(settings = it)) }, onExport = onExport, onImport = onImport)
        }
        Spacer(Modifier.height(88.dp))
    }
    if (dialog == "cycle") CycleForm(data.settings, cycle, today, onDismiss = { dialog = null; pendingDialogSave = null }) { start, end, income ->
        val next = data.copy(cycles = data.cycles + newCycle(data.settings, start, end, income))
        validateData(next); pendingDialogSave = next; save(next)
    }
    if (dialog == "movement" && cycle != null) MovementForm(cycle, today, edit, isCopy = repeating, onDismiss = { dialog = null; pendingDialogSave = null }) { movement ->
        val next = data.copy(movements = data.movements.filterNot { it.id == movement.id } + movement)
        validateData(next); pendingDialogSave = next; save(next)
    }
    if (dialog == "target" && cycle != null) MoneyForm("Ahorro reservado en este periodo", cycle.target, cycle.currency, { dialog = null; pendingDialogSave = null }) { amount ->
        val next = data.copy(cycles = data.cycles.map { if (it.id == cycle.id) it.copy(target = amount) else it })
        validateData(next); pendingDialogSave = next; save(next)
    }
    delete?.let { movement -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(if (movement.billId != null) "¿Desmarcar este pago?" else "¿Eliminar movimiento?") },
        text = { Text(if (movement.billId != null) "El gasto fijo volverá a estar pendiente." else "Se eliminará este movimiento del periodo.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { change(data.copy(movements = data.movements.filterNot { it.id == movement.id })); delete = null }) { Text(if (movement.billId != null) "Desmarcar" else "Eliminar") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } }) }
}

@Composable private fun History(data: AppData, busy: Boolean, save: (AppData) -> Unit) {
    var deleting by remember { mutableStateOf<Cycle?>(null) }
    var pending by remember { mutableStateOf<AppData?>(null) }
    LaunchedEffect(data, pending) {
        if (pending != null && data == pending) { deleting = null; pending = null }
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { if (!busy) { deleting = null; pending = null } },
            title = { Text("¿Borrar este periodo?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.start.readable()} – ${item.end.minusDays(1).readable()} · ${item.start.year}")
                Text("Se eliminarán el periodo y sus ${data.movements.count { it.cycleId == item.id }} movimientos. Tu presupuesto y tus objetivos se conservarán. Esta acción no se puede deshacer.")
                LocalFormSaveError.current?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { val next = deleteCycle(data, item.id); pending = next; save(next) }) { Text(if (busy) "Borrando…" else "Borrar periodo") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null; pending = null }) { Text("Cancelar") } })
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var historyPage by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val cycles = remember(data.cycles) { data.cycles.sortedByDescending { it.start } }
    val cycle = cycles.firstOrNull { it.id == selected } ?: cycles.firstOrNull()
    if (cycle == null) { EmptyMessage(Icons.Default.BarChart, "Tu dinero de un vistazo", "Aquí verás cómo va cada periodo cuando empieces el primero."); return }
    val currentPage = historyPage.coerceIn(0, lastPage(cycles.size))
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp))
            Text("${cycle.start.readable()} – ${cycle.end.minusDays(1).readable()} · ${cycle.start.year}", modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, "Elegir periodo")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cycles.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE).forEach { item ->
                DropdownMenuItem(text = { Text("${item.start.readable()} – ${item.end.minusDays(1).readable()} · ${item.start.year} · ${item.currency}") },
                    onClick = { selected = item.id; expanded = false })
            }
            if (cycles.size > PAGE_SIZE) {
                DropdownMenuItem(text = { Text("Periodos más recientes") }, enabled = currentPage > 0, onClick = { historyPage-- })
                DropdownMenuItem(text = { Text("Periodos anteriores") }, enabled = currentPage < lastPage(cycles.size), onClick = { historyPage++ })
            }
        }
    }
    val summary = summarize(cycle, data.movements)
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Amount("Saldo registrado", summary.balance, cycle.currency)
            HorizontalDivider()
            MoneyLine("Ingresos", summary.income, cycle.currency, Icons.Default.SouthWest)
            MoneyLine("Gastos pagados", summary.spent, cycle.currency, Icons.Default.NorthEast)
            if (summary.pendingBills > 0) MoneyLine("Fijos pendientes", summary.pendingBills, cycle.currency, Icons.Default.Schedule)
        }
    }
    Text("El saldo es lo registrado; no confirma una transferencia a tu ahorro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Title("En qué has gastado")
    val categories = remember(data.movements, cycle.id) {
        data.movements.filter { it.cycleId == cycle.id && !it.income }.groupBy { it.category }
            .map { (category, movements) -> category to movements.sumOf { it.amount } }.sortedByDescending { it.second }
    }
    var categoryPage by remember(cycle.id) { mutableIntStateOf(0) }
    val currentCategoryPage = categoryPage.coerceIn(0, lastPage(categories.size))
    if (categories.isEmpty()) Text("Aún no hay gastos en este periodo.")
    categories.drop(currentCategoryPage * PAGE_SIZE).take(PAGE_SIZE).forEach { (category, total) ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyLine(category, total, cycle.currency, categoryIcon(category))
            LinearProgressIndicator(progress = { if (summary.spent == 0L) 0f else (total.toFloat() / summary.spent).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "$category: ${formatMoney(total, cycle.currency)} del total de gastos" },
                color = MaterialTheme.colorScheme.secondary)
        }
    }
    Pages(currentCategoryPage, categories.size) { categoryPage = it }
    TextButton(enabled = !busy, onClick = { deleting = cycle }) {
        Icon(Icons.Default.DeleteOutline, null)
        Spacer(Modifier.width(8.dp))
        Text("Borrar este periodo", color = MaterialTheme.colorScheme.error)
    }
}

@Composable private fun BudgetCard(spent: Long, budget: Long, currency: String) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gastos del día a día", style = MaterialTheme.typography.titleMedium)
            Text("${formatMoney(spent, currency)} de ${formatMoney(budget, currency)}", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { if (budget == 0L) { if (spent > 0L) 1f else 0f } else (spent.toFloat() / budget).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = "Presupuesto variable: gastados ${formatMoney(spent, currency)} de ${formatMoney(budget, currency)}" },
                color = if (spent > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(if (spent > budget) "Has superado el presupuesto en ${formatMoney(spent - budget, currency)}" else "Quedan ${formatMoney(budget - spent, currency)} para gastos del día a día",
                style = MaterialTheme.typography.bodySmall, color = if (spent > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun MovementRow(movement: Movement, currency: String, busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRepeat: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy && movement.billId == null, onClickLabel = "Editar movimiento", onClick = onEdit).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(if (movement.income) Icons.Default.SouthWest else categoryIcon(movement.category), null, Modifier.padding(12.dp).size(22.dp),
                tint = if (movement.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
        }
        Column(Modifier.weight(1f)) {
            Text(movement.note.ifBlank { movement.category }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${movement.category} · ${movement.date.readable()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${if (movement.income) "+" else "−"}${formatMoney(movement.amount, currency)}", style = MaterialTheme.typography.titleSmall,
            color = if (movement.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Box {
            IconButton(enabled = !busy, onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de ${movement.category}, ${formatMoney(movement.amount, currency)}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onRepeat != null) DropdownMenuItem(text = { Text("Repetir gasto") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, enabled = !busy,
                    onClick = { menu = false; onRepeat() })
                DropdownMenuItem(text = { Text(if (movement.billId == null) "Eliminar" else "Desmarcar pago") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
}

private fun categoryIcon(category: String): ImageVector = when (category.lowercase(Locale.ROOT)) {
    "comida", "alimentación", "supermercado" -> Icons.Default.Restaurant
    "transporte" -> Icons.Default.DirectionsBus
    "ocio" -> Icons.Default.Celebration
    "salud" -> Icons.Default.FavoriteBorder
    "compras", "ropa" -> Icons.Default.ShoppingBag
    "fijos", "hogar", "vivienda" -> Icons.Default.Home
    else -> Icons.AutoMirrored.Filled.ReceiptLong
}
@Composable private fun EmptyMessage(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable internal fun MoneyLine(label: String, amount: Long, currency: String, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary) }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatMoney(amount, currency), style = MaterialTheme.typography.titleSmall)
    }
}
private const val PAGE_SIZE = 50
private fun lastPage(count: Int) = ((count - 1) / PAGE_SIZE).coerceAtLeast(0)
@Composable private fun Pages(page: Int, count: Int, onPage: (Int) -> Unit) {
    if (count > PAGE_SIZE) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = page > 0, onClick = { onPage(page - 1) }) { Text("Anterior") }
        Text("${page + 1} / ${lastPage(count) + 1}")
        TextButton(enabled = page < lastPage(count), onClick = { onPage(page + 1) }) { Text("Siguiente") }
    }
}
@Composable internal fun Title(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
@Composable private fun CycleLabel(cycle: Cycle) { Text("${cycle.start.readable()} – ${cycle.end.minusDays(1).readable()} · ${cycle.start.year}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable internal fun Amount(label: String, amount: Long, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(formatMoney(amount, currency), style = MaterialTheme.typography.headlineMedium, color = if (amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}
