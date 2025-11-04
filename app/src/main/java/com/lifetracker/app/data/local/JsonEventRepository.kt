package com.lifetracker.app.data.local

import com.lifetracker.core.repository.EventRepository
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.EventPayload
import com.lifetracker.core.model.EventType
import com.lifetracker.core.model.Habit
import com.lifetracker.core.model.LogType
import com.lifetracker.core.model.PomodoroTargetType
import com.lifetracker.core.model.QuickLogTag
import com.lifetracker.core.model.ensureMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * JSONL-backed implementation of [EventRepository].
 */
class JsonEventRepository(
    private val storage: LifeTrackerStorage
) : EventRepository {

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    override val habits: Flow<List<Habit>> = _habits.asStateFlow()

    private val _tags = MutableStateFlow<List<QuickLogTag>>(emptyList())
    override val tags: Flow<List<QuickLogTag>> = _tags.asStateFlow()

    override suspend fun initialize() {
        _habits.value = storage.readHabits()
        _tags.value = storage.readTags()

        if (_habits.value.isEmpty()) {
            createDefaultHabits()
        }
        if (_tags.value.isEmpty()) {
            createDefaultTags()
        }
    }

    override suspend fun logHabitCompletion(habitId: String, notes: String?) {
        val event = Event(
            timestamp = Instant.now().toString(),
            type = EventType.HABIT_COMPLETED,
            payload = EventPayload.HabitCompleted(habitId, notes),
        )
        storage.appendEvent(event.ensureMetadata())
    }

    override suspend fun logQuick(tag: String, value: Double?, context: String?) {
        val event = Event(
            timestamp = Instant.now().toString(),
            type = EventType.LOG_QUICK,
            payload = EventPayload.QuickLog(tag, value, context)
        )
        storage.appendEvent(event.ensureMetadata())
    }

    override suspend fun logTaskCompletion(taskId: String, projectId: String?, notes: String?) {
        val event = Event(
            timestamp = Instant.now().toString(),
            type = EventType.TASK_COMPLETED,
            payload = EventPayload.TaskCompleted(taskId, projectId, notes)
        )
        storage.appendEvent(event.ensureMetadata())
    }

    override suspend fun logPomodoroCompletion(
        targetType: PomodoroTargetType,
        targetId: String?,
        focusDurationSeconds: Int,
        breakDurationSeconds: Int?,
        startedAt: Instant,
        endedAt: Instant,
        interrupted: Boolean
    ) {
        val event = Event(
            timestamp = endedAt.toString(),
            type = EventType.POMODORO_COMPLETED,
            payload = EventPayload.PomodoroCompleted(
                targetType = targetType,
                targetId = targetId,
                focusDurationSeconds = focusDurationSeconds,
                breakDurationSeconds = breakDurationSeconds,
                startedAt = startedAt.toString(),
                endedAt = endedAt.toString(),
                interrupted = interrupted
            )
        )
        storage.appendEvent(event.ensureMetadata())
    }

    override suspend fun getTodayCompletedHabits(): Set<String> {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

        return storage.readEventsByDateRange(startOfDay, endOfDay)
            .filter { it.type == EventType.HABIT_COMPLETED }
            .mapNotNull { event ->
                (event.payload as? EventPayload.HabitCompleted)?.habitId
            }
            .toSet()
    }

    override suspend fun addHabit(habit: Habit) {
        val updated = _habits.value + habit
        _habits.value = updated
        storage.saveHabits(updated)
    }

    override suspend fun archiveHabit(habitId: String) {
        val updated = _habits.value.map {
            if (it.id == habitId) it.copy(isArchived = true) else it
        }
        _habits.value = updated
        storage.saveHabits(updated)
    }

    override suspend fun addTag(tag: QuickLogTag) {
        val updated = _tags.value + tag
        _tags.value = updated
        storage.saveTags(updated)
    }

    override fun getExportFiles(): List<java.io.File> = storage.getExportFiles()

    private suspend fun createDefaultHabits() {
        val defaults = listOf(
            Habit(
                id = "morning-exercise",
                name = "朝の運動",
                description = "30分以上運動",
                icon = "🏃",
                color = "#4CAF50",
                createdAt = Instant.now().toString()
            ),
            Habit(
                id = "reading",
                name = "読書",
                description = "15分以上読む",
                icon = "📚",
                color = "#2196F3",
                createdAt = Instant.now().toString()
            ),
            Habit(
                id = "meditation",
                name = "瞑想",
                description = "10分の瞑想",
                icon = "🧘",
                color = "#9C27B0",
                createdAt = Instant.now().toString()
            )
        )
        _habits.value = defaults
        storage.saveHabits(defaults)
    }

    private suspend fun createDefaultTags() {
        val defaults = listOf(
            QuickLogTag(
                id = "mood",
                name = "気分",
                type = LogType.SCALE,
                min = 1.0,
                max = 10.0,
                createdAt = Instant.now().toString()
            ),
            QuickLogTag(
                id = "energy",
                name = "エネルギー",
                type = LogType.SCALE,
                min = 1.0,
                max = 10.0,
                createdAt = Instant.now().toString()
            ),
            QuickLogTag(
                id = "sleep-hours",
                name = "睡眠時間",
                type = LogType.NUMERIC,
                unit = "時間",
                min = 0.0,
                max = 24.0,
                createdAt = Instant.now().toString()
            )
        )
        _tags.value = defaults
        storage.saveTags(defaults)
    }
}
