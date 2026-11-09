package com.resetfocus.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.resetfocus.app.MainActivity
import com.resetfocus.app.R

object Notifier {
    const val CH_FOCUS = "focus"
    const val CH_INFO = "info"
    const val ID_FOCUS = 1
    const val ID_DONE = 2

    fun init(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "Focus sessions", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Session results", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(c: Context) = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun sessionNotification(c: Context, untilMs: Long): Notification =
        NotificationCompat.Builder(c, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("Focus session in progress")
            .setContentText("Distracting apps are paused")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(untilMs)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(c))
            .build()

    fun sessionComplete(c: Context) {
        val n = NotificationCompat.Builder(c, CH_INFO)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("Session complete")
            .setContentText("Nice work. You stayed with it.")
            .setAutoCancel(true)
            .setContentIntent(openApp(c))
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(ID_DONE, n) }
    }
}
