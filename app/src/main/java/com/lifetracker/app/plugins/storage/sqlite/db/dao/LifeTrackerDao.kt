package com.lifetracker.app.plugins.storage.sqlite.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lifetracker.app.plugins.storage.sqlite.db.entities.EventEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.HabitEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.OutboxEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.QuickLogTagEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskListEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.MoodEntryEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.SleepSessionEntity

@Dao
interface LifeTrackerDao {
    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC")
    suspend fun getTaskLists(): List<TaskListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskLists(lists: List<TaskListEntity>)

    @Query("DELETE FROM task_lists")
    suspend fun clearTaskLists()

    @Query("SELECT * FROM tasks ORDER BY createdAt ASC")
    suspend fun getTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun getHabits(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("SELECT * FROM quick_log_tags ORDER BY createdAt ASC")
    suspend fun getQuickLogTags(): List<QuickLogTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickLogTags(tags: List<QuickLogTagEntity>)

    @Query("DELETE FROM quick_log_tags")
    suspend fun clearQuickLogTags()

    @Query("SELECT * FROM events WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getEventsBetween(start: String, end: String): List<EventEntity>

    @Query("SELECT json FROM events WHERE timestamp >= :start AND timestamp <= :end AND type = :type ORDER BY timestamp ASC")
    suspend fun getEventJsonByType(start: String, end: String, type: String): List<String>

    @Query(
        "SELECT type as type, COUNT(*) as count FROM events WHERE timestamp >= :start AND timestamp <= :end GROUP BY type"
    )
    suspend fun countEventsByType(start: String, end: String): List<EventTypeCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT COUNT(*) FROM events")
    suspend fun countEvents(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(entity: OutboxEntity)

    @Query("SELECT * FROM json_outbox WHERE processedAt IS NULL ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingOutbox(limit: Int): List<OutboxEntity>

    @Query("UPDATE json_outbox SET processedAt = :processedAt WHERE id = :id")
    suspend fun markOutboxProcessed(id: String, processedAt: Long)

    @Query("SELECT * FROM mood_entries ORDER BY recordedAt DESC")
    suspend fun getMoodEntries(): List<MoodEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodEntries(entries: List<MoodEntryEntity>)

    @Query("DELETE FROM mood_entries")
    suspend fun clearMoodEntries()

    @Query("SELECT * FROM sleep_sessions ORDER BY startedAt DESC")
    suspend fun getSleepSessions(): List<SleepSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSessions(sessions: List<SleepSessionEntity>)

    @Query("DELETE FROM sleep_sessions")
    suspend fun clearSleepSessions()
}

data class EventTypeCount(
    val type: String,
    val count: Long
)
