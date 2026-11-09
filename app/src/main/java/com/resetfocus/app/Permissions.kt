package com.resetfocus.app

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.resetfocus.app.service.FocusAccessibilityService

data class Perms(
    val usage: Boolean = false,
    val accessibility: Boolean = false,
    val notifications: Boolean = true,
    val battery: Boolean = false,
)

object Permissions {
    @Suppress("DEPRECATION")
    fun read(c: Context): Perms {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val usage = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val cn = ComponentName(c, FocusAccessibilityService::class.java)
        val a11y = enabled.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
        val notif = NotificationManagerCompat.from(c).areNotificationsEnabled()
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        return Perms(usage, a11y, notif, pm.isIgnoringBatteryOptimizations(c.packageName))
    }
}
