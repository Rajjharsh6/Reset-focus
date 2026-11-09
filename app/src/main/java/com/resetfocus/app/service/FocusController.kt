package com.resetfocus.app.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.resetfocus.app.ResetFocusApp
import com.resetfocus.app.data.FocusSession
import com.resetfocus.app.data.FocusState

object FocusController {
    suspend fun start(app: ResetFocusApp, minutes: Int, strict: Boolean) {
        val now = System.currentTimeMillis()
        val id = app.db.dao().insertSession(FocusSession(startMs = now, plannedMin = minutes, strict = strict))
        FocusState.set(id, now, now + minutes * 60_000L, strict)
        ContextCompat.startForegroundService(app, Intent(app, FocusSessionService::class.java))
    }

    /** Closes the active session record. Returns true if a session was actually closed. */
    suspend fun finish(app: ResetFocusApp, reason: String): Boolean {
        val id = FocusState.sessionId
        if (id == 0L) return false
        val dao = app.db.dao()
        val s = dao.session(id)
        if (s != null && s.endReason == null) {
            val actual = minOf(System.currentTimeMillis(), FocusState.activeUntilMs) - s.startMs
            dao.updateSession(s.copy(actualMs = actual.coerceAtLeast(0), completed = reason == "completed", endReason = reason))
        }
        FocusState.clear()
        return true
    }

    suspend fun end(app: ResetFocusApp, reason: String) {
        finish(app, reason)
        app.stopService(Intent(app, FocusSessionService::class.java))
    }

    /** Called on app open and after boot: closes sessions whose time has passed while no service was running. */
    suspend fun reconcile(app: ResetFocusApp) {
        if (FocusState.sessionId != 0L && !FocusState.isSessionActive()) finish(app, "completed")
    }
}
