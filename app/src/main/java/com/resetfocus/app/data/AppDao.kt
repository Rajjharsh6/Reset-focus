package com.resetfocus.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM app_rules") fun appRules(): Flow<List<AppRule>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRule(r: AppRule)
    @Query("DELETE FROM app_rules WHERE packageName = :p") suspend fun removeRule(p: String)

    @Insert suspend fun insertSession(s: FocusSession): Long
    @Update suspend fun updateSession(s: FocusSession)
    @Query("SELECT * FROM focus_sessions WHERE id = :id") suspend fun session(id: Long): FocusSession?
    @Query("SELECT * FROM focus_sessions ORDER BY startMs DESC") fun allSessions(): Flow<List<FocusSession>>

    @Insert suspend fun insertEvent(e: BlockEvent)
    @Query("SELECT * FROM block_events WHERE timeMs >= :from") fun eventsSince(from: Long): Flow<List<BlockEvent>>

    @Insert suspend fun insertUrge(u: UrgeLog)
    @Query("SELECT * FROM urge_logs ORDER BY timeMs DESC") fun urges(): Flow<List<UrgeLog>>

    @Query("SELECT * FROM schedules") fun schedules(): Flow<List<Schedule>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSchedule(s: Schedule)
    @Delete suspend fun deleteSchedule(s: Schedule)

    @Query("SELECT * FROM custom_domains ORDER BY domain") fun domains(): Flow<List<CustomDomain>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDomain(d: CustomDomain)
    @Delete suspend fun removeDomain(d: CustomDomain)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDaily(d: DailyUsage)
    @Query("SELECT * FROM daily_usage WHERE dayStartMs >= :from ORDER BY dayStartMs") fun dailySince(from: Long): Flow<List<DailyUsage>>
}

@Database(
    entities = [AppRule::class, FocusSession::class, BlockEvent::class, UrgeLog::class,
        CustomDomain::class, Schedule::class, DailyUsage::class],
    version = 1, exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        fun build(c: Context): AppDatabase =
            Room.databaseBuilder(c, AppDatabase::class.java, "resetfocus.db").build()
    }
}
