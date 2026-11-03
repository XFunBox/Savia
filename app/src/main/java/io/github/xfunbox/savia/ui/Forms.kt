package io.github.xfunbox.savia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.*
import java.time.LocalDate
import java.util.UUID

private fun inputMoney(amount: Long) = "${amount / 100}.${(amount % 100).toString().padStart(2, '0')}"
private fun money(text: String) = requireNotNull(parseMoney(text)) { "Importe inválido: usa como máximo dos decimales y ningún separador de miles." }
private fun date(text: String): LocalDate = try { LocalDate.parse(text.trim()) } catch (_: Exception) { throw IllegalArgumentException("Fecha inválida. Usa AAAA-MM-DD.") }

@Composable private fun Form(title: String, dismiss: () -> Unit, submit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 460.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { try { submit() } catch (e: Exception) { error = e.message ?: "Revisa los datos." } }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable internal fun Field(label: String, value: String, update: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = update, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text), modifier = Modifier.fillMaxWidth())
}

@Composable internal fun CycleForm(settings: Settings, previous: Cycle?, today: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate, LocalDate, Long) -> Unit) {
    var start by remember { mutableStateOf((previous?.end ?: today).toString()) }
    var end by remember { mutableStateOf(nextPayday(previous?.end ?: today, settings.payday).toString()) }
    var income by remember { mutableStateOf(inputMoney(settings.salary)) }
    Form("Abrir ciclo", onDismiss, {
        val from = date(start)
        val until = date(end)
        require(!from.isAfter(today)) { "El inicio no puede estar en el futuro." }
        require(until.isAfter(from)) { "El siguiente cobro debe ser posterior al inicio." }
        require(previous == null || !from.isBefore(previous.end)) { "El ciclo no puede solaparse con el anterior." }
        onSave(from, until, money(income))
    }) {
        Text("Confirma el ingreso recibido y las fechas. El día del siguiente cobro pertenece al próximo ciclo.")
        Field("Inicio (AAAA-MM-DD)", start, { start = it })
        Field("Siguiente cobro (AAAA-MM-DD)", end, { end = it })
        Field("Ingreso (${settings.currency})", income, { income = it }, true)
        Text("Se conservará una copia de tus fijos, presupuesto, moneda y meta.")
    }
}

@Composable internal fun MovementForm(cycle: Cycle, today: LocalDate, previous: Movement?, onDismiss: () -> Unit, onSave: (Movement) -> Unit) {
    var amount by remember { mutableStateOf(previous?.let { inputMoney(it.amount) } ?: "") }
    var movementDate by remember { mutableStateOf((previous?.date ?: minOf(today, cycle.end.minusDays(1))).toString()) }
    var income by remember { mutableStateOf(previous?.income ?: false) }
    var category by remember { mutableStateOf(previous?.category ?: "Otros") }
    var note by remember { mutableStateOf(previous?.note ?: "") }
    Form(if (previous == null) "Añadir movimiento" else "Editar movimiento", onDismiss, {
        val day = date(movementDate)
        require(!day.isBefore(cycle.start) && day.isBefore(cycle.end)) { "La fecha debe pertenecer al ciclo." }
        require(!day.isAfter(today)) { "No puedes registrar movimientos futuros." }
        require(category.isNotBlank()) { "Escribe una categoría." }
        onSave(Movement(previous?.id ?: UUID.randomUUID().toString(), cycle.id, day, money(amount), income, category.trim(), note.trim()))
    }) {
        Row(Modifier.fillMaxWidth().toggleable(value = income, role = Role.Checkbox, onValueChange = { income = it }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = income, onCheckedChange = null)
            Text("Ingreso extra", modifier = Modifier.padding(12.dp))
        }
        Field("Importe (${cycle.currency})", amount, { amount = it }, true)
        Field("Fecha (AAAA-MM-DD)", movementDate, { movementDate = it })
        Field("Categoría", category, { category = it })
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable internal fun MoneyForm(title: String, initial: Long, currency: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amount by remember { mutableStateOf(inputMoney(initial)) }
    Form(title, onDismiss, { onSave(money(amount)) }) { Field("Importe ($currency)", amount, { amount = it }, true) }
}

@Composable internal fun SettingsScreen(settings: Settings, busy: Boolean, onSave: (Settings) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var salary by remember(settings) { mutableStateOf(inputMoney(settings.salary)) }
    var payday by remember(settings) { mutableStateOf(settings.payday.toString()) }
    var variable by remember(settings) { mutableStateOf(inputMoney(settings.variableBudget)) }
    var target by remember(settings) { mutableStateOf(inputMoney(settings.manualTarget)) }
    var automatic by remember(settings) { mutableStateOf(settings.automaticTarget) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var bills by remember(settings) { mutableStateOf(settings.fixedExpenses) }
    var billEditor by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<FixedExpense?>(null) }
    var removeBill by remember { mutableStateOf<FixedExpense?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Title("Ajustes")
    Text("Guarda los ajustes antes de abrir un ciclo. Los cambios se aplican a ciclos futuros y conservan el historial.")
    Field("Salario neto ($currency)", salary, { salary = it }, true)
    Field("Día habitual de cobro (1–31)", payday, { payday = it }, true)
    Field("Presupuesto variable ($currency)", variable, { variable = it }, true)
    Text("Moneda")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("EUR", "USD", "GBP", "MXN").forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) }
    }
    Row(Modifier.fillMaxWidth().toggleable(value = automatic, enabled = !busy, role = Role.Switch, onValueChange = { automatic = it }), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = automatic, onCheckedChange = null, enabled = !busy)
        Text("Meta automática", modifier = Modifier.padding(12.dp))
    }
    if (!automatic) Field("Meta manual ($currency)", target, { target = it }, true)
    Text("La meta automática reserva el ingreso menos fijos y presupuesto variable, con un mínimo de cero.")
    Title("Gastos fijos")
    bills.forEach { bill ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("${bill.name} · ${formatMoney(bill.amount, currency)}")
            Row {
                TextButton(enabled = !busy, onClick = { editingBill = bill; billEditor = true }) { Text("Editar") }
                TextButton(enabled = !busy, onClick = { removeBill = bill }) { Text("Quitar") }
            }
        } }
    }
    OutlinedButton(enabled = !busy, onClick = { editingBill = null; billEditor = true }) { Text("Añadir gasto fijo") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(enabled = !busy, onClick = {
        try {
            val day = payday.toIntOrNull()
            require(day != null && day in 1..31) { "El día de cobro debe estar entre 1 y 31." }
            val next = Settings(money(salary), day, money(variable), money(target), automatic, currency, bills)
            validateData(AppData(settings = next))
            onSave(next)
            error = null
        } catch (e: Exception) { error = e.message ?: "Revisa los ajustes." }
    }) { Text("Guardar ajustes") }
    HorizontalDivider()
    Title("Copias de seguridad")
    Text("Las copias JSON contienen datos financieros sin cifrar. Guárdalas en un lugar de confianza. Importar reemplaza todos los datos guardados.")
    OutlinedButton(enabled = !busy, onClick = onExport) { Text("Exportar copia JSON") }
    OutlinedButton(enabled = !busy, onClick = onImport) { Text("Importar copia JSON") }
    if (billEditor) FixedForm(editingBill, currency, { billEditor = false }) { bill ->
        require(bills.none { it.id != bill.id && it.name.equals(bill.name, ignoreCase = true) }) { "Ya existe un fijo con ese nombre." }
        bills = bills.filterNot { it.id == bill.id } + bill
        billEditor = false
    }
    removeBill?.let { bill -> AlertDialog(onDismissRequest = { removeBill = null }, title = { Text("¿Quitar ${bill.name}?") }, text = { Text("Se quitará de estos ajustes al guardarlos. Los ciclos existentes conservan sus fijos.") },
        confirmButton = { TextButton(onClick = { bills = bills.filterNot { it.id == bill.id }; removeBill = null }) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = { removeBill = null }) { Text("Cancelar") } }) }
}

@Composable private fun FixedForm(previous: FixedExpense?, currency: String, onDismiss: () -> Unit, onSave: (FixedExpense) -> Unit) {
    var name by remember { mutableStateOf(previous?.name ?: "") }
    var amount by remember { mutableStateOf(previous?.let { inputMoney(it.amount) } ?: "") }
    Form("Gasto fijo", onDismiss, {
        require(name.isNotBlank()) { "Escribe el nombre del gasto fijo." }
        onSave(FixedExpense(previous?.id ?: UUID.randomUUID().toString(), name.trim(), money(amount)))
    }) {
        Field("Nombre", name, { name = it })
        Field("Importe ($currency)", amount, { amount = it }, true)
    }
}
