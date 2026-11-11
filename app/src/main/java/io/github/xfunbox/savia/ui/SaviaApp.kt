package io.github.xfunbox.savia.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xfunbox.savia.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaviaApp(reminderDestination: String? = null, reminderRequestId: Int = 0) {
    val context = LocalContext.current
    val store = remember { AppStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<AppData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var addRequest by remember { mutableIntStateOf(0) }
    var newMovementIsIncome by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var openBackup by remember { mutableStateOf(false) }
    LaunchedEffect(reminderDestination, reminderRequestId, data != null) {
        if (data != null && reminderDestination != null) {
            openBackup = reminderDestination == LocalReminders.DESTINATION_BACKUP
            page = if (openBackup) 4 else 0
        }
    }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = page != 0) { page = 0 }
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it); notice = null }
    }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun load() {
        if (busy) return
        busy = true
        loading = true
        error = null
        scope.launch {
            try {
                data = withContext(Dispatchers.IO) { store.load() }
                data?.let { runCatching { LocalReminders.refresh(context, it) } }
                page = 0
            } catch (e: Exception) { error = e.message ?: "No se pudieron cargar los datos." }
            finally { busy = false; loading = false }
        }
    }
    fun save(next: AppData) {
        if (busy || data == null) return
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                data = next
                runCatching { LocalReminders.refresh(context, next) }
                if (page == 4 && next.cycles.isEmpty()) page = 0
                notice = "Cambios guardados"
            } catch (e: Exception) { error = e.message ?: "No se pudieron guardar los cambios." }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && !busy && data != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val text = store.export()
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: kotlin.error("No se puede abrir el archivo de destino.")
                        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    LocalReminders.recordExport(context)
                    notice = "Copia exportada"
                } catch (e: Exception) { error = e.message ?: "Error al exportar." }
                finally { busy = false }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    pendingImport = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: kotlin.error("No se puede abrir la copia.")
                        val bytes = input.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 5 * 1024 * 1024) { "La copia supera 5 MiB." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        val text = bytes.toString(Charsets.UTF_8)
                        Backup.decode(text)
                        text
                    }
                } catch (e: Exception) { error = e.message ?: "La copia no es válida." }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    SaviaTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF10151A), Color(0xFF090B10), Color(0xFF161020))))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x527F5CFF), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * .8f),
                    radius = size.width * .8f, center = Offset(size.width, 0f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x28C6FF00), Color.Transparent), center = Offset(0f, size.height * .58f), radius = size.width * .7f),
                    radius = size.width * .7f, center = Offset(0f, size.height * .58f)
                )
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                            SaviaMark(Modifier.padding(10.dp).size(24.dp))
                        }
                        Column {
                            Text(when(page) { 1 -> "Movimientos"; 2 -> "Objetivos"; 3 -> "Resumen"; 4 -> "Mi presupuesto"; else -> "Savia" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (page == 0) Text("Tu dinero, a tu ritmo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { if (page == 4) IconButton(onClick = { page = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al inicio") } },
                actions = { if (page != 4 && data != null) IconButton(onClick = { page = 4 }) { Icon(Icons.Default.Settings, "Mi presupuesto") } }
            ) },
            floatingActionButton = {
                val latest = data?.cycles?.maxByOrNull { it.start }
                if (latest != null && !busy && (page == 0 || page == 1)) Box {
                    ExtendedFloatingActionButton(
                        modifier = Modifier.semantics { contentDescription = "Añadir movimiento" },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(50),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, focusedElevation = 6.dp, pressedElevation = 2.dp),
                        onClick = { if (!busy) addMenuExpanded = true },
                        icon = { Icon(Icons.Default.Add, null) }, text = { Text("Añadir movimiento") })
                    DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Añadir gasto") }, leadingIcon = { Icon(Icons.Default.Remove, null) },
                            onClick = {
                                addMenuExpanded = false
                                newMovementIsIncome = false
                                page = 1
                                addRequest++
                            })
                        DropdownMenuItem(text = { Text("Añadir ingreso extra") }, leadingIcon = { Icon(Icons.Default.SouthWest, null) },
                            onClick = {
                                addMenuExpanded = false
                                newMovementIsIncome = true
                                page = 1
                                addRequest++
                            })
                    }
                }
            }, bottomBar = {
            if (data != null && page != 4) Box(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .86f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                ) {
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                        listOf(0 to "Inicio", 1 to "Actividad", 2 to "Objetivos", 3 to "Resumen").forEach { (index, title) ->
                            NavigationBarItem(selected = page == index, onClick = { page = index },
                                icon = { Icon(when(index) { 1 -> Icons.AutoMirrored.Filled.ReceiptLong; 2 -> Icons.Default.Flag; 3 -> Icons.Default.PieChart; else -> Icons.Default.Home }, null) },
                                label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ))
                        }
                    }
                }
            }

        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { error = null }) { Text("Ocultar aviso") }
                }
                val current = data
                if (current == null) {
                    if (!loading) {
                        Text("No se han cargado los datos. El archivo existente se conserva.")
                        Button(onClick = { load() }, enabled = !busy) { Text("Reintentar carga") }
                        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Text("Importar respaldo") }
                    }
                } else {
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalFormBusy provides busy, LocalFormSaveError provides error) {
                        Screens(current, page, today, busy, ::save,
                            onSettings = { page = 4 },
                            onExport = { export.launch("ahorro-${LocalDate.now()}.json") },
                            onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            addRequest = addRequest, addIncome = newMovementIsIncome, onPlan = { page = 2 }, openBackup = openBackup)
                        }
                    }
                }
            }
            }
        }
        if (pendingImport != null) AlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            title = { Text("¿Reemplazar todos los datos?") },
            text = { Text("Esta copia sustituirá presupuesto, objetivos, periodos y movimientos actuales. El archivo contiene datos financieros y no está cifrado.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val text = pendingImport ?: return@TextButton
                busy = true
                scope.launch {
                    try {
                        val imported = withContext(Dispatchers.IO) { store.import(text) }
                        data = imported
                        runCatching { LocalReminders.refresh(context, imported) }
                        pendingImport = null
                        error = null
                        page = 0
                        notice = "Copia importada"
                    } catch (e: Exception) { error = e.message ?: "No se pudo importar la copia."; pendingImport = null }
                    finally { busy = false }
                }
            }) { Text("Reemplazar datos") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("Cancelar") } }
        )
    }
}
