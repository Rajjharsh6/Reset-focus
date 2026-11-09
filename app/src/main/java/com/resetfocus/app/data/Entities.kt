package com.resetfocus.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-app rules: block during focus, trigger the urge interceptor, or allow in allow-list mode. */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val label: String,
    val blockInFocus: Boolean = false,
    val urgeTrigger: Boolean = false,
    val allowed: Boolean = false,
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val plannedMin: Int,
    val actualMs: Long = 0,
    val completed: Boolean = false,
    val strict: Boolean = false,
    val endReason: String? = null, // completed | manual | emergency
)

object Kinds {
    const val APP = "APP"
    const val WEB = "WEB"
    const val URGE = "URGE"
    const val URGE_RESIST = "URGE_RESIST"
    const val URGE_CONTINUE = "URGE_CONTINUE"
}

/** Counts only. For websites we store a counter event, never the URL or domain visited. */
@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val kind: String,
    val target: String,
)

@Entity(tableName = "urge_logs")
data class UrgeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val triggerName: String,
    val intensity: Int,
    val note: String,
)

@Entity(tableName = "custom_domains")
data class CustomDomain(@PrimaryKey val domain: String)

/** daysMask: bit0 = Monday ... bit6 = Sunday. Overnight windows (start > end) are supported. */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean = true,
)

@Entity(tableName = "daily_usage")
data class DailyUsage(@PrimaryKey val dayStartMs: Long, val totalMs: Long)
