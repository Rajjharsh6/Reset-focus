package com.resetfocus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resetfocus.app.*
import com.resetfocus.app.data.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun UrgeSession(total: Int, tip: String, onSave: (String, Int, String) -> Unit, onClose: () -> Unit) {
    var left by remember { mutableIntStateOf(total) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val phase = listOf("Breathe in", "Hold", "Breathe out", "Hold")[((total - left) / 4) % 4]
    AppCard {
        if (left > 0) {
            Text(phase, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("${left}s", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Muted(tip)
            TextButton(onClick = onClose) { Text("Stop") }
        } else {
            var trig by remember { mutableStateOf("") }
            var note by remember { mutableStateOf("") }
            var inten by remember { mutableFloatStateOf(5f) }
            SectionTitle(if (total > 0) "You paused. That counts." else "Log an urge")
            OutlinedTextField(trig, { trig = it }, label = { Text("What triggered it? (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("Intensity ${inten.toInt()}/10", modifier = Modifier.padding(top = 8.dp))
            Slider(inten, { inten = it }, valueRange = 1f..10f, steps = 8)
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(trig, inten.toInt(), note) }) { Text("Save to log") }
                TextButton(onClick = onClose) { Text("Skip") }
            }
        }
    }
}

@Composable
fun RecoveryScreen(vm: MainViewModel) {
    val urges by vm.urges.collectAsState()
    val events by vm.events.collectAsState()
    var secs by rememberSaveable { mutableIntStateOf(-1) }
    val tip = remember(secs) { Content.tips.random() }
    val weekStart = dayStart(-6)
    val shown = events.count { it.kind == Kinds.URGE }
    val closed = events.count { it.kind == Kinds.URGE_RESIST }
    val cont = events.count { it.kind == Kinds.URGE_CONTINUE }
    val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    ScreenList("Recovery") {
        item {
            if (secs >= 0) key(secs, tip) { UrgeSession(secs, tip, { t, i, n -> vm.logUrge(t, i, n); secs = -1 }, { secs = -1 }) }
            else AppCard {
                SectionTitle("Feeling an urge?")
                Muted("Take 30 to 60 seconds. Urges rise and fall; you only need to ride this one out.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { secs = 30 }) { Text("30 seconds") }
                    Button(onClick = { secs = 60 }) { Text("60 seconds") }
                    TextButton(onClick = { secs = 0 }) { Text("Just log") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Urges logged", "${urges.size}", Modifier.weight(1f))
                StatTile("This week", "${urges.count { it.timeMs >= weekStart }}", Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                SectionTitle("Pause screen")
                Muted("Shown $shown times in the last 30 days. You closed the app $closed times and continued $cont times. Every pause is practice.")
            }
        }
        item {
            AppCard {
                SectionTitle("Grounding techniques")
                Spacer(Modifier.height(6.dp))
                Content.tips.forEach { Muted(it, Modifier.padding(vertical = 4.dp)) }
            }
        }
        item {
            AppCard {
                SectionTitle("Things to do instead")
                Spacer(Modifier.height(6.dp))
                Content.activities.forEach { Muted("•  $it", Modifier.padding(vertical = 2.dp)) }
            }
        }
        item { SectionTitle("Recent urges") }
        if (urges.isEmpty()) item { Muted("Nothing logged yet. Logging is optional and stays on this phone.") }
        items(urges.take(8).size) { i ->
            val u = urges[i]
            AppCard {
                Text(u.triggerName, style = MaterialTheme.typography.titleSmall)
                Muted("${fmt.format(Date(u.timeMs))}  ·  intensity ${u.intensity}/10" + if (u.note.isNotBlank()) "\n${u.note}" else "")
            }
        }
    }
}
