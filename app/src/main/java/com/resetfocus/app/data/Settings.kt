package com.resetfocus.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.ds: DataStore<Preferences> by preferencesDataStore("settings")

data class Prefs(
    val strictCooldownSec: Int = 30,
    val urgeCooldownSec: Int = 30,
    val dailyGoalMin: Int = 60,
    val allowlistMode: Boolean = false,
    val websiteBlocking: Boolean = false,
    val onboarded: Boolean = false,
)

class SettingsStore(private val c: Context) {
    companion object {
        val STRICT_CD = intPreferencesKey("strict_cd")
        val URGE_CD = intPreferencesKey("urge_cd")
        val GOAL = intPreferencesKey("goal")
        val ALLOWLIST = booleanPreferencesKey("allowlist")
        val WEBSITE_BLOCKING = booleanPreferencesKey("website_blocking")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val flow: Flow<Prefs> = c.ds.data.map {
        Prefs(
            strictCooldownSec = it[STRICT_CD] ?: 30,
            urgeCooldownSec = it[URGE_CD] ?: 30,
            dailyGoalMin = it[GOAL] ?: 60,
            allowlistMode = it[ALLOWLIST] ?: false,
            websiteBlocking = it[WEBSITE_BLOCKING] ?: false,
            onboarded = it[ONBOARDED] ?: false,
        )
    }

    suspend fun <T> set(k: Preferences.Key<T>, v: T) { c.ds.edit { it[k] = v } }
}

/** Hot-path state read synchronously by the accessibility service. */
object FocusState {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("focus_state", Context.MODE_PRIVATE) }

    val activeUntilMs: Long get() = sp.getLong("until", 0L)
    val startMs: Long get() = sp.getLong("start", 0L)
    val sessionId: Long get() = sp.getLong("id", 0L)
    val strict: Boolean get() = sp.getBoolean("strict", false)
    fun isSessionActive(): Boolean = System.currentTimeMillis() < activeUntilMs

    fun set(id: Long, start: Long, until: Long, strict: Boolean) {
        sp.edit().putLong("id", id).putLong("start", start).putLong("until", until).putBoolean("strict", strict).apply()
    }
    fun clear() { sp.edit().putLong("until", 0L).putLong("id", 0L).apply() }
}

object ScheduleUtil {
    val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun isActive(s: Schedule, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!s.enabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon = 0 ... Sun = 6
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        fun on(d: Int) = ((s.daysMask shr d) and 1) == 1
        return if (s.startMin <= s.endMin) on(dow) && m >= s.startMin && m < s.endMin
        else (on(dow) && m >= s.startMin) || (on((dow + 6) % 7) && m < s.endMin)
    }

    fun daysLabel(mask: Int): String =
        if (mask == 127) "Every day" else DAYS.filterIndexed { i, _ -> ((mask shr i) and 1) == 1 }.joinToString(" ")
}
