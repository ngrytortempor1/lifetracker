package com.lifetracker.core.analytics

import com.lifetracker.core.model.EventType
import java.time.Instant

/**
 * Contract for generating insights across the event stream without binding to a storage implementation.
 */
interface EventAnalyticsRepository {

    /**
     * Returns counts per event type within the given time window.
     */
    suspend fun countEventsByType(start: Instant, end: Instant): Map<EventType, Long>

    /**
     * Calculates rolling averages per event type (e.g., 7-day moving average).
     */
    suspend fun rollingAverage(
        type: EventType,
        windowDays: Int,
        start: Instant,
        end: Instant
    ): List<RollingAveragePoint>

    /**
     * Fetches the top tags involved in quick logs.
     */
    suspend fun topQuickLogTags(limit: Int, start: Instant, end: Instant): List<TagAggregate>

    /**
     * Calculates routine predictability transitions for completed tasks.
     */
    suspend fun routinePredictability(start: Instant, end: Instant): List<RoutinePredictabilityPoint>

    /**
     * Returns raw task completion transitions ordered by occurrence time.
     */
    suspend fun taskCompletionTransitions(start: Instant, end: Instant): List<TaskTransition>
}

data class RollingAveragePoint(
    val windowStart: Instant,
    val windowEnd: Instant,
    val average: Double
)

data class TagAggregate(
    val tag: String,
    val occurrences: Long,
    val lastOccurredAt: Instant
)

data class RoutinePredictabilityPoint(
    val timestamp: Instant,
    val probability: Double,
    val sourceTaskId: String,
    val destinationTaskId: String,
    val sourceSampleSize: Long,
    val pairOccurrences: Long
)

data class TaskTransition(
    val timestamp: Instant,
    val sourceTaskId: String,
    val destinationTaskId: String
)
