package com.resetfocus.app

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resetfocus.app.data.*
import com.resetfocus.app.service.FocusController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AppInfo(val pkg: String, val label: String, val isGame: Boolean)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ResetFocusApp
    private val dao = app.db.dao()
    private val usageRepo = UsageRepository(app)

    private fun <T> Flow<T>.eager(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val prefs: StateFlow<Prefs?> = app.settings.flow.map<Prefs, Prefs?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rules = dao.appRules().eager(emptyList())
    val schedules = dao.schedules().eager(emptyList())
    val sessions = dao.allSessions().eager(emptyList())
    val events = dao.eventsSince(dayStart(-30)).eager(emptyList())
    val urges = dao.urges().eager(emptyList())
    val domains = dao.domains().eager(emptyList())
    val now = flow { while (true) { emit(System.currentTimeMillis()); delay(1000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val perms = MutableStateFlow(Perms())
    val usage = MutableStateFlow(UsageUi())

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            FocusController.reconcile(app)
            val p = Permissions.read(app)
            perms.value = p
            if (p.usage) usage.value = runCatching { usageRepo.build(dao) }.getOrDefault(usage.value)
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            apps.value = pm.queryIntentActivities(q, 0)
                .map { ri ->
                    val ai = ri.activityInfo.applicationInfo
                    AppInfo(ai.packageName, ri.loadLabel(pm).toString(), ai.category == ApplicationInfo.CATEGORY_GAME)
                }
                .filter { it.pkg != app.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    fun updateRule(a: AppInfo, f: (AppRule) -> AppRule) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = rules.value.find { it.packageName == a.pkg } ?: AppRule(a.pkg, a.label)
            val n = f(cur)
            if (!n.blockInFocus && !n.urgeTrigger && !n.allowed) dao.removeRule(a.pkg) else dao.upsertRule(n)
        }
    }

    fun applyPreset(kind: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = apps.value
            val pkgs: Set<String> = when (kind) {
                "social" -> Presets.social
                "games" -> installed.filter { it.isGame }.map { it.pkg }.toSet()
                "browsers" -> app.packageManager
                    .queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE), 0)
                    .map { it.activityInfo.packageName }.toSet()
                else -> emptySet()
            }
            installed.filter { it.pkg in pkgs }.forEach { a ->
                val cur = rules.value.find { it.packageName == a.pkg } ?: AppRule(a.pkg, a.label)
                dao.upsertRule(cur.copy(blockInFocus = true))
            }
        }
    }

    fun startFocus(min: Int, strict: Boolean) { viewModelScope.launch { FocusController.start(app, min, strict) } }
    fun endFocus(reason: String) { viewModelScope.launch { FocusController.end(app, reason) } }
    fun upsertSchedule(s: Schedule) { viewModelScope.launch { dao.upsertSchedule(s) } }
    fun deleteSchedule(s: Schedule) { viewModelScope.launch { dao.deleteSchedule(s) } }
    fun addDomain(raw: String) {
        val clean = raw.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .trim('/')
        if (clean.isBlank() || clean.count { it == '.' } < 1) return
        viewModelScope.launch(Dispatchers.IO) { dao.addDomain(CustomDomain(clean)) }
    }
    fun removeDomain(domain: String) { viewModelScope.launch(Dispatchers.IO) { dao.removeDomain(CustomDomain(domain)) } }
    fun logUrge(trigger: String, intensity: Int, note: String) {
        viewModelScope.launch { dao.insertUrge(UrgeLog(timeMs = System.currentTimeMillis(), triggerName = trigger.ifBlank { "Not specified" }, intensity = intensity, note = note)) }
    }
    fun <T> setPref(k: Preferences.Key<T>, v: T) { viewModelScope.launch { app.settings.set(k, v) } }

    fun wipeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            FocusController.end(app, "manual")
            app.db.clearAllTables()
            usage.value = UsageUi()
        }
    }
}
