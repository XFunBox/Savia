package io.github.xfunbox.savia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xfunbox.savia.ui.SaviaApp

class MainActivity : ComponentActivity() {
    private var reminderDestination by mutableStateOf<String?>(null)
    private var reminderRequestId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readReminderDestination(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { SaviaApp(reminderDestination = reminderDestination, reminderRequestId = reminderRequestId) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReminderDestination(intent)
    }

    private fun readReminderDestination(intent: Intent) {
        reminderDestination = intent.getStringExtra(LocalReminders.EXTRA_DESTINATION).takeIf {
            it == LocalReminders.DESTINATION_PAYMENTS || it == LocalReminders.DESTINATION_BACKUP
        }
        reminderRequestId++
    }
}
