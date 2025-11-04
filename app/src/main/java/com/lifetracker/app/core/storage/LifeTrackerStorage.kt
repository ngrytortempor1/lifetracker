package com.lifetracker.app.core.storage

import com.lifetracker.core.model.Event
import com.lifetracker.core.model.Habit
import com.lifetracker.core.model.MoodEntry
import com.lifetracker.core.model.QuickLogTag
import com.lifetracker.core.model.SleepSession
import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskList
import java.time.Instant

/**
 * Core storage contract that allows the app to swap persistence implementations.
 * Concrete plugins (e.g., JSONL, database) implement this interface.
 */
interface LifeTrackerStorage {
    suspend fun appendEvent(event: Event)
    suspend fun readEventsByDateRange(startDate: Instant, endDate: Instant): List<Event>
    fun getExportFiles(): List<java.io.File>

    suspend fun saveHabits(habits: List<Habit>)
    suspend fun readHabits(): List<Habit>

    suspend fun saveTags(tags: List<QuickLogTag>)
    suspend fun readTags(): List<QuickLogTag>

    suspend fun saveTaskLists(lists: List<TaskList>)
    suspend fun readTaskLists(): List<TaskList>

    suspend fun saveTasks(tasks: List<Task>)
    suspend fun readTasks(): List<Task>

    suspend fun saveMoodEntries(entries: List<MoodEntry>)
    suspend fun readMoodEntries(): List<MoodEntry>

    suspend fun saveSleepSessions(sessions: List<SleepSession>)
    suspend fun readSleepSessions(): List<SleepSession>
}
