package io.github.xfunbox.savia.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xfunbox.savia.LocalReminders
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ReminderSettings(onChanged: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var prefs by remember { mutableStateOf(LocalReminders.preferences(context)) }
    var allowed by remember { mutableStateOf(LocalReminders.notificationsAllowed(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = LocalReminders.notificationsAllowed(context)
        onChanged()
    }
    DisposableEffect(context, lifecycle) {
        val storage = LocalReminders.storage(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefs = LocalReminders.preferences(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = LocalReminders.notificationsAllowed(context)
        }
        storage.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            storage.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !LocalReminders.notificationsAllowed(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recordatorios de este dispositivo", style = MaterialTheme.typography.titleMedium)
        Text("Última exportación completada: ${prefs.lastExportAt?.let(::reminderDate) ?: "ninguna en este dispositivo"}.")
        Text("Esta fecha no confirma que el archivo siga disponible. Importar una copia no actualiza la fecha.", style = MaterialTheme.typography.bodySmall)
        ReminderSwitch("Avisarme de pagos pendientes y de los próximos 3 días", prefs.billsEnabled) { enabled ->
            LocalReminders.updatePreferences(context, billsEnabled = enabled)
            if (enabled) requestPermission()
            onChanged()
        }
        ReminderSwitch("Recordarme guardar una copia", prefs.backupsEnabled) { enabled ->
            LocalReminders.updatePreferences(context, backupsEnabled = enabled)
            if (enabled) requestPermission()
            onChanged()
        }
        if (prefs.backupsEnabled) {
            Text("Frecuencia desde la última exportación; si aún no hay ninguna, desde la activación.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30).forEach { days ->
                    OutlinedButton(onClick = {
                        LocalReminders.updatePreferences(context, backupIntervalDays = days)
                        onChanged()
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (prefs.backupIntervalDays == days) "✓ $days días" else "$days días")
                    }
                }
            }
            OutlinedButton(onClick = {
                LocalReminders.snoozeBackup(context)
                onChanged()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Posponer el aviso de copia 1 día") }
            prefs.backupSnoozedUntil?.takeIf { it > System.currentTimeMillis() }?.let {
                Text("Aviso de copia pospuesto hasta ${reminderDate(it)}.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Las copias JSON no están cifradas: guárdalas en un lugar seguro.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Los avisos se revisan una vez al día, aproximadamente desde las 9:00; Android puede retrasarlos. Avisar no registra un pago.", style = MaterialTheme.typography.bodySmall)
        if ((prefs.billsEnabled || prefs.backupsEnabled) && !allowed) {
            Text("Las notificaciones están bloqueadas. La lista de próximos pagos sigue disponible.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Abrir ajustes de notificaciones") }
        }
        Text("Estas preferencias se conservan solo aquí; recuperar una copia no activa avisos ni cambia la fecha de exportación.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReminderSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun reminderDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag("es-ES")))
