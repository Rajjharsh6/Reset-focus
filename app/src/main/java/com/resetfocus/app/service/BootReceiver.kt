package com.resetfocus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.resetfocus.app.app
import com.resetfocus.app.data.FocusState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = c.app.settings.flow.first()
                if (prefs.websiteBlocking) {
                    // Android re-enables the accessibility service after reboot if the user enabled it.
                    // We keep the domain blocklist and settings in DataStore so the guard resumes with the same configuration.
                }
                if (FocusState.isSessionActive()) ContextCompat.startForegroundService(c, Intent(c, FocusSessionService::class.java))
                else FocusController.reconcile(c.app)
            } finally { pending.finish() }
        }
    }
}
