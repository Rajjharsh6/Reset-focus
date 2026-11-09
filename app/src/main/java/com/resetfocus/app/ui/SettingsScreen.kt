package com.resetfocus.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.resetfocus.app.*
import com.resetfocus.app.data.Prefs
import com.resetfocus.app.data.SettingsStore

@Composable
private fun PermRow(title: String, why: String, granted: Boolean, button: String, onClick: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (granted) "On" else "Off", color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        }
        Muted(why, Modifier.padding(vertical = 6.dp))
        if (!granted) OutlinedButton(onClick = onClick) { Text(button) }
        extra()
    }
}

@Composable
fun PermissionsPanel(vm: MainViewModel) {
    val ctx = LocalContext.current
    val perms by vm.perms.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    PermRow(
        "Usage access",
        "Lets Reset Focus read how long each app was in front, to show screen-time charts. Only totals are stored, on this phone.",
        perms.usage, "Grant usage access",
        { ctx.openSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
    )
    Spacer(Modifier.height(12.dp))
    PermRow(
        "Accessibility service",
        "Used only to notice which app comes to the front so a pause screen can be shown. It cannot read screen content, typed text, passwords or messages, and it never leaves your phone. You can turn it off any time in Android settings.",
        perms.accessibility, "Open accessibility settings",
        { ctx.openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    ) {
        if (!perms.accessibility) {
            Muted("If the switch is greyed out after sideloading: App info > top-right menu > Allow restricted settings.", Modifier.padding(top = 8.dp))
            TextButton(onClick = { ctx.openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Open app info") }
        }
    }
    Spacer(Modifier.height(12.dp))
    PermRow(
        "Notifications",
        "Shows the countdown while a session runs and tells you when it is complete.",
        perms.notifications, "Allow notifications",
        {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.openSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        },
    )
    Spacer(Modifier.height(12.dp))
    PermRow(
        "Background activity",
        "HyperOS and other Android skins stop background apps aggressively. Exempt Reset Focus from battery limits and allow autostart so blocking keeps working.",
        perms.battery, "Open battery settings",
        { ctx.openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
    ) {
        TextButton(onClick = {
            val i = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            ctx.openSafely(i)
        }) { Text("HyperOS autostart page") }
        Muted("Also lock the app in the recents screen so it is not cleared.")
    }
}

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Welcome to Reset Focus", style = MaterialTheme.typography.headlineMedium)
            Muted("Everything stays on your phone. No account, no analytics, no uploads. Each permission below is explained before you grant it, and the app's monitoring is always visible to you.", Modifier.padding(vertical = 12.dp))
            PermissionsPanel(vm)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.setPref(SettingsStore.ONBOARDED, true) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            Muted("You can grant these later from Settings.", Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val prefsN by vm.prefs.collectAsState()
    val prefs = prefsN ?: Prefs()
    var confirmWipe by remember { mutableStateOf(false) }
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all local data?") },
        text = { Text("This removes sessions, urge logs, rules, schedules and usage history from this phone. It cannot be undone.") },
        confirmButton = { Button(onClick = { vm.wipeAll(); confirmWipe = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
    )
    ScreenList("Settings & privacy", trailing = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) {
        item { SectionTitle("Permissions") }
        item { Column { PermissionsPanel(vm) } }
        item {
            AppCard {
                SectionTitle("Goals and timers")
                Spacer(Modifier.height(8.dp))
                Muted("Daily focus goal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { m -> FilterChip(prefs.dailyGoalMin == m, { vm.setPref(SettingsStore.GOAL, m) }, label = { Text("$m min") }) }
                }
                Spacer(Modifier.height(8.dp))
                Muted("Urge pause length")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { s -> FilterChip(prefs.urgeCooldownSec == s, { vm.setPref(SettingsStore.URGE_CD, s) }, label = { Text("$s s") }) }
                }
            }
        }
        item {
            AppCard {
                SectionTitle("What this app does with your data")
                Spacer(Modifier.height(6.dp))
                Muted("Stored on this phone only: your block lists, schedules, session history, urge notes, per-day screen-time totals and counts of blocked attempts.")
                Spacer(Modifier.height(6.dp))
                Muted("Never collected: typed text, passwords, messages, screen content, browsing history. There is no keylogger and no network code in this app. Backups are disabled.")
            }
        }
        item {
            AppCard {
                SectionTitle("Honest limits")
                Spacer(Modifier.height(6.dp))
                Muted("An ordinary app cannot stop you from force-stopping it, uninstalling it or switching off its accessibility permission. Strict mode adds friction, not a lock. Blocking can also pause if the system kills the app in the background.")
            }
        }
        item { OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all local data") } }
    }
}
