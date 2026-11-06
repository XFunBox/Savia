package io.github.xfunbox.savia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.*
import java.time.LocalDate

@Composable internal fun PeriodReview(data: AppData, cycle: Cycle, today: LocalDate, busy: Boolean, save: (AppData) -> Unit) {
    val summary = summarize(cycle, data.movements, data.goals)
    val closure = data.closures.find { it.cycleId == cycle.id }
    var open by remember(cycle.id) { mutableStateOf(false) }
    var pending by remember { mutableStateOf<AppData?>(null) }
    LaunchedEffect(data, pending) { if (pending != null && data == pending) { open = false; pending = null } }
    Title("Revisión del periodo")
    MoneyLine("Gastos previstos", cycle.bills.sumOf { it.amount } + cycle.variableBudget, cycle.currency)
    MoneyLine("Gastos registrados", summary.spent, cycle.currency)
    MoneyLine("Reserva prevista", cycle.target, cycle.currency)
    MoneyLine("Saldo tras fijos pendientes", summary.balance - summary.pendingBills, cycle.currency)
    val saved = data.goals.filter { it.currency == cycle.currency }.flatMap { it.entries }
        .filter { it.cycleId == cycle.id }.sumOf { if (it.withdrawal) -it.amount else it.amount }
    MoneyLine("Ahorro ya registrado (neto)", saved, cycle.currency)
    Text("El saldo pendiente de revisar no es ahorro confirmado. Los pagos pendientes siguen descontados y el próximo periodo requiere confirmar otro ingreso.", style = MaterialTheme.typography.bodySmall)
    if (closure != null) Text(if (periodReviewChanged(data, cycle)) "Las cifras cambiaron desde la revisión del ${closure.reviewedOn}. Revísalas de nuevo."
        else "Revisado el ${closure.reviewedOn}", color = if (periodReviewChanged(data, cycle)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if (today >= cycle.end) OutlinedButton(enabled = !busy, onClick = { open = true }) { Text(if (closure == null) "Revisar y confirmar cierre" else "Revisar cierre") }
    else Text("Podrás confirmar el cierre cuando termine el periodo.", style = MaterialTheme.typography.bodySmall)
    if (open) {
        val goals = data.goals.filter { it.currency == cycle.currency }
        val oldIds = closure?.entryIds.orEmpty().toSet()
        var amounts by remember(cycle.id, closure) { mutableStateOf(goals.associate { goal ->
            goal.id to inputMoney(goal.entries.filter { it.id in oldIds }.sumOf { it.amount })
        }) }
        var allocate by remember { mutableStateOf(false) }
        Form("Revisar cierre", { if (!busy) open = false }, {
            val next = if (!allocate) reviewPeriod(data, cycle, today)
            else closePeriod(data, cycle.id, amounts.mapValues { money(it.value) }, today)
            pending = next; save(next)
        }) {
            Text("Fijos pendientes: ${formatMoney(summary.pendingBills, cycle.currency)}. Ahorro neto ya registrado: ${formatMoney(saved, cycle.currency)}.")
            Text("Guardar la revisión no mueve dinero. Activa el reparto solo para confirmar ahorro que has apartado realmente.")
            FilterChip(enabled = !busy && goals.isNotEmpty(), selected = allocate, onClick = { allocate = !allocate }, label = { Text("Confirmar aportaciones del cierre") })
            if (goals.isEmpty()) Text("No hay objetivos en ${cycle.currency}. Puedes guardar la revisión y crear uno después.")
            if (allocate) {
                Text("Estos importes son aportaciones de este cierre, además de las manuales ya registradas. Al revisar sustituyen las del cierre anterior, evitando duplicados. Para retirar ahorro usa el historial del objetivo.", style = MaterialTheme.typography.bodySmall)
                goals.forEach { goal -> Field("${goal.name} (${cycle.currency})", amounts[goal.id] ?: "0.00", { value -> amounts = amounts + (goal.id to value) }, true) }
                Text("Se usará la reserva disponible y el exceso contará como ahorro adicional. La fecha será el último día del periodo.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
