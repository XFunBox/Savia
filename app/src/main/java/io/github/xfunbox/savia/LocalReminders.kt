package io.github.xfunbox.savia

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Device metadata deliberately stays outside JSON backups and imported financial data.
data class ReminderPreferences(
    val lastExportAt: Long? = null,
    val billsEnabled: Boolean = false,
    val backupsEnabled: Boolean = false,
    val backupIntervalDays: Int = 7,
    val backupStartedAt: Long? = null,
    val backupSnoozedUntil: Long? = null
)

data class BillReminder(val id: String, val name: String, val date: LocalDate, val paid: Boolean)

object LocalReminders {
    private const val PREFS = "local_reminders"
    private const val CHANNEL = "reminders"
    private const val BILLS_NOTIFICATION = 1001
    private const val BACKUP_NOTIFICATION = 1002
    internal const val ACTION_CHECK = "io.github.xfunbox.savia.CHECK_REMINDERS"
    const val EXTRA_DESTINATION = "io.github.xfunbox.savia.REMINDER_DESTINATION"
    const val DESTINATION_PAYMENTS = "payments"
    const val DESTINATION_BACKUP = "backup"

    fun storage(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun preferences(context: Context): ReminderPreferences = storage(context).let { prefs ->
        fun timestamp(key: String) = prefs.getLong(key, 0).takeIf { it > 0 }
        ReminderPreferences(timestamp("last_export"), prefs.getBoolean("bills", false),
            prefs.getBoolean("backups", false), prefs.getInt("backup_days", 7).takeIf { it in listOf(7, 14, 30) } ?: 7,
            timestamp("backup_started"), timestamp("backup_snoozed"))
    }

    fun updatePreferences(context: Context, billsEnabled: Boolean? = null, backupsEnabled: Boolean? = null,
                          backupIntervalDays: Int? = null) {
        require(backupIntervalDays == null || backupIntervalDays in listOf(7, 14, 30))
        val previous = preferences(context)
        storage(context).edit().apply {
            billsEnabled?.let { putBoolean("bills", it) }
            backupsEnabled?.let {
                putBoolean("backups", it)
                if (it && previous.backupStartedAt == null) putLong("backup_started", System.currentTimeMillis())
            }
            backupIntervalDays?.let { putInt("backup_days", it) }
        }.apply()
        if (billsEnabled == false) manager(context).cancel(BILLS_NOTIFICATION)
        if (backupsEnabled == false) manager(context).cancel(BACKUP_NOTIFICATION)
        schedule(context)
    }

    // Call only after the export's output stream has closed successfully.
    fun recordExport(context: Context) {
        storage(context).edit().putLong("last_export", System.currentTimeMillis()).remove("backup_snoozed").apply()
        manager(context).cancel(BACKUP_NOTIFICATION)
        schedule(context)
    }

    fun snoozeBackup(context: Context, days: Int = 1) {
        require(days in 1..30)
        storage(context).edit().putLong("backup_snoozed", Instant.now().plusSeconds(days * 86400L).toEpochMilli()).apply()
        manager(context).cancel(BACKUP_NOTIFICATION)
        schedule(context)
    }

    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager(context).areNotificationsEnabled()

    fun refresh(context: Context, data: AppData) = refresh(context, upcomingPayments(data, LocalDate.now()).map {
        BillReminder("${it.cycleId}:${it.bill.id}:${it.date}", it.bill.name, it.date, it.paid)
    })

    fun refresh(context: Context, payments: List<BillReminder>) {
        val prefs = preferences(context)
        if (!prefs.billsEnabled || dueBills(payments, LocalDate.now()).isEmpty()) manager(context).cancel(BILLS_NOTIFICATION)
        if (!backupDue(prefs, Instant.now())) manager(context).cancel(BACKUP_NOTIFICATION)
        schedule(context)
    }

    internal fun dueBills(payments: List<BillReminder>, today: LocalDate): List<BillReminder> =
        payments.filter { !it.paid && it.date <= today.plusDays(3) }

    internal fun backupDue(prefs: ReminderPreferences, now: Instant): Boolean {
        if (!prefs.backupsEnabled || (prefs.backupSnoozedUntil ?: 0) > now.toEpochMilli()) return false
        val last = prefs.lastExportAt ?: prefs.backupStartedAt ?: return false
        val zone = ZoneId.systemDefault()
        val due = Instant.ofEpochMilli(last).atZone(zone).toLocalDate().plusDays(prefs.backupIntervalDays.toLong())
        return now.atZone(zone).toLocalDate() >= due
    }

    internal fun schedule(context: Context, force: Boolean = false) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 1000, Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prefs = preferences(context)
        val storage = storage(context)
        if (!prefs.billsEnabled && !prefs.backupsEnabled) {
            alarmManager.cancel(pending)
            storage.edit().remove("scheduled_check").apply()
            return
        }
        val now = Instant.now().atZone(ZoneId.systemDefault())
        // Keep today's inexact check even after its nominal time: Android may deliver it late.
        // Reopening or saving must not move an undelivered check to tomorrow.
        if (!force && keepScheduledCheck(storage.getLong("scheduled_check", 0), now.toInstant())) return
        alarmManager.cancel(pending)
        var next = now.toLocalDate().atTime(9, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        // A single inexact daily check; rescheduling follows local time and DST.
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), 60 * 60 * 1000L, pending)
        storage.edit().putLong("scheduled_check", next.toInstant().toEpochMilli()).apply()
    }

    internal fun keepScheduledCheck(scheduledAt: Long, now: Instant): Boolean =
        scheduledAt > 0 && scheduledAt >= now.minusSeconds(36 * 3600L).toEpochMilli() &&
            scheduledAt <= now.plusSeconds(26 * 3600L).toEpochMilli()

    internal fun notifyDue(context: Context, data: AppData) {
        if (!notificationsAllowed(context)) return
        val manager = manager(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT))
        val prefs = preferences(context)
        val due = dueBills(upcomingPayments(data, LocalDate.now()).map {
            BillReminder("${it.cycleId}:${it.bill.id}:${it.date}", it.bill.name, it.date, it.paid)
        }, LocalDate.now())
        if (prefs.billsEnabled && due.isNotEmpty()) {
            post(context, BILLS_NOTIFICATION, DESTINATION_PAYMENTS, "Pagos pendientes o próximos",
                "${due.size} pago(s) hasta dentro de 3 días. Revisa las fechas y confirma solo los pagos realizados.")
        }
        if (backupDue(prefs, Instant.now())) {
            post(context, BACKUP_NOTIFICATION, DESTINATION_BACKUP, "Recuerda guardar una copia", "Exporta tus datos desde Ajustes. La copia JSON no está cifrada.")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, destination: String, title: String, text: String) {
        // Distinct request codes preserve each destination because PendingIntent identity excludes extras.
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_DESTINATION, destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openApp = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_savia_monochrome).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(openApp)
            .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
        try {
            manager(context).notify(id, notification)
        } catch (_: SecurityException) {
            // The user may revoke permission between checking it and posting.
        }
    }

    private fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The alarm was consumed, or Android invalidated it on reboot/time change.
                LocalReminders.storage(appContext).edit().remove("scheduled_check").apply()
                val data = AppStore(appContext).load()
                if (intent.action == LocalReminders.ACTION_CHECK) LocalReminders.notifyDue(appContext, data)
                LocalReminders.refresh(appContext, data)
            } catch (error: Exception) {
                // Failed reads are retried later; never replace financial data with an empty model.
                Log.w("SaviaReminders", "No se pudo revisar los recordatorios.", error)
                LocalReminders.schedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
