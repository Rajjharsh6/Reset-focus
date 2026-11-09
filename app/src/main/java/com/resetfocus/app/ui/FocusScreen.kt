package com.resetfocus.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resetfocus.app.*
import com.resetfocus.app.data.*
import kotlinx.coroutines.delay

private const val PHRASE = "I choose to stop"

@Composable
fun ExitDialog(strict: Boolean, cooldownSec: Int, allowEmergency: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var left by remember { mutableIntStateOf(if (strict) cooldownSec else 0) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val phraseOk = !strict || text.trim().equals(PHRASE, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (strict) "End strict session?" else "End session?") },
        text = {
            Column {
                Text(if (strict) "Type \"$PHRASE\" and wait for the timer. Ending early is logged without judgement." else "You can always start again. Ending early is logged without judgement.")
                if (strict) OutlinedTextField(text, { text = it }, label = { Text(PHRASE) }, singleLine = true, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { Button(enabled = left == 0 && phraseOk, onClick = { onConfirm("manual") }) { Text(if (left > 0) "Wait ${left}s" else "End session") } },
        dismissButton = {
            Row {
                if (strict && allowEmergency) TextButton(enabled = phraseOk, onClick = { onConfirm("emergency") }) { Text("Emergency exit") }
                TextButton(onClick = onDismiss) { Text("Keep going") }
            }
        },
    )
}

@Composable
private fun DayDot(letter: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun AddScheduleDialog(onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    val ctx = LocalContext.current
    var mask by remember { mutableIntStateOf(0b0011111) }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(17 * 60) }
    fun pick(cur: Int, set: (Int) -> Unit) { TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, cur / 60, cur % 60, true).show() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New focus schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScheduleUtil.DAYS.forEachIndexed { i, d -> DayDot(d.take(1), ((mask shr i) and 1) == 1) { mask = mask xor (1 shl i) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(start) { start = it } }) { Text("From ${fmtTime(start)}") }
                    OutlinedButton(onClick = { pick(end) { end = it } }) { Text("To ${fmtTime(end)}") }
                }
                Muted("If the end time is earlier than the start, the window runs overnight.")
            }
        },
        confirmButton = { Button(enabled = mask != 0 && start != end, onClick = { onSave(Schedule(daysMask = mask, startMin = start, endMin = end)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun FocusScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val now by vm.now.collectAsState()
    val prefsN by vm.prefs.collectAsState()
    val prefs = prefsN ?: Prefs()
    val rules by vm.rules.collectAsState()
    val schedules by vm.schedules.collectAsState()
    val perms by vm.perms.collectAsState()
    val until = FocusState.activeUntilMs
    val active = now < until

    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var useCustom by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf("") }
    var strict by rememberSaveable { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf<(() -> Unit)?>(null) }

    val chosen = if (useCustom) custom.toIntOrNull() ?: 0 else minutes
    val blockedCount = rules.count { it.blockInFocus }
    val allowedCount = rules.count { it.allowed }

    if (showExit) ExitDialog(FocusState.strict, prefs.strictCooldownSec, true, { showExit = false }) { reason -> showExit = false; vm.endFocus(reason) }
    gate?.let { g -> ExitDialog(true, prefs.strictCooldownSec, false, { gate = null }) { g(); gate = null } }
    if (showAdd) AddScheduleDialog({ showAdd = false }) { vm.upsertSchedule(it); showAdd = false }

    ScreenList("Focus") {
        if (active) item {
            val left = (until - now).coerceAtLeast(0)
            val total = (until - FocusState.startMs).coerceAtLeast(1)
            AppCard {
                Text(if (FocusState.strict) "Strict session" else "Session in progress", style = MaterialTheme.typography.titleMedium)
                Text("%02d:%02d".format(left / 60000, (left / 1000) % 60), style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                LinearProgressIndicator(progress = { 1f - left.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showExit = true }, modifier = Modifier.fillMaxWidth()) { Text("End session") }
            }
        } else item {
            AppCard {
                SectionTitle("Start a session")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60).forEach { m -> FilterChip(selected = !useCustom && minutes == m, onClick = { useCustom = false; minutes = m }, label = { Text("$m min") }) }
                    FilterChip(selected = useCustom, onClick = { useCustom = true }, label = { Text("Custom") })
                }
                if (useCustom) OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes (1-720)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Strict mode"); Muted("Ending early needs a typed phrase and a wait.") }
                    Switch(strict, { strict = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Allow-list mode"); Muted("Block everything except apps marked Allowed.") }
                    Switch(prefs.allowlistMode, { vm.setPref(SettingsStore.ALLOWLIST, it) })
                }
                Spacer(Modifier.height(8.dp))
                Muted(if (prefs.allowlistMode) "$allowedCount allowed apps. Home screen, keyboard and system UI are always allowed." else "$blockedCount apps will be blocked. Choose them in the Blocking tab.")
                Spacer(Modifier.height(12.dp))
                if (!perms.accessibility) {
                    Muted("Enable the Reset Focus accessibility service first. Nothing can be blocked without it.")
                    OutlinedButton(onClick = { ctx.openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Open accessibility settings") }
                }
                Button(enabled = perms.accessibility && chosen in 1..720, onClick = { vm.startFocus(chosen, strict) }, modifier = Modifier.fillMaxWidth()) { Text("Start") }
            }
        }
        item {
            AppCard {
                SectionTitle("Strict mode wait before ending")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 30, 60, 120).forEach { s -> FilterChip(selected = prefs.strictCooldownSec == s, onClick = { vm.setPref(SettingsStore.STRICT_CD, s) }, label = { Text(if (s == 0) "None" else "${s}s") }) }
                }
                Spacer(Modifier.height(8.dp))
                Muted("Strict mode adds friction; it cannot stop you from force-stopping or uninstalling the app, or from turning off the accessibility service in Android settings.")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Schedules", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdd = true }) { Text("Add") }
            }
        }
        if (schedules.isEmpty()) item { Muted("No schedules. Add one to block your chosen apps automatically at set times.") }
        items(schedules.size) { i ->
            val s = schedules[i]
            val activeNow = ScheduleUtil.isActive(s)
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmtTime(s.startMin)} to ${fmtTime(s.endMin)}", style = MaterialTheme.typography.titleSmall)
                        Muted(ScheduleUtil.daysLabel(s.daysMask) + if (activeNow) "  ·  active now" else "")
                    }
                    Switch(s.enabled, { on -> val n = s.copy(enabled = on); if (!on && activeNow) gate = { vm.upsertSchedule(n) } else vm.upsertSchedule(n) })
                    IconButton(onClick = { if (activeNow) gate = { vm.deleteSchedule(s) } else vm.deleteSchedule(s) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
}
