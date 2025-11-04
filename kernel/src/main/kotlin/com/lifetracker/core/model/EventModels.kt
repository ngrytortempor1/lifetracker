package com.lifetracker.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Habit definition stored in persistence and cached by repositories.
 */
@Serializable
data class Habit(
    val id: String,
    val name: String,
    val description: String = "",
    val color: String = "#6200EE",
    val icon: String = "\uD83D\uDCAA",
    val createdAt: String,
    val isArchived: Boolean = false
)

/**
 * Quick log tags allow users to capture frequently used metrics.
 */
@Serializable
data class QuickLogTag(
    val id: String,
    val name: String,
    val type: LogType,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val createdAt: String
)

@Serializable
enum class LogType {
    NUMERIC,
    BOOLEAN,
    SCALE
}

@Serializable
enum class PomodoroTargetType {
    NONE,
    TASK,
    HABIT
}

/**
 * Immutable event model recorded in the event stream.
 */
@Serializable
data class Event(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val source: String = "android",
    val type: EventType,
    val payload: EventPayload,
    val metadata: EventMetadata = EventMetadata()
)

@Serializable
data class EventMetadata(
    val tags: List<String> = emptyList(),
    val details: Map<String, String> = emptyMap()
)

@Serializable
enum class EventType {
    HABIT_COMPLETED,
    TASK_COMPLETED,
    LOG_QUICK,
    POMODORO_COMPLETED
}

/**
 * Event payloads are encoded as a sealed hierarchy for serialization.
 */
@Serializable
sealed class EventPayload {
    @Serializable
    data class HabitCompleted(
        val habitId: String,
        val notes: String? = null
    ) : EventPayload()

    @Serializable
    data class TaskCompleted(
        val taskId: String,
        val projectId: String? = null,
        val completionNotes: String? = null
    ) : EventPayload()

    @Serializable
    data class QuickLog(
        val tag: String,
        val value: Double? = null,
        val context: String? = null
    ) : EventPayload()

    @Serializable
    data class PomodoroCompleted(
        val targetType: PomodoroTargetType,
        val targetId: String? = null,
        val focusDurationSeconds: Int,
        val breakDurationSeconds: Int? = null,
        val startedAt: String,
        val endedAt: String,
        val interrupted: Boolean = false
    ) : EventPayload()
}
