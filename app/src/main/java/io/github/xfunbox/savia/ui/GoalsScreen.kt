package io.github.xfunbox.savia.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.*
import java.util.UUID
import java.time.LocalDate

@Composable
internal fun GoalsScreen(data: AppData, cycle: Cycle?, busy: Boolean, save: (AppData) -> Unit) {
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavingsGoal?>(null) }
    var entryGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var editingEntry by remember { mutableStateOf<SavingsEntry?>(null) }
    var withdrawal by remember { mutableStateOf(false) }
    var removingEntry by remember { mutableStateOf<Pair<SavingsGoal, SavingsEntry>?>(null) }
    var history by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<AppData?>(null) }
    var sourceMovement by remember { mutableStateOf<Movement?>(null) }
    LaunchedEffect(data, pending) {
        if (pending != null && data == pending) {
            creating = false; editing = null; deleting = null; entryGoal = null; editingEntry = null; removingEntry = null; sourceMovement = null; pending = null
        }
    }
    fun submit(next: AppData) {
        validateData(next)
        pending = next
        save(next)
    }
    SectionIntro("Haz sitio a lo que quieres", "Reparte tu reserva entre objetivos. El progreso muestra solo el ahorro que has registrado.")
    if (cycle != null) {
        val summary = summarize(cycle, data.movements, data.goals)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f))) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Amount("Ahorro reservado en este periodo", cycle.target, cycle.currency)
                MoneyLine("Ahorro previsto al terminar", summary.forecast, cycle.currency)
                if (cycle.target > summary.forecast) Text("Tu reserva supera el ahorro previsto en ${formatMoney(cycle.target - summary.forecast, cycle.currency)}.", color = MaterialTheme.colorScheme.error)
                val assigned = data.goals.filter { it.currency == cycle.currency }.sumOf { it.periodAmount }
                MoneyLine("Repartido entre objetivos", assigned, cycle.currency)
                if (assigned > cycle.target) Text("Las aportaciones superan tu reserva en ${formatMoney(assigned - cycle.target, cycle.currency)}.", color = MaterialTheme.colorScheme.error)
                else MoneyLine("Reserva sin repartir", cycle.target - assigned, cycle.currency)
                if (assigned > summary.forecast) Text("Con los gastos previstos no alcanzarías las aportaciones de este periodo.", color = MaterialTheme.colorScheme.error)
                Text("Las aportaciones son una previsión: no descuentan dinero otra vez ni aumentan automáticamente lo ahorrado.", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Text("Puedes preparar tus objetivos ahora. Al empezar un periodo verás cuánto ahorro tienes para repartir.", style = MaterialTheme.typography.bodySmall)
    }
    Button(enabled = !busy && data.goals.size < 100, onClick = { creating = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear objetivo")
    }
    if (data.goals.isEmpty()) GlassPanel {
        Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
        Text("Tu próxima meta empieza aquí", style = MaterialTheme.typography.titleLarge)
        Text("Un viaje, un colchón para imprevistos… Crea tu primer objetivo y elige tu ritmo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    data.goals.forEach { goal ->
        val total = goalSaved(goal)
        key(goal.id) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = .14f)) {
                            Icon(Icons.Default.Flag, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                        Text(goal.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                    Text(formatMoney(total, goal.currency), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Ahorrado de ${formatMoney(goal.targetAmount, goal.currency)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { (total.toFloat() / goal.targetAmount).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                            contentDescription = "${goal.name}: ahorrados ${formatMoney(total, goal.currency)} de ${formatMoney(goal.targetAmount, goal.currency)}"
                        })
                    Text(if (total >= goal.targetAmount) "¡Objetivo alcanzado!" else "Te faltan ${formatMoney(goal.targetAmount - total, goal.currency)}")
                    goal.targetDate?.let { Text("Fecha de meta: $it", style = MaterialTheme.typography.bodyMedium) }
                    GoalSuggestion(goal, data.settings, cycle)
                    MoneyLine("Aportación prevista por periodo", goal.periodAmount, goal.currency)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), enabled = !busy, onClick = { entryGoal = goal; editingEntry = null; withdrawal = false }) { Text("Añadir ahorro") }
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy && total > 0, onClick = { entryGoal = goal; editingEntry = null; withdrawal = true }) { Text("Retirar") }
                    }
                    TextButton(onClick = { history = if (history == goal.id) null else goal.id }) { Text(if (history == goal.id) "Ocultar historial" else "Ver historial (${goal.entries.size})") }
                    if (history == goal.id) {
                        MoneyLine("Saldo inicial (sin fecha histórica)", goal.savedAmount, goal.currency)
                        goal.entries.asReversed().sortedByDescending { it.date }.forEach { entry ->
                            Text("${entry.date} · ${if (entry.withdrawal) "Retirada" else "Aportación"} · ${formatMoney(entry.amount, goal.currency)}")
                            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
                            Text(if (entry.cycleId == null) "Ahorro externo, sin periodo" else if (entry.additional) "Adicional a la reserva del periodo" else "Dentro de la reserva del periodo", style = MaterialTheme.typography.bodySmall)
                            if (entry.sourceMovementId != null) {
                                Text("Vinculado al ingreso extra. Los cambios del ingreso actualizan esta aportación.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(enabled = !busy, onClick = {
                                    sourceMovement = data.movements.firstOrNull { it.id == entry.sourceMovementId }
                                }) { Text("Ver ingreso original") }
                            } else {
                                Row {
                                    TextButton(enabled = !busy, onClick = { entryGoal = goal; editingEntry = entry; withdrawal = entry.withdrawal }) { Text("Corregir") }
                                    TextButton(enabled = !busy, onClick = { removingEntry = goal to entry }) { Text("Eliminar entrada") }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { editing = goal }) { Text("Editar objetivo") }
                        IconButton(enabled = !busy, onClick = { deleting = goal }) { Icon(Icons.Default.DeleteOutline, "Eliminar ${goal.name}") }
                    }
                }
            }
        }
    }
    if (creating || editing != null) GoalForm(editing, data.settings.currency, { creating = false; editing = null; pending = null }) { goal ->
        submit(data.copy(goals = data.goals.filterNot { it.id == goal.id } + goal))
    }
    sourceMovement?.let { movement ->
        val sourceCycle = data.cycles.firstOrNull { it.id == movement.cycleId }
        if (sourceCycle != null) key(movement.id) {
            MovementForm(sourceCycle, LocalDate.now(), movement, goals = data.goals,
                onDismiss = { sourceMovement = null; pending = null }) { corrected, allocation ->
                submit(saveMovement(data, corrected, allocation))
            }
        }
    }
    entryGoal?.let { goal ->
        SavingsEntryForm(goal, data.cycles, cycle, editingEntry, withdrawal, { entryGoal = null; pending = null }) { entry ->
            submit(putSavingsEntry(data, goal.id, entry))
        }
    }
    removingEntry?.let { (goal, entry) ->
        var localError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!busy) removingEntry = null }, title = { Text("¿Eliminar esta entrada?") },
            text = { Column { Text("Se recalculará el ahorro y, si procede, el disponible del periodo. No se puede dejar un saldo negativo.")
                (localError ?: LocalFormSaveError.current)?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(enabled = !busy, onClick = { try { submit(removeSavingsEntry(data, goal.id, entry.id)) } catch (e: Exception) { localError = e.message } }) { Text("Eliminar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { removingEntry = null }) { Text("Cancelar") } })
    }
    deleting?.let { goal ->
        AlertDialog(onDismissRequest = { if (!busy) { deleting = null; pending = null } }, title = { Text("¿Eliminar ${goal.name}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borrará el objetivo, su saldo inicial y todo su historial. Se liberará cualquier ahorro adicional atribuido a un periodo. Tu presupuesto, tus movimientos y tu dinero no cambian. Esta acción no se puede deshacer.")
                LocalFormSaveError.current?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { submit(data.copy(goals = data.goals.filterNot { it.id == goal.id })) }) { Text(if (busy) "Eliminando…" else "Eliminar objetivo") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null; pending = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun GoalForm(previous: SavingsGoal?, defaultCurrency: String, dismiss: () -> Unit, save: (SavingsGoal) -> Unit) {
    val busy = LocalFormBusy.current
    val id = remember { previous?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(previous?.name ?: "") }
    var target by remember { mutableStateOf(previous?.let { inputMoney(it.targetAmount) } ?: "") }
    var saved by remember { mutableStateOf("0.00") }
    var dated by remember { mutableStateOf(previous?.targetDate != null) }
    var targetDate by remember { mutableStateOf(previous?.targetDate ?: LocalDate.now().plusMonths(6)) }
    var period by remember { mutableStateOf(inputMoney(previous?.periodAmount ?: 0)) }
    var currency by remember { mutableStateOf(previous?.currency ?: defaultCurrency) }
    Form(if (previous == null) "Nuevo objetivo" else "Editar objetivo", dismiss, {
        val goal = (previous ?: SavingsGoal(id = id, savedAmount = money(saved))).copy(name = name.trim(), targetAmount = money(target), periodAmount = money(period), currency = currency, targetDate = if (dated) targetDate else null)
        save(goal)
    }) {
        Field("Nombre del objetivo", name, { name = it })
        if (previous == null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EUR", "USD", "GBP", "MXN").forEach { code -> FilterChip(enabled = !busy, selected = currency == code, onClick = { currency = code }, label = { Text(code) }) }
        } else Text("Moneda: $currency. Los objetivos conservan su moneda.", style = MaterialTheme.typography.bodySmall)
        Field("¿Cuánto quieres reunir? ($currency)", target, { target = it }, true)
        if (previous == null) Field("Saldo inicial ya ahorrado ($currency)", saved, { saved = it }, true)
        else Text("El saldo inicial se conserva. Usa Añadir ahorro, Retirar o Corregir para registrar cambios con historial.", style = MaterialTheme.typography.bodySmall)
        FilterChip(enabled = !busy, selected = dated, onClick = { dated = !dated }, label = { Text(if (dated) "Con fecha objetivo" else "Sin fecha objetivo") })
        if (dated) DateField("Fecha de meta", targetDate, LocalDate.now(), { targetDate = it })
        Field("Aportación prevista por periodo ($currency)", period, { period = it }, true)
        Text("Es parte del ahorro reservado de tu presupuesto. Déjala en 0 si todavía no has decidido cuánto aportar.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun BudgetBreakdown(income: Long, fixed: Long, daily: Long, reserve: Long, currency: String) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title("Así se reparte tu ingreso")
            MoneyLine("Ingresos", income, currency)
            MoneyLine("Gastos fijos previstos", fixed, currency)
            MoneyLine("Gastos del día a día previstos", daily, currency)
            HorizontalDivider()
            MoneyLine("Lo que queda para ahorrar", income - fixed - daily, currency)
            MoneyLine("Ahorro que quieres reservar", reserve, currency)
            Text("Es una planificación para el próximo periodo. No confirma dinero ahorrado.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GoalSuggestion(goal: SavingsGoal, settings: Settings, cycle: Cycle?) {
    val suggestion = suggestGoalContribution(goal, settings)
    val text = when (suggestion.status) {
        GoalSuggestionStatus.NO_DATE -> "Sin fecha: tú eliges el ritmo de ahorro."
        GoalSuggestionStatus.COMPLETED -> "Meta cubierta con tu ahorro confirmado."
        GoalSuggestionStatus.OVERDUE -> "La fecha de meta ha pasado. Puedes revisarla sin perder tu progreso."
        GoalSuggestionStatus.INVALID_CALENDAR -> "No hay un cobro válido antes de la meta. Revisa fecha e ingresos en Mi presupuesto."
        GoalSuggestionStatus.CURRENCY_MISMATCH -> "La moneda del presupuesto es distinta. No calculamos ni convertimos aportaciones."
        GoalSuggestionStatus.READY -> "Sugerencia: ${formatMoney(suggestion.amountPerPayday, goal.currency)} en cada uno de ${suggestion.paydays} cobros, empezando el ${suggestion.firstPayday}. Si hoy cobras, cuenta como primera aportación. No cambia tu reserva."
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
    if (cycle != null && cycle.currency != goal.currency) Text("Este objetivo no se reparte en el periodo actual (${cycle.currency}).", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SavingsEntryForm(goal: SavingsGoal, cycles: List<Cycle>, cycle: Cycle?, previous: SavingsEntry?, withdrawal: Boolean,
    dismiss: () -> Unit, save: (SavingsEntry) -> Unit) {
    val id = remember { previous?.id ?: UUID.randomUUID().toString() }
    val today = LocalDate.now()
    var amount by remember { mutableStateOf(previous?.let { inputMoney(it.amount) } ?: "") }
    var date by remember { mutableStateOf(previous?.date ?: today) }
    var note by remember { mutableStateOf(previous?.note ?: "") }
    val eligible = cycle?.takeIf { it.currency == goal.currency && date >= it.start && date < it.end }
    val originalCycle = cycles.find { it.id == previous?.cycleId }
    var selectedCycleId by remember { mutableStateOf(previous?.cycleId ?: if (previous == null) eligible?.id else null) }
    var additional by remember { mutableStateOf(previous?.additional ?: false) }
    Form(if (previous != null) "Corregir entrada" else if (withdrawal) "Registrar retirada" else "Añadir ahorro", dismiss, {
        val selectedCycle = cycles.find { it.id == selectedCycleId }
        if (selectedCycleId != null) {
            require(selectedCycle != null && selectedCycle.currency == goal.currency && date >= selectedCycle.start && date < selectedCycle.end) {
                "La fecha no pertenece al periodo elegido. Revisa la fecha o elige otro periodo o ahorro externo."
            }
        }
        save(SavingsEntry(id, date, money(amount), withdrawal, note.trim(), selectedCycleId, selectedCycleId != null && additional))
    }) {
        Text("${goal.name} · Ahorrado: ${formatMoney(goalSaved(goal), goal.currency)}")
        Field("Importe (${goal.currency})", amount, { amount = it }, true)
        DateField("Fecha real", date, today, { date = it }, maximum = maxOf(today, previous?.date ?: today))
        Field("Nota (opcional)", note, { note = it })
        if (eligible != null || selectedCycleId != null || originalCycle != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedCycleId == null, enabled = !LocalFormBusy.current,
                    onClick = { selectedCycleId = null }, label = { Text("Ahorro externo, sin periodo") })
                listOfNotNull(originalCycle, eligible).distinctBy { it.id }.forEach { option ->
                    FilterChip(selected = selectedCycleId == option.id, enabled = !LocalFormBusy.current,
                        onClick = { selectedCycleId = option.id }, label = {
                            Text("${if (option.id == cycle?.id) "Periodo actual" else "Periodo original"}: ${option.start} – ${option.end.minusDays(1)}")
                        })
                }
            }
            if (selectedCycleId != null) {
                FilterChip(selected = additional, enabled = !LocalFormBusy.current, onClick = { additional = !additional }, label = { Text(if (additional) "Adicional a la reserva" else "Dentro de la reserva") })
                Text(if (additional) "El neto adicional reduce el disponible del periodo. Una retirada adicional libera como máximo lo aportado adicionalmente."
                    else "La reserva ya se descontó del disponible. Esta entrada no vuelve a descontarla.", style = MaterialTheme.typography.bodySmall)
            } else Text("Ahorro externo: no cambia el disponible de ningún periodo.", style = MaterialTheme.typography.bodySmall)
        } else Text("Se registra como ahorro externo: sin un periodo compatible para esta fecha y moneda, no cambia el disponible.", style = MaterialTheme.typography.bodySmall)
        Text("Registra dinero que ya has apartado o retirado. Savia no hace transferencias ni crea gastos o ingresos con estas entradas.", style = MaterialTheme.typography.bodySmall)
    }
}
