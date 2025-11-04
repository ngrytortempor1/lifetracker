package com.lifetracker.app.plugins.storage.sqlite.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val sortOrder: Int,
    val json: String
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val createdAt: String,
    val json: String
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val createdAt: String,
    val json: String
)

@Entity(tableName = "quick_log_tags")
data class QuickLogTagEntity(
    @PrimaryKey val id: String,
    val createdAt: String,
    val json: String
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val eventId: String,
    val timestamp: String,
    val type: String,
    val json: String,
    val tagsJson: String,
    val detailsJson: String
)

@Entity(tableName = "json_outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val payloadJson: String,
    val createdAt: Long,
    val processedAt: Long? = null
)

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey val entryId: String,
    val recordedAt: String,
    val slot: String,
    val json: String
)

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAt: String,
    val endedAt: String,
    val source: String,
    val json: String
)
