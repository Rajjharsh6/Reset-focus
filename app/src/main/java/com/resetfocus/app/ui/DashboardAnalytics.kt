package com.resetfocus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.resetfocus.app.*
import com.resetfocus.app.data.*
import java.util.Calendar

fun computeStreak(s: List<FocusSession>): Int {
    val days = s.filter { it.completed }.map { dayStartOf(it.startMs) }.toSet()
    val cal = Calendar.getInstance().apply { timeInMillis = dayStart(0) }
    if (cal.timeInMillis !in days) cal.add(Calendar.DAY_OF_YEAR, -1)
    var n = 0
    while (cal.timeInMillis in days) { n++; cal.add(Calendar.DAY_OF_YEAR, -1) }
    return n
}

@Composable
fun DashboardScreen(vm: MainViewModel, nav: NavController) {
    val usage by vm.usage.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val events by vm.events.collectAsState()
    val perms by vm.perms.collectAsState()
    val prefsN by vm.prefs.collectAsState()
    val prefs = prefsN ?: Prefs()
    val today = dayStart(0)
    val focusToday = sessions.filter { it.startMs >= today }.sumOf { it.actualMs }
    val goalMs = prefs.dailyGoalMin * 60_000L
    val appBlocks = events.count { it.kind == Kinds.APP && it.timeMs >= today }
    val webBlocks = events.count { it.kind == Kinds.WEB && it.timeMs >= today }
    val resisted = events.count { it.kind == Kinds.URGE_RESIST && it.timeMs >= today }
    val streak = remember(sessions) { computeStreak(sessions) }

    ScreenList("Today", trailing = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
    }) {
        if (!perms.accessibility || !perms.usage) item {
            AppCard {
                SectionTitle("Finish setup")
                Muted("Blocking and screen-time tracking need permissions. Each one is explained before you grant it.")
                TextButton(onClick = { nav.navigate("settings") }) { Text("Open permissions") }
            }
        }
        item {
            AppCard {
                SectionTitle("Daily focus goal")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { if (goalMs == 0L) 0f else (focusToday.toFloat() / goalMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Muted("${fmtDuration(focusToday)} of ${prefs.dailyGoalMin} min")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Screen time", if (perms.usage) fmtDuration(usage.todayMs) else "Grant access", Modifier.weight(1f))
                StatTile("Focus time", fmtDuration(focusToday), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Blocked app attempts", "$appBlocks", Modifier.weight(1f))
                StatTile("Website blocks", "$webBlocks", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Focus streak", if (streak == 1) "1 day" else "$streak days", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                StatTile("Urges paused today", "$resisted", Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                SectionTitle("Screen time, last 7 days")
                Spacer(Modifier.height(12.dp))
                if (usage.week.isEmpty()) Muted("Grant Usage Access in Settings to see this chart.")
                else BarChart(usage.week.map { it.ms / 3_600_000f }, usage.week.map { it.label })
            }
        }
        item { Button(onClick = { nav.navigate("focus") }, modifier = Modifier.fillMaxWidth()) { Text("Start a focus session") } }
    }
}

@Composable
fun AnalyticsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val usage by vm.usage.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val perms by vm.perms.collectAsState()
    val weekStart = dayStart(-6)
    val week = sessions.filter { it.startMs >= weekStart }
    val completed = week.count { it.completed }
    val focusPerDay = (-6..0).map { i ->
        val s = dayStart(i); val e = dayStart(i + 1)
        sessions.filter { it.startMs in s until e }.sumOf { it.actualMs } / 60_000f
    }
    val labels = usage.week.map { it.label }.ifEmpty { (-6..0).map { "" } }

    ScreenList("Usage") {
        if (!perms.usage) item {
            AppCard { SectionTitle("Usage Access needed"); Muted("Turn it on in Settings > Permissions to see screen-time statistics.") }
        } else {
            item { StatTile("Today", fmtDuration(usage.todayMs)) }
            item {
                AppCard {
                    SectionTitle("Daily screen time (hours)")
                    Spacer(Modifier.height(12.dp)); BarChart(usage.week.map { it.ms / 3_600_000f }, labels)
                }
            }
            item {
                AppCard {
                    SectionTitle("Most used today")
                    Spacer(Modifier.height(8.dp))
                    if (usage.topApps.isEmpty()) Muted("No usage recorded yet.")
                    val max = (usage.topApps.firstOrNull()?.second ?: 1L).toFloat()
                    usage.topApps.take(8).forEach { (pkg, ms) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, 36.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ctx.appLabel(pkg), maxLines = 1)
                                LinearProgressIndicator(progress = { ms / max }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                            Spacer(Modifier.width(12.dp)); Muted(fmtDuration(ms))
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                SectionTitle("Focus minutes, last 7 days")
                Spacer(Modifier.height(12.dp)); BarChart(focusPerDay, labels, MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Muted("Completed $completed of ${week.size} sessions this week.")
            }
        }
        item { SectionTitle("Recent sessions") }
        if (sessions.isEmpty()) item { Muted("No sessions yet.") }
        items(sessions.take(10).size) { i ->
            val s = sessions[i]
            val d = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(s.startMs))
            AppCard {
                Text(d, style = MaterialTheme.typography.titleSmall)
                Muted("${fmtDuration(s.actualMs)} of ${s.plannedMin}m  ·  ${if (s.completed) "completed" else (s.endReason ?: "in progress")}${if (s.strict) "  ·  strict" else ""}")
            }
        }
    }
}
