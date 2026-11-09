package com.resetfocus.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.resetfocus.app.app
import com.resetfocus.app.data.FocusState
import kotlinx.coroutines.*

/** Foreground service that shows the countdown notification and closes the session when time is up. */
class FocusSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val until = FocusState.activeUntilMs
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.ID_FOCUS, Notifier.sessionNotification(this, until), type)
        job?.cancel()
        job = scope.launch {
            val wait = until - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            if (FocusController.finish(app, "completed")) Notifier.sessionComplete(this@FocusSessionService)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
