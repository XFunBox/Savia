package io.github.xfunbox.savia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xfunbox.savia.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal fun inputMoney(amount: Long) = "${amount / 100}.${(amount % 100).toString().padStart(2, '0')}"
internal fun money(text: String) = requireNotNull(parseMoney(text)) { "Revisa el importe: usa coma o punto y hasta dos decimales." }
private val displayDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-ES"))
internal val LocalFormBusy = compositionLocalOf { false }
internal val LocalFormSaveError = compositionLocalOf<String?> { null }

@Composable internal fun Form(title: String, dismiss: () -> Unit, submit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val busy = LocalFormBusy.current
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 460.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            FormError(error ?: LocalFormSaveError.current)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { if (!busy) try { error = null; submit() } catch (e: Exception) { error = e.message ?: "Revisa los datos." } }) { Text(if (busy) "Guardando…" else "Guardar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (!busy) dismiss() }) { Text("Cancelar") } })
}

@Composable private fun FormError(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
}

@Composable internal fun Field(label: String, value: String, update: (String) -> Unit, numeric: Boolean = false) {
    val busy = LocalFormBusy.current
    OutlinedTextField(value = value, enabled = !busy, onValueChange = { if (!busy) update(it) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text), modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DateField(label: String, value: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit, minimum: LocalDate? = null, maximum: LocalDate? = null) {
    val busy = LocalFormBusy.current
    var open by remember { mutableStateOf(false) }
    OutlinedCard(enabled = !busy, onClick = { if (!busy) open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (value == today) "Hoy · ${value.format(displayDate)}" else value.format(displayDate))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Cambiar $label")
        }
    }
    if (open && !busy) {
        // Material represents calendar days at midnight UTC, independent of local time zone.
        val picker = rememberDatePickerState(initialSelectedDateMillis = value.toEpochDay() * 86_400_000L,
            yearRange = 1900..9999, selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L))
                    return (minimum == null || day >= minimum) && (maximum == null || day <= maximum)
                }
                override fun isSelectableYear(year: Int) = year >= (minimum?.year ?: 1900) && year <= (maximum?.year ?: 9999)
            })
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(enabled = !busy && picker.selectedDateMillis != null, onClick = {
                if (!busy) picker.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                open = false
            }) { Text("Elegir fecha") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) {
            DatePicker(state = picker, title = { Text(label, Modifier.padding(start = 24.dp, top = 16.dp)) }, showModeToggle = false)
        }
    }
}

@Composable internal fun CycleForm(settings: Settings, previous: Cycle?, today: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate, LocalDate, Long) -> Unit) {
    var start by remember { mutableStateOf(previous?.end ?: today) }
    var end by remember { mutableStateOf(nextPayday(previous?.end ?: today, settings.payday)) }
    var income by remember { mutableStateOf(inputMoney(settings.salary)) }
    Form("Empieza tu periodo de ahorro", onDismiss, {
        require(start <= today) { "El primer día no puede estar en el futuro." }
        require(end > start) { "El próximo cobro debe ser posterior al primer día." }
        require(previous == null || start >= previous.end) { "El periodo no puede solaparse con el anterior." }
        onSave(start, end, money(income))
    }) {
        Field("¿Cuánto has cobrado? (${settings.currency})", income, { income = it }, true)
        DateField("Primer día", start, today, { start = it }, minimum = previous?.end, maximum = today)
        DateField("Próximo cobro", end, today, { end = it }, minimum = start.plusDays(1))
        Text("Organizaremos este ingreso hasta que vuelvas a cobrar. El día del próximo cobro empieza otro periodo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MovementForm(cycle: Cycle, today: LocalDate, previous: Movement?, onDismiss: () -> Unit, isCopy: Boolean = false, onSave: (Movement) -> Unit) {
    val busy = LocalFormBusy.current
    val currentBusy by rememberUpdatedState(busy)
    val categories = listOf("Comida", "Transporte", "Casa", "Ocio", "Salud", "Compras", "Otros")
    var amount by remember { mutableStateOf(previous?.let { inputMoney(it.amount) } ?: "") }
    var movementDate by remember { mutableStateOf(previous?.date ?: minOf(today, cycle.end.minusDays(1))) }
    var income by remember { mutableStateOf(previous?.income ?: false) }
    var category by remember { mutableStateOf(previous?.category ?: "Otros") }
    var customCategory by remember { mutableStateOf(previous != null && previous.category !in categories) }
    var note by remember { mutableStateOf(previous?.note ?: "") }
    var showNote by remember { mutableStateOf(!previous?.note.isNullOrEmpty()) }
    var options by remember { mutableStateOf(previous?.income ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !currentBusy })) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.95f).imePadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isCopy) "Repetir gasto" else if (previous == null) "¿Cuánto ${if (income) "has recibido" else "has gastado"}?" else "Editar ${if (income) "ingreso" else "gasto"}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(enabled = !busy, onClick = { if (!busy) onDismiss() }) { Icon(Icons.Default.Close, contentDescription = "Cerrar sin guardar") }
            }
            OutlinedTextField(value = amount, enabled = !busy, onValueChange = { if (!busy) amount = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Importe (${cycle.currency})") }, placeholder = { Text("0,00", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                textStyle = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { item -> FilterChip(enabled = !busy, selected = !customCategory && category == item, onClick = { if (!busy) { category = item; customCategory = false } }, label = { Text(item) }, leadingIcon = {
                    Icon(when (item) {
                        "Comida" -> Icons.Default.Restaurant
                        "Transporte" -> Icons.Default.DirectionsBus
                        "Casa" -> Icons.Default.Home
                        "Ocio" -> Icons.Default.SportsEsports
                        "Salud" -> Icons.Default.LocalHospital
                        "Compras" -> Icons.Default.ShoppingBag
                        else -> Icons.Default.MoreHoriz
                    }, contentDescription = null, modifier = Modifier.size(18.dp))
                }, modifier = Modifier.heightIn(min = 48.dp)) }
                FilterChip(enabled = !busy, selected = customCategory, onClick = { if (!busy) { if (!customCategory) category = ""; customCategory = true } }, label = { Text("Personalizada") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            if (customCategory) Field("Nombre de la categoría", category, { category = it })
            DateField("Fecha", movementDate, today, { movementDate = it }, minimum = cycle.start, maximum = minOf(today, cycle.end.minusDays(1)))
            TextButton(enabled = !busy, onClick = { if (!busy) showNote = !showNote }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (showNote) "Ocultar nota" else "Añadir una nota") }
            if (showNote) OutlinedTextField(enabled = !busy, value = note, onValueChange = { if (!busy) note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            Accordion("Más opciones", if (income) "Ingreso extra" else "", options, { if (!busy) options = !options }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = income, enabled = !busy, role = Role.Switch, onValueChange = { if (!busy) income = it }), verticalAlignment = Alignment.CenterVertically) {
                    Text("Es un ingreso extra", Modifier.weight(1f))
                    Switch(checked = income, onCheckedChange = null, enabled = !busy)
                }
            }
            }
            FormError(error ?: LocalFormSaveError.current)
            Button(enabled = !busy, onClick = {
                if (!busy) try {
                    error = null
                    require(movementDate >= cycle.start && movementDate < cycle.end) { "La fecha debe pertenecer al periodo." }
                    require(movementDate <= today) { "No puedes registrar movimientos futuros." }
                    require(category.isNotBlank()) { "Escribe una categoría." }
                    onSave(Movement(previous?.id ?: UUID.randomUUID().toString(), cycle.id, movementDate, money(amount), income, category.trim(), note.trim(), previous?.billId))
                } catch (e: Exception) { error = e.message ?: "Revisa los datos." }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (busy) "Guardando…" else if (income) "Guardar ingreso" else "Guardar gasto") }
        }
        LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    }
}

@Composable internal fun MoneyForm(title: String, initial: Long, currency: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amount by remember { mutableStateOf(inputMoney(initial)) }
    Form(title, onDismiss, { onSave(money(amount)) }) { Field("Importe ($currency)", amount, { amount = it }, true) }
}

@Composable private fun Accordion(title: String, summary: String, expanded: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val busy = LocalFormBusy.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = !busy, onClick = { if (!busy) toggle() }, role = Role.Button).semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun SettingsScreen(settings: Settings, busy: Boolean, onSave: (Settings) -> Unit, onExport: () -> Unit, onImport: () -> Unit, initialSection: String? = null) {
    var salary by remember(settings) { mutableStateOf(inputMoney(settings.salary)) }
    var payday by remember(settings) { mutableStateOf(settings.payday.toString()) }
    var variable by remember(settings) { mutableStateOf(inputMoney(settings.variableBudget)) }
    var target by remember(settings) { mutableStateOf(inputMoney(settings.manualTarget)) }
    var automatic by remember(settings) { mutableStateOf(settings.automaticTarget) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var bills by remember(settings) { mutableStateOf(settings.fixedExpenses) }
    var limits by remember(settings) { mutableStateOf(settings.categoryLimits) }
    var editingLimit by remember { mutableStateOf<String?>(null) }
    var limitEditor by remember { mutableStateOf(false) }
    var guideStep by remember { mutableIntStateOf(if (settings.salary == 0L) 0 else -1) }
    val guideSections = listOf("income", "bills", "daily", "target", "review")
    var section by remember { mutableStateOf<String?>(initialSection ?: if (settings.salary == 0L) "income" else null) }
    LaunchedEffect(initialSection) { if (initialSection != null) section = initialSection }
    var billEditor by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<FixedExpense?>(null) }
    var removeBill by remember { mutableStateOf<FixedExpense?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (guideStep >= 0) {
        Title("Guía del presupuesto · ${guideStep + 1} de 5")
        Text("Disponible: lo que puedes gastar tras pagos y reserva. Reservado: lo que planeas apartar. Ahorrado: lo que confirmas en Objetivos.")
        Text("Ejemplo ilustrativo: ingreso 1500, fijos 600 y diarios 500 dejan 400 para reservar. Este ejemplo no se guarda como datos tuyos.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy && guideStep > 0, onClick = { guideStep--; section = guideSections[guideStep] }) { Text("Atrás") }
            if (guideStep < 4) Button(enabled = !busy, onClick = { guideStep++; section = guideSections[guideStep] }) { Text("Continuar") }
            TextButton(enabled = !busy, onClick = { guideStep = -1; section = null }) { Text("Omitir guía") }
        }
        if (guideStep == 4) Text("Revisa el reparto de abajo antes de guardar. Cambiará solo el presupuesto de próximos periodos; los actuales conservan sus cifras.")
    } else OutlinedButton(enabled = !busy, onClick = { guideStep = 0; section = "income" }) { Text("Abrir guía paso a paso") }
    Text("Organiza tu próximo periodo: ingresos, gastos y ahorro. Los periodos anteriores conservan sus importes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Accordion("1. Ingresos y cobro", "Ingreso habitual, día de cobro y moneda", section == "income", { if (!busy) section = if (section == "income") null else "income" }) {
        Field("Ingreso neto habitual ($currency)", salary, { if (!busy) salary = it }, true)
        Field("¿Qué día del mes cobras? (1–31)", payday, { if (!busy) payday = it }, true)
        Text("Si ese mes no tiene ese día, usaremos el último día del mes.", style = MaterialTheme.typography.bodySmall)
        Text("Moneda", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EUR", "USD", "GBP", "MXN").forEach { code -> FilterChip(selected = currency == code, enabled = !busy, onClick = { if (!busy) currency = code }, label = { Text(code) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
    }
    Accordion("2. Gastos fijos", if (bills.isEmpty()) "Alquiler, luz, suscripciones…" else "${bills.size} gastos · ${formatMoney(fixedBudgetPerPeriod(bills), currency)}", section == "bills", { if (!busy) section = if (section == "bills") null else "bills" }) {
        if (bills.isEmpty()) Text("Añade los pagos que se repiten cada periodo.", style = MaterialTheme.typography.bodyMedium)
        bills.forEach { bill ->
            ListItem(headlineContent = { Text(bill.name) }, supportingContent = { Text("${formatMoney(bill.amount, currency)} · ${billFrequency(bill)}") }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Editar") },
                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button, onClick = { if (!busy) { editingBill = bill; billEditor = true } }))
        }
        OutlinedButton(enabled = !busy, onClick = { if (!busy) { editingBill = null; billEditor = true } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Añadir gasto fijo") }
    }
    Accordion("3. Gastos del día a día", "Comida, transporte y ocio", section == "daily", { if (!busy) section = if (section == "daily") null else "daily" }) {
        Field("¿Cuánto necesitas por periodo? ($currency)", variable, { if (!busy) variable = it }, true)
        Text("Es tu presupuesto para gastos que cambian. Al registrar una compra verás cuánto te queda; no la añadas también como gasto fijo.", style = MaterialTheme.typography.bodySmall)
        Text("Límites opcionales por categoría", style = MaterialTheme.typography.titleSmall)
        Text("Reparten este presupuesto y no descuentan dinero adicional. Se aplican a los próximos periodos; puedes registrar gastos aunque los superes.", style = MaterialTheme.typography.bodySmall)
        limits.forEach { (category, limit) ->
            ListItem(headlineContent = { Text(category) }, supportingContent = { Text(formatMoney(limit, currency)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Editar límite") },
                modifier = Modifier.clickable(enabled = !busy) { editingLimit = category; limitEditor = true })
        }
        OutlinedButton(enabled = !busy, onClick = { editingLimit = null; limitEditor = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Añadir límite de categoría") }
        val totalLimits = limits.values.sum()
        parseMoney(variable)?.let { budget -> if (totalLimits > budget) Text("Los límites suman ${formatMoney(totalLimits, currency)} y superan el presupuesto diario en ${formatMoney(totalLimits - budget, currency)}.", color = MaterialTheme.colorScheme.error) }
    }
    Accordion("4. Ahorro por periodo", if (automatic) "Calculada con tu presupuesto" else "Elegida por ti", section == "target", { if (!busy) section = if (section == "target") null else "target" }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = automatic, enabled = !busy, role = Role.Switch, onValueChange = { if (!busy) automatic = it }), verticalAlignment = Alignment.CenterVertically) {
            Text("Reservar lo que queda tras los gastos", Modifier.weight(1f))
            Switch(checked = automatic, onCheckedChange = null, enabled = !busy)
        }
        if (!automatic) Field("¿Cuánto quieres reservar por periodo? ($currency)", target, { if (!busy) target = it }, true)
        Text(if (automatic) "Calculamos tu reserva con lo que queda después de los gastos previstos." else "Esta reserva se descuenta del disponible para gastar. Puedes repartirla entre tus objetivos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val parsedSalary = parseMoney(salary)
    val parsedVariable = parseMoney(variable)
    val parsedTarget = if (automatic) 0L else parseMoney(target)
    if (parsedSalary != null && parsedVariable != null && parsedTarget != null) {
        val capacity = parsedSalary - fixedBudgetPerPeriod(bills) - parsedVariable
        val reserve = if (automatic) capacity.coerceAtLeast(0) else parsedTarget
        BudgetBreakdown(parsedSalary, fixedBudgetPerPeriod(bills), parsedVariable, reserve, currency)
        if (bills.any { it.dueMonth != null }) Text("Los gastos anuales se muestran aquí como media mensual orientativa. El periodo reservará el pago completo solo cuando venza.", style = MaterialTheme.typography.bodySmall)
        if (capacity < 0) Text("Los gastos previstos superan tus ingresos en ${formatMoney(-capacity, currency)}.", color = MaterialTheme.colorScheme.error)
        else if (reserve > capacity) Text("Quieres reservar ${formatMoney(reserve - capacity, currency)} más de lo que queda tras tus gastos.", color = MaterialTheme.colorScheme.error)
    }
    Accordion("Copias de seguridad", "Guardar o recuperar tus datos", section == "backup", { if (!busy) section = if (section == "backup") null else "backup" }) {
        Text("Las copias no están cifradas: guárdalas en un lugar seguro. Importar reemplaza todos los datos.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(enabled = !busy, onClick = { if (!busy) onExport() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Guardar una copia") }
        OutlinedButton(enabled = !busy, onClick = { if (!busy) onImport() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Recuperar una copia") }
        ReminderSettings { }
    }
    FormError(error ?: LocalFormSaveError.current)
    Button(enabled = !busy && (guideStep < 0 || guideStep == 4), onClick = {
        if (!busy) try {
            error = null
            val day = payday.toIntOrNull()
            require(day != null && day in 1..31) { "El día de cobro debe estar entre 1 y 31." }
            val next = Settings(money(salary), day, money(variable), money(target), automatic, currency, bills, categoryLimits = limits)
            validateData(AppData(settings = next))
            onSave(next)
            error = null
        } catch (e: Exception) { error = e.message ?: "Revisa los ajustes." }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (busy) "Guardando…" else "Guardar ajustes") }
    if (billEditor) FixedForm(editingBill, currency, { if (!busy) billEditor = false }, onRemove = editingBill?.let { bill -> { if (!busy) { billEditor = false; removeBill = bill } } }) { bill ->
        if (!busy) {
            require(bills.none { it.id != bill.id && it.name.equals(bill.name, ignoreCase = true) }) { "Ya existe un fijo con ese nombre." }
            bills = bills.filterNot { it.id == bill.id } + bill
            billEditor = false
        }
    }
    if (limitEditor) CategoryLimitForm(editingLimit, limits, currency, { limitEditor = false }, onRemove = {
        limits = limits - requireNotNull(editingLimit); limitEditor = false
    }) { category, amount ->
        val normalized = category.trim().lowercase(Locale.ROOT)
        require(limits.keys.none { it != editingLimit && it.trim().lowercase(Locale.ROOT) == normalized }) { "Ya existe un límite para esa categoría." }
        limits = (limits - listOfNotNull(editingLimit).toSet()) + (normalized to amount)
        limitEditor = false
    }
    removeBill?.let { bill -> AlertDialog(onDismissRequest = { if (!busy) removeBill = null }, title = { Text("¿Quitar ${bill.name}?") }, text = { Text("Se quitará al guardar los ajustes. Los periodos anteriores conservan este gasto.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { if (!busy) { bills = bills.filterNot { it.id == bill.id }; removeBill = null } }) { Text("Quitar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (!busy) removeBill = null }) { Text("Cancelar") } }) }
}

@Composable private fun FixedForm(previous: FixedExpense?, currency: String, onDismiss: () -> Unit, onRemove: (() -> Unit)?, onSave: (FixedExpense) -> Unit) {
    val busy = LocalFormBusy.current
    var name by remember { mutableStateOf(previous?.name ?: "") }
    var amount by remember { mutableStateOf(previous?.let { inputMoney(it.amount) } ?: "") }
    var frequency by remember { mutableStateOf(if (previous?.dueMonth != null) "Anual" else if (previous?.dueDay != null) "Mensual" else "Por periodo") }
    var dueDay by remember { mutableStateOf(previous?.dueDay?.toString() ?: "1") }
    var dueMonth by remember { mutableStateOf(previous?.dueMonth?.toString() ?: "1") }
    Form(if (previous == null) "Nuevo gasto fijo" else "Editar gasto fijo", onDismiss, {
        require(name.isNotBlank()) { "Escribe el nombre del gasto fijo." }
        val day = if (frequency == "Por periodo") null else requireNotNull(dueDay.toIntOrNull()?.takeIf { it in 1..31 }) { "El día debe estar entre 1 y 31." }
        val month = if (frequency != "Anual") null else requireNotNull(dueMonth.toIntOrNull()?.takeIf { it in 1..12 }) { "El mes debe estar entre 1 y 12." }
        onSave(FixedExpense(previous?.id ?: UUID.randomUUID().toString(), name.trim(), money(amount), dueDay = day, dueMonth = month))
    }) {
        Field("Nombre (alquiler, luz…)", name, { name = it })
        Field("Importe del pago completo ($currency)", amount, { amount = it }, true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Por periodo", "Mensual", "Anual").forEach { choice -> FilterChip(selected = frequency == choice, enabled = !busy, onClick = { frequency = choice }, label = { Text(choice) }) }
        }
        if (frequency != "Por periodo") {
            Field("Día del vencimiento (1–31)", dueDay, { dueDay = it }, true)
            if (frequency == "Anual") Field("Mes del vencimiento (1–12)", dueMonth, { dueMonth = it }, true)
            Text("Si el mes no tiene ese día, se usa su último día. El aviso no marca el pago como realizado.", style = MaterialTheme.typography.bodySmall)
        } else Text("Una vez por periodo, sin fecha de vencimiento ni aviso.", style = MaterialTheme.typography.bodySmall)
        if (onRemove != null) TextButton(enabled = !busy, onClick = { if (!busy) onRemove() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quitar este gasto", color = MaterialTheme.colorScheme.error) }
    }
}

private fun billFrequency(bill: FixedExpense): String = when {
    bill.dueMonth != null -> "anual · día ${bill.dueDay}, mes ${bill.dueMonth}"
    bill.dueDay != null -> "mensual · día ${bill.dueDay}"
    else -> "por periodo"
}

@Composable private fun CategoryLimitForm(previous: String?, limits: Map<String, Long>, currency: String, onDismiss: () -> Unit,
                                         onRemove: () -> Unit, onSave: (String, Long) -> Unit) {
    val busy = LocalFormBusy.current
    var category by remember { mutableStateOf(previous ?: "Comida") }
    var amount by remember { mutableStateOf(previous?.let { inputMoney(limits.getValue(it)) } ?: "") }
    Form(if (previous == null) "Nuevo límite de categoría" else "Editar límite", onDismiss, {
        require(category.trim().length in 1..100) { "Escribe una categoría de hasta 100 caracteres." }
        onSave(category, money(amount))
    }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Comida", "Transporte", "Casa", "Ocio", "Salud", "Compras", "Otros").forEach { choice ->
                FilterChip(enabled = !busy, selected = category.equals(choice, true), onClick = { category = choice }, label = { Text(choice) })
            }
        }
        Field("Categoría (puedes escribir una personalizada)", category, { category = it })
        Field("Límite por periodo ($currency)", amount, { amount = it }, true)
        if (previous != null) TextButton(enabled = !busy, onClick = onRemove) { Text("Quitar límite", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable internal fun PaymentForm(cycle: Cycle, bill: FixedExpense, today: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    var date by remember { mutableStateOf(minOf(today, cycle.end.minusDays(1)).coerceAtLeast(cycle.start)) }
    Form("Confirmar pago de ${bill.name}", onDismiss, {
        require(date >= cycle.start && date < cycle.end && date <= today) { "La fecha debe estar dentro del periodo y no ser futura." }
        onSave(date)
    }) {
        Text("Importe: ${formatMoney(bill.amount, cycle.currency)}. Confirma solo si ya lo has pagado; Savia no realiza transferencias.")
        Text("Periodo: ${cycle.start.format(displayDate)} – ${cycle.end.minusDays(1).format(displayDate)}", style = MaterialTheme.typography.bodySmall)
        DateField("Fecha real del pago", date, today, { date = it }, minimum = cycle.start, maximum = minOf(today, cycle.end.minusDays(1)))
    }
}
