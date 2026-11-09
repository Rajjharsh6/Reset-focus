package com.resetfocus.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resetfocus.app.MainViewModel
import com.resetfocus.app.data.Prefs
import com.resetfocus.app.data.SettingsStore

@Composable
fun BlockingScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("Blocking", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Apps") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Websites") })
        }
        if (tab == 0) AppsTab(vm) else WebsitesTab(vm)
    }
}

@Composable
private fun AppsTab(vm: MainViewModel) {
    val apps by vm.apps.collectAsState()
    val rules by vm.rules.collectAsState()
    val prefsN by vm.prefs.collectAsState()
    val prefs = prefsN ?: Prefs()
    var q by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(q, true) }
    val byPkg = rules.associateBy { it.packageName }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Muted("Focus: blocked while a session or schedule is active. Urge: shows a pause screen every time you open it." + if (prefs.allowlistMode) " Allow: usable in allow-list mode." else "")
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { vm.applyPreset("social") }, label = { Text("Block social apps") })
                AssistChip(onClick = { vm.applyPreset("browsers") }, label = { Text("Block browsers") })
                AssistChip(onClick = { vm.applyPreset("games") }, label = { Text("Block games") })
            }
        }
        item { OutlinedTextField(q, { q = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        items(shown, key = { it.pkg }) { a ->
            val r = byPkg[a.pkg]
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.pkg); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label, maxLines = 1)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(r?.blockInFocus == true, { vm.updateRule(a) { it.copy(blockInFocus = !it.blockInFocus) } }, label = { Text("Focus") })
                            FilterChip(r?.urgeTrigger == true, { vm.updateRule(a) { it.copy(urgeTrigger = !it.urgeTrigger) } }, label = { Text("Urge") })
                            if (prefs.allowlistMode) FilterChip(r?.allowed == true, { vm.updateRule(a) { it.copy(allowed = !it.allowed) } }, label = { Text("Allow") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsitesTab(vm: MainViewModel) {
    val domains by vm.domains.collectAsState()
    val prefsN by vm.prefs.collectAsState()
    val prefs = prefsN ?: Prefs()
    var input by rememberSaveable { mutableStateOf("") }

    ScreenList("Websites") {
        item {
            AppCard {
                SectionTitle("Website blocking")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Block listed domains while browsing")
                        Muted("Uses browser URL detection through Android accessibility and persists across reboot as long as the service remains enabled.")
                    }
                    Switch(
                        checked = prefs.websiteBlocking,
                        onCheckedChange = { vm.setPref(SettingsStore.WEBSITE_BLOCKING, it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Muted("This is the local domain list the blocker checks against. Domains stay on this phone only.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Add a domain") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.addDomain(input); input = "" },
                    enabled = input.trim().isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add domain") }
            }
        }
        if (domains.isEmpty()) {
            item { Muted("No domains added yet.") }
        } else {
            items(domains, key = { it.domain }) { domain ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(domain.domain, style = MaterialTheme.typography.titleSmall)
                            Muted("Blocked while browsing")
                        }
                        IconButton(onClick = { vm.removeDomain(domain.domain) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove domain")
                        }
                    }
                }
            }
        }
    }
}
