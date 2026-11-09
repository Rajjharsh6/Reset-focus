package com.resetfocus.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.resetfocus.app.dayStart
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DayUsage(val label: String, val ms: Long)
data class UsageUi(
    val loaded: Boolean = false,
    val todayMs: Long = 0,
    val topApps: List<Pair<String, Long>> = emptyList(),
    val week: List<DayUsage> = emptyList(),
)

class UsageRepository(private val c: Context) {
    private val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val excluded: Set<String> by lazy {
        val home = c.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        ).map { it.activityInfo.packageName }
        (home + listOf("com.android.systemui", "android", c.packageName)).toSet()
    }

    /** Foreground time per package, built from resume/pause events for accuracy. */
    fun perApp(from: Long, to: Long): Map<String, Long> {
        val end = minOf(to, System.currentTimeMillis())
        val ev = usm.queryEvents(from, end)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val p = e.packageName
            when (e.eventType) {
                1 -> open[p] = e.timeStamp                      // ACTIVITY_RESUMED
                2, 23 -> open.remove(p)?.let {                  // ACTIVITY_PAUSED / STOPPED
                    total[p] = (total[p] ?: 0L) + (e.timeStamp - it)
                }
            }
        }
        open.forEach { (p, s) -> total[p] = (total[p] ?: 0L) + (end - s) }
        return total.filterKeys { it !in excluded }
    }

    suspend fun build(dao: AppDao): UsageUi {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        val stored = dao.dailySince(dayStart(-6)).first().associate { it.dayStartMs to it.totalMs }
        val week = ArrayList<DayUsage>()
        var today = emptyMap<String, Long>()
        for (i in -6..0) {
            val s = dayStart(i)
            val m = perApp(s, if (i == 0) System.currentTimeMillis() else dayStart(i + 1))
            if (i == 0) today = m
            val total = if (m.isEmpty()) stored[s] ?: 0L else m.values.sum()
            week.add(DayUsage(fmt.format(Date(s)), total))
            if (i < 0 && total > 0) dao.upsertDaily(DailyUsage(s, total))
        }
        return UsageUi(true, today.values.sum(), today.entries.sortedByDescending { it.value }.map { it.key to it.value }, week)
    }
}
