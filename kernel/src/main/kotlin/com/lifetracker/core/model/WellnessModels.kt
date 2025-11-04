package com.lifetracker.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Mood tracking entry captured by the app or imported from connected services.
 */
@Serializable
data class MoodEntry(
    val entryId: String = UUID.randomUUID().toString(),
    val recordedAt: String,
    val slot: MoodSlot,
    val score: Int,
    val note: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Day partitions used when collecting mood samples.
 */
@Serializable
enum class MoodSlot {
    MORNING,
    NOON,
    NIGHT
}

/**
 * Sleep session captured manually or imported via integrations.
 */
@Serializable
data class SleepSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val startedAt: String,
    val endedAt: String,
    val source: SleepSessionSource,
    val quality: SleepQuality? = null,
    val note: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Origin of a recorded sleep session.
 */
@Serializable
enum class SleepSessionSource {
    MANUAL,
    DEVICE_USAGE,
    HEALTH_CONNECT
}

/**
 * Optional qualitative assessment recorded alongside the sleep session.
 */
@Serializable
enum class SleepQuality {
    POOR,
    OKAY,
    GOOD
}
