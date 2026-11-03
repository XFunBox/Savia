package io.github.xfunbox.savia.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xfunbox.savia.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SaviaApp() {
    val context = LocalContext.current
    val store = remember { AppStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<AppData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
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
                if (data!!.cycles.isEmpty()) page = 4
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
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB3FF66), secondary = Color(0xFFB59AFF),
        background = Color(0xFF101014), surface = Color(0xFF19191F),
        onPrimary = Color(0xFF152000)
    )) {
        Scaffold(bottomBar = {
            if (data != null) NavigationBar {
                listOf("Inicio", "Gastos", "Plan", "Resumen", "Ajustes").forEachIndexed { index, title ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Text(listOf("⌂", "€", "◎", "≡", "⚙")[index]) }, label = { Text(title) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Text("Ahorro", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
                if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { error = null }) { Text("Ocultar aviso") }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                val current = data
                if (current == null) {
                    if (!loading) {
                        Text("No se han cargado los datos. El archivo existente se conserva.")
                        Button(onClick = { load() }, enabled = !busy) { Text("Reintentar carga") }
                        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Text("Importar respaldo") }
                    }
                } else {
                    Box(Modifier.weight(1f)) {
                        Screens(current, page, today, busy, ::save,
                            onSettings = { page = 4 },
                            onExport = { export.launch("ahorro-${LocalDate.now()}.json") },
                            onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                    }
                }
            }
        }
        if (pendingImport != null) AlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            title = { Text("¿Reemplazar todos los datos?") },
            text = { Text("Esta copia sustituirá ajustes, ciclos y movimientos actuales. El archivo contiene datos financieros y no está cifrado.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val text = pendingImport ?: return@TextButton
                busy = true
                scope.launch {
                    try {
                        val imported = withContext(Dispatchers.IO) { store.import(text) }
                        data = imported
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
