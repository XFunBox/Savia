package io.github.xfunbox.savia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.AppData
import io.github.xfunbox.savia.Cycle
import io.github.xfunbox.savia.FixedExpense
import io.github.xfunbox.savia.Movement
import io.github.xfunbox.savia.ScheduledPayment
import io.github.xfunbox.savia.categorySpending
import io.github.xfunbox.savia.formatMoney
import io.github.xfunbox.savia.upcomingPayments
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fullPaymentDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-ES"))

@Composable internal fun UpcomingPayments(data: AppData, today: LocalDate, busy: Boolean, onPay: (Cycle, FixedExpense) -> Unit) {
    val latest = data.cycles.maxByOrNull { it.start }
    val payments = upcomingPayments(data, today).filterNot { it.paid } + data.cycles.filter { it.id != latest?.id }.flatMap { cycle ->
        cycle.bills.filter { bill -> bill.dueDate == null && data.movements.none { it.cycleId == cycle.id && it.billId == bill.id } }
            .map { ScheduledPayment(cycle.id, it, cycle.start, false) }
    }
    val ordered = payments.sortedBy { it.date }
    if (ordered.isEmpty()) return
    var count by remember { mutableIntStateOf(10) }
    Title("Próximos pagos y pendientes")
    Text("Incluye pagos de periodos anteriores. Verlos o recibir un aviso no registra un gasto.", style = MaterialTheme.typography.bodySmall)
    ordered.take(count).forEach { payment ->
        val cycle = data.cycles.firstOrNull { it.id == payment.cycleId } ?: return@forEach
        val label = payment.bill.dueDate?.let { date ->
            "${if (date < today) "Vencido" else "Vence"} · ${date.format(fullPaymentDate)}"
        } ?: "Sin vencimiento"
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(payment.bill.name, style = MaterialTheme.typography.titleMedium)
                Text("${formatMoney(payment.bill.amount, cycle.currency)} · $label")
                Text("Periodo ${cycle.start.format(fullPaymentDate)} – ${cycle.end.minusDays(1).format(fullPaymentDate)}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy && cycle.start <= today, onClick = { onPay(cycle, payment.bill) }) { Text("Marcar pagado…") }
            }
        }
    }
    if (ordered.size > count) TextButton(onClick = { count += 10 }) { Text("Ver más pagos (${ordered.size - count} pendientes)") }
}

@Composable internal fun CategoryLimits(cycle: Cycle, movements: List<Movement>) {
    val categories = categorySpending(cycle, movements)
    if (categories.isEmpty()) return
    Title("Límites del periodo")
    Text("Reparten tu presupuesto diario; no son una reserva adicional.", style = MaterialTheme.typography.bodySmall)
    categories.forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.category, style = MaterialTheme.typography.titleSmall)
            Text("Gastado ${formatMoney(item.spent, cycle.currency)} / límite ${formatMoney(item.limit, cycle.currency)}")
            LinearProgressIndicator(progress = {
                if (item.limit == 0L) { if (item.spent > 0L) 1f else 0f } else (item.spent.toFloat() / item.limit).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "${item.category}: ${formatMoney(item.spent, cycle.currency)} de ${formatMoney(item.limit, cycle.currency)}" },
                color = when { item.remaining < 0 -> MaterialTheme.colorScheme.error; item.nearLimit -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.secondary })
            Text(when {
                item.remaining < 0 -> "Superado en ${formatMoney(-item.remaining, cycle.currency)}"
                item.nearLimit -> "Aviso: ya has usado al menos el 80 % del límite. Quedan ${formatMoney(item.remaining, cycle.currency)}"
                else -> "Restante: ${formatMoney(item.remaining, cycle.currency)}"
            }, color = when {
                item.remaining < 0 -> MaterialTheme.colorScheme.error
                item.nearLimit -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
