package com.lifetracker.app.data.analytics

import com.lifetracker.app.analytics.EventAnalyticsCalculations
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.analytics.RollingAveragePoint
import com.lifetracker.core.analytics.RoutinePredictabilityPoint
import com.lifetracker.core.analytics.TagAggregate
import com.lifetracker.core.analytics.TaskTransition
import com.lifetracker.core.model.EventType
import java.time.Instant

class JsonEventAnalyticsRepository(
    private val storage: LifeTrackerStorage
) : EventAnalyticsRepository {

    override suspend fun countEventsByType(start: Instant, end: Instant): Map<EventType, Long> {
        val events = storage.readEventsByDateRange(start, end)
        return events.groupingBy { it.type }.eachCount().mapValues { it.value.toLong() }
    }

    override suspend fun rollingAverage(
        type: EventType,
        windowDays: Int,
        start: Instant,
        end: Instant
    ): List<RollingAveragePoint> {
        val events = storage.readEventsByDateRange(start, end)
        return EventAnalyticsCalculations.rollingAverage(events, type, windowDays, start, end)
    }

    override suspend fun topQuickLogTags(
        limit: Int,
        start: Instant,
        end: Instant
    ): List<TagAggregate> {
        val events = storage.readEventsByDateRange(start, end)
        return EventAnalyticsCalculations.topQuickLogTags(events, limit)
    }

    override suspend fun routinePredictability(
        start: Instant,
        end: Instant
    ): List<RoutinePredictabilityPoint> {
        val events = storage.readEventsByDateRange(start, end)
        return EventAnalyticsCalculations.routinePredictability(events)
    }

    override suspend fun taskCompletionTransitions(
        start: Instant,
        end: Instant
    ): List<TaskTransition> {
        val events = storage.readEventsByDateRange(start, end)
        return EventAnalyticsCalculations.taskCompletionTransitions(events)
    }
}
