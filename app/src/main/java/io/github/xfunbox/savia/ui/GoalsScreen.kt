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

@Composable
internal fun GoalsScreen(data: AppData, cycle: Cycle?, busy: Boolean, save: (AppData) -> Unit) {
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavingsGoal?>(null) }
    var pending by remember { mutableStateOf<AppData?>(null) }
    LaunchedEffect(data, pending) {
        if (pending != null && data == pending) {
            creating = false; editing = null; deleting = null; pending = null
        }
    }
    fun submit(next: AppData) {
        validateData(next)
        pending = next
        save(next)
    }
    Text("Un presupuesto, varios objetivos", style = MaterialTheme.typography.titleLarge)
    Text("Reparte el ahorro que reservas cada periodo. El progreso refleja lo que tú indiques que ya has ahorrado.", style = MaterialTheme.typography.bodyMedium)
    if (cycle != null) {
        val summary = summarize(cycle, data.movements)
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Amount("Ahorro reservado en este periodo", cycle.target, cycle.currency)
                MoneyLine("Ahorro previsto al terminar", summary.forecast, cycle.currency)
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
    if (data.goals.isEmpty()) Text("Todavía no tienes objetivos. Crea uno, por ejemplo Vacaciones o Fondo de emergencia.")
    data.goals.forEach { goal ->
        key(goal.id) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.titleLarge)
                    Text("${formatMoney(goal.savedAmount, goal.currency)} de ${formatMoney(goal.targetAmount, goal.currency)}", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (goal.savedAmount.toFloat() / goal.targetAmount).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                            contentDescription = "${goal.name}: ahorrados ${formatMoney(goal.savedAmount, goal.currency)} de ${formatMoney(goal.targetAmount, goal.currency)}"
                        })
                    Text(if (goal.savedAmount >= goal.targetAmount) "¡Objetivo alcanzado!" else "Te faltan ${formatMoney(goal.targetAmount - goal.savedAmount, goal.currency)}")
                    MoneyLine("Aportación prevista por periodo", goal.periodAmount, goal.currency)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { editing = goal }) { Text("Editar / actualizar ahorro") }
                        IconButton(enabled = !busy, onClick = { deleting = goal }) { Icon(Icons.Default.DeleteOutline, "Eliminar ${goal.name}") }
                    }
                }
            }
        }
    }
    if (creating || editing != null) GoalForm(editing, data.settings.currency, { creating = false; editing = null; pending = null }) { goal ->
        submit(data.copy(goals = data.goals.filterNot { it.id == goal.id } + goal))
    }
    deleting?.let { goal ->
        AlertDialog(onDismissRequest = { if (!busy) { deleting = null; pending = null } }, title = { Text("¿Eliminar ${goal.name}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borrará el objetivo y su progreso registrado. Tu presupuesto, tus movimientos y tu dinero no cambian. Esta acción no se puede deshacer.")
                LocalFormSaveError.current?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { submit(data.copy(goals = data.goals.filterNot { it.id == goal.id })) }) { Text(if (busy) "Eliminando…" else "Eliminar objetivo") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null; pending = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun GoalForm(previous: SavingsGoal?, defaultCurrency: String, dismiss: () -> Unit, save: (SavingsGoal) -> Unit) {
    val busy = LocalFormBusy.current
    var name by remember { mutableStateOf(previous?.name ?: "") }
    var target by remember { mutableStateOf(previous?.let { inputMoney(it.targetAmount) } ?: "") }
    var saved by remember { mutableStateOf(inputMoney(previous?.savedAmount ?: 0)) }
    var period by remember { mutableStateOf(inputMoney(previous?.periodAmount ?: 0)) }
    var currency by remember { mutableStateOf(previous?.currency ?: defaultCurrency) }
    Form(if (previous == null) "Nuevo objetivo" else "Editar objetivo", dismiss, {
        val goal = SavingsGoal(previous?.id ?: UUID.randomUUID().toString(), name.trim(), money(target), money(saved), money(period), currency)
        save(goal)
    }) {
        Field("Nombre del objetivo", name, { name = it })
        if (previous == null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EUR", "USD", "GBP", "MXN").forEach { code -> FilterChip(enabled = !busy, selected = currency == code, onClick = { currency = code }, label = { Text(code) }) }
        } else Text("Moneda: $currency. Los objetivos conservan su moneda.", style = MaterialTheme.typography.bodySmall)
        Field("¿Cuánto quieres reunir? ($currency)", target, { target = it }, true)
        Field("¿Cuánto tienes ya ahorrado? ($currency)", saved, { saved = it }, true)
        Text("Actualiza este importe cuando apartes dinero de verdad. No se registra como gasto ni como ingreso.", style = MaterialTheme.typography.bodySmall)
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
