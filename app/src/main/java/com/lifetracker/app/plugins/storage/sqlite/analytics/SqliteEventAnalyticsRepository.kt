package com.lifetracker.app.plugins.storage.sqlite.analytics

import com.lifetracker.app.analytics.EventAnalyticsCalculations
import com.lifetracker.app.plugins.storage.sqlite.db.dao.EventTypeCount
import com.lifetracker.app.plugins.storage.sqlite.db.dao.LifeTrackerDao
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.analytics.RollingAveragePoint
import com.lifetracker.core.analytics.RoutinePredictabilityPoint
import com.lifetracker.core.analytics.TagAggregate
import com.lifetracker.core.analytics.TaskTransition
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.EventType
import com.lifetracker.core.model.ensureMetadata
import java.time.Instant
import kotlinx.serialization.json.Json

class SqliteEventAnalyticsRepository(
    private val dao: LifeTrackerDao
) : EventAnalyticsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun countEventsByType(start: Instant, end: Instant): Map<EventType, Long> {
        val counts = dao.countEventsByType(start.toString(), end.toString())
        return counts.mapNotNull { it.toEventTypeCount() }.toMap()
    }

    override suspend fun rollingAverage(
        type: EventType,
        windowDays: Int,
        start: Instant,
        end: Instant
    ): List<RollingAveragePoint> {
        val events = fetchEventsByType(type, start, end)
        return EventAnalyticsCalculations.rollingAverage(events, type, windowDays, start, end)
    }

    override suspend fun topQuickLogTags(
        limit: Int,
        start: Instant,
        end: Instant
    ): List<TagAggregate> {
        val events = fetchEventsByType(EventType.LOG_QUICK, start, end)
        return EventAnalyticsCalculations.topQuickLogTags(events, limit)
    }

    override suspend fun routinePredictability(
        start: Instant,
        end: Instant
    ): List<RoutinePredictabilityPoint> {
        val taskEvents = fetchEventsByType(EventType.TASK_COMPLETED, start, end)
        return EventAnalyticsCalculations.routinePredictability(taskEvents)
    }

    override suspend fun taskCompletionTransitions(
        start: Instant,
        end: Instant
    ): List<TaskTransition> {
        val taskEvents = fetchEventsByType(EventType.TASK_COMPLETED, start, end)
        return EventAnalyticsCalculations.taskCompletionTransitions(taskEvents)
    }

    private suspend fun fetchEventsByType(
        type: EventType,
        start: Instant,
        end: Instant
    ): List<Event> = dao.getEventJsonByType(start.toString(), end.toString(), type.name)
        .mapNotNull { decodeEvent(it) }

    private fun decodeEvent(raw: String): Event? =
        runCatching { json.decodeFromString<Event>(raw).ensureMetadata() }.getOrNull()

    private fun EventTypeCount.toEventTypeCount(): Pair<EventType, Long>? =
        runCatching { EventType.valueOf(type) }.getOrNull()?.let { it to count }
}
