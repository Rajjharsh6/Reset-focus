package com.resetfocus.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.util.ArrayDeque
import com.resetfocus.app.app
import com.resetfocus.app.appLabel
import com.resetfocus.app.data.*
import kotlinx.coroutines.*

/**
 * Reads ONLY window-state-change events (which package is in front). canRetrieveWindowContent is false,
 * so it cannot see text, passwords or messages, and it does not listen for key events.
 */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var rules: Map<String, AppRule> = emptyMap()
    @Volatile private var schedules: List<Schedule> = emptyList()
    @Volatile private var prefs = Prefs()
    @Volatile private var blockedDomains: Set<String> = emptySet()
    private var transient: Set<String> = emptySet()
    private var launchers: Set<String> = emptySet()
    private var lastPkg: String? = null
    private val lastLog = HashMap<String, Long>()
    private val urgeClearedUntil = HashMap<String, Long>()
    private var overlay: InterruptOverlay? = null
    private val browserPkgs = setOf(
        "com.android.chrome",
        "com.android.browser",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.brave.browser",
        "com.google.android.apps.messaging",
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = InterruptOverlay(this)
        buildIgnoredSets()
        val dao = app.db.dao()
        scope.launch { dao.appRules().collect { l -> rules = l.associateBy { it.packageName } } }
        scope.launch { dao.schedules().collect { schedules = it } }
        scope.launch { dao.domains().collect { blockedDomains = it.map { it.domain }.toSet() } }
        scope.launch { app.settings.flow.collect { prefs = it } }
    }

    private fun buildIgnoredSets() {
        val home = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )
        launchers = home.map { it.activityInfo.packageName }.toSet()
        val ime = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.map { it.packageName }
        transient = setOf(packageName, "com.android.systemui", "android", "com.miui.securitycenter", "com.lbe.security.miui") + ime
    }

    private fun focusActive(now: Long) = FocusState.isSessionActive() || schedules.any { ScheduleUtil.isActive(it, now) }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg in transient || pkg == lastPkg) return
        lastPkg = pkg
        if (pkg in launchers) return
        val now = System.currentTimeMillis()
        val rule = rules[pkg]
        if (focusActive(now)) {
            val block = if (prefs.allowlistMode) rule?.allowed != true else rule?.blockInFocus == true
            if (block) { blockApp(pkg, rule, now); return }
        }
        if (prefs.websiteBlocking && blockedDomains.isNotEmpty() && pkg in browserPkgs) {
            val url = findVisibleUrl(e)
            val host = url?.let { normalizeHost(it) }
            if (host != null && blockedDomains.any { it.equals(host, true) || host.endsWith(".$it") }) {
                blockWebsite(host, pkg, now)
                return
            }
        }
        if (rule?.urgeTrigger == true && (urgeClearedUntil[pkg] ?: 0L) < now) urgeIntercept(pkg, rule, now)
    }

    private fun normalizeHost(url: String): String? {
        val u = url.trim().lowercase()
        return try {
            val s = when {
                u.startsWith("http://") || u.startsWith("https://") -> u.substringAfter("//")
                else -> u
            }
            val clean = s.substringBefore('/').substringBefore('?').substringBefore('#')
            clean.substringBefore(':').trim().ifEmpty { null }
        } catch (_: Exception) { null }
    }

    private fun findVisibleUrl(e: AccessibilityEvent): String? {
        val root = rootInActiveWindow ?: return null
        val out = ArrayDeque<AccessibilityNodeInfo>()
        out.add(root)
        while (out.isNotEmpty()) {
            val n = out.removeFirst()
            val text = n.text?.toString() ?: n.contentDescription?.toString() ?: ""
            if (text.contains("http://", true) || text.contains("https://", true) || text.contains(".com", true) || text.contains(".io", true) || text.contains(".net", true)) {
                return text
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { out.addLast(it) }
        }
        return null
    }

    private fun log(kind: String, target: String) {
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { app.db.dao().insertEvent(BlockEvent(timeMs = now, kind = kind, target = target)) }
    }

    private fun blockApp(pkg: String, rule: AppRule?, now: Long) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val left = if (FocusState.isSessionActive()) ((FocusState.activeUntilMs - now) / 60000).toInt() + 1 else null
        overlay?.showBlock(rule?.label ?: appLabel(pkg), left)
        if ((lastLog[pkg] ?: 0L) + 3000 < now) { lastLog[pkg] = now; log(Kinds.APP, pkg) }
    }

    private fun blockWebsite(host: String, pkg: String, now: Long) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlay?.showBlock(host, null)
        if ((lastLog[pkg] ?: 0L) + 3000 < now) { lastLog[pkg] = now; log(Kinds.WEB, host) }
    }

    private fun urgeIntercept(pkg: String, rule: AppRule, now: Long) {
        log(Kinds.URGE, pkg)
        overlay?.showUrge(
            label = rule.label, secs = prefs.urgeCooldownSec,
            onClose = { performGlobalAction(GLOBAL_ACTION_HOME); log(Kinds.URGE_RESIST, pkg) },
            onLog = {
                scope.launch(Dispatchers.IO) {
                    app.db.dao().insertUrge(UrgeLog(timeMs = System.currentTimeMillis(), triggerName = rule.label, intensity = 5, note = "Logged from the pause screen"))
                }
                Toast.makeText(this, "Urge logged", Toast.LENGTH_SHORT).show()
            },
            onContinue = { urgeClearedUntil[pkg] = System.currentTimeMillis() + 10 * 60_000L; log(Kinds.URGE_CONTINUE, pkg) },
        )
    }

    override fun onInterrupt() { overlay?.dismiss() }
    override fun onDestroy() { overlay?.dismiss(); scope.cancel(); super.onDestroy() }
}
