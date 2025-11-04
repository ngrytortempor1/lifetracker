package com.lifetracker.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * Core representation of a task list, shared between the app and kernel modules.
 */
@Serializable
data class TaskList(
    val id: String,
    val name: String,
    val icon: String = "\uD83D\uDCDD",
    val color: String = "#2196F3",
    val createdAt: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Core task representation. Serializable so plugins can persist it directly.
 */
@Serializable
data class Task(
    val id: String,
    val listId: String,
    val title: String,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false,
    val dueDate: String? = null,      // ISO-8601 date (yyyy-MM-dd)
    val reminderTime: String? = null, // ISO-8601 instant
    val isInMyDay: Boolean = false,
    val createdAt: String,
    val completedAt: String? = null,
    val steps: List<TaskStep> = emptyList(),
    val repeatRule: TaskRepeatRule? = null,
    val repeatDetail: String? = null
)

/**
 * Sub-task that belongs to a parent task.
 */
@Serializable
data class TaskStep(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false
)

/**
 * Supported repeat rules for a task.
 */
@Serializable
enum class TaskRepeatRule {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM
}

/**
 * Value object for aggregating task creation parameters.
 */
data class TaskCreationParams(
    val title: String,
    val listId: String,
    val isImportant: Boolean,
    val addToMyDay: Boolean,
    val dueDate: LocalDate?,
    val reminderTime: Instant?,
    val repeatRule: TaskRepeatRule?,
    val repeatDetail: String?
)
