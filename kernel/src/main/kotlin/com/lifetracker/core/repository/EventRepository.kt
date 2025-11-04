package com.lifetracker.core.repository

import com.lifetracker.core.model.Habit
import com.lifetracker.core.model.PomodoroTargetType
import com.lifetracker.core.model.QuickLogTag
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Core contract for event-based data (habits, quick logs, task completions).
 * Implementations can be swapped to change persistence or sync strategy.
 */
interface EventRepository {
    val habits: Flow<List<Habit>>
    val tags: Flow<List<QuickLogTag>>

    suspend fun initialize()

    suspend fun logHabitCompletion(habitId: String, notes: String? = null)
    suspend fun logQuick(tag: String, value: Double? = null, context: String? = null)
    suspend fun logTaskCompletion(taskId: String, projectId: String? = null, notes: String? = null)
    suspend fun logPomodoroCompletion(
        targetType: PomodoroTargetType,
        targetId: String?,
        focusDurationSeconds: Int,
        breakDurationSeconds: Int?,
        startedAt: Instant,
        endedAt: Instant,
        interrupted: Boolean = false
    )

    suspend fun getTodayCompletedHabits(): Set<String>

    suspend fun addHabit(habit: Habit)
    suspend fun archiveHabit(habitId: String)

    suspend fun addTag(tag: QuickLogTag)

    fun getExportFiles(): List<java.io.File>
}

