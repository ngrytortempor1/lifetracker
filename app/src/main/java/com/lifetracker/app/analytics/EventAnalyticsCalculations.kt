package com.lifetracker.app.analytics

import com.lifetracker.core.analytics.RollingAveragePoint
import com.lifetracker.core.analytics.RoutinePredictabilityPoint
import com.lifetracker.core.analytics.TagAggregate
import com.lifetracker.core.analytics.TaskTransition
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.EventPayload
import com.lifetracker.core.model.EventType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

object EventAnalyticsCalculations {

    fun rollingAverage(
        events: List<Event>,
        type: EventType,
        windowDays: Int,
        start: Instant,
        end: Instant,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<RollingAveragePoint> {
        if (windowDays <= 0) return emptyList()
        val startDate = start.atZone(zoneId).toLocalDate()
        val endDate = end.atZone(zoneId).toLocalDate()
        if (endDate.isBefore(startDate)) return emptyList()

        val safeWindow = max(1, windowDays)
        val countsByDate = events.asSequence()
            .filter { it.type == type }
            .mapNotNull { event ->
                event.timestamp.toInstantOrNull()?.atZone(zoneId)?.toLocalDate()
            }
            .groupingBy { it }
            .eachCount()

        val points = mutableListOf<RollingAveragePoint>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val windowStart = maxOf(currentDate.minusDays((safeWindow - 1).toLong()), startDate)
            var total = 0
            var dayCounter = 0
            var iterDate = windowStart
            while (!iterDate.isAfter(currentDate)) {
                total += countsByDate[iterDate] ?: 0
                dayCounter += 1
                iterDate = iterDate.plusDays(1)
            }
            val average = if (dayCounter == 0) 0.0 else total.toDouble() / dayCounter.toDouble()
            points += RollingAveragePoint(
                windowStart = windowStart.atStartOfDay(zoneId).toInstant(),
                windowEnd = currentDate.plusDays(1).atStartOfDay(zoneId).toInstant(),
                average = average
            )
            currentDate = currentDate.plusDays(1)
        }
        return points
    }

    fun topQuickLogTags(events: List<Event>, limit: Int): List<TagAggregate> {
        if (limit <= 0) return emptyList()
        val aggregates = mutableMapOf<String, TagBucket>()
        events.asSequence()
            .filter { it.type == EventType.LOG_QUICK }
            .forEach { event ->
                val payload = event.payload as? EventPayload.QuickLog ?: return@forEach
                val timestamp = event.timestamp.toInstantOrNull() ?: return@forEach
                val bucket = aggregates.getOrPut(payload.tag) { TagBucket() }
                bucket.count += 1
                if (bucket.lastOccurredAt == null || timestamp.isAfter(bucket.lastOccurredAt)) {
                    bucket.lastOccurredAt = timestamp
                }
            }

        return aggregates.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, TagBucket>> { it.value.count }
                    .thenByDescending { it.value.lastOccurredAt }
            )
            .take(limit)
            .mapNotNull { (tag, bucket) ->
                val last = bucket.lastOccurredAt ?: return@mapNotNull null
                TagAggregate(tag, bucket.count, last)
            }
    }

    fun routinePredictability(events: List<Event>): List<RoutinePredictabilityPoint> =
        predictabilityPoints(taskCompletionTransitions(events))

    fun taskCompletionTransitions(events: List<Event>): List<TaskTransition> {
        val taskEvents = events.asSequence()
            .filter { it.type == EventType.TASK_COMPLETED }
            .mapNotNull { event ->
                val payload = event.payload as? EventPayload.TaskCompleted ?: return@mapNotNull null
                val timestamp = event.timestamp.toInstantOrNull() ?: return@mapNotNull null
                TaskCompletion(timestamp, payload.taskId)
            }
            .sortedBy { it.timestamp }
            .toList()

        if (taskEvents.size < 2) return emptyList()

        val transitions = ArrayList<TaskTransition>(taskEvents.size - 1)
        for (index in 0 until taskEvents.size - 1) {
            val current = taskEvents[index]
            val next = taskEvents[index + 1]
            transitions += TaskTransition(
                timestamp = next.timestamp,
                sourceTaskId = current.taskId,
                destinationTaskId = next.taskId
            )
        }

        return transitions
    }

    fun predictabilityPoints(transitions: List<TaskTransition>): List<RoutinePredictabilityPoint> {
        if (transitions.isEmpty()) return emptyList()
        val sortedTransitions = transitions.sortedBy { it.timestamp }
        val totalsBySource = sortedTransitions.groupingBy { it.sourceTaskId }.eachCount()
        val countsByPair = sortedTransitions.groupingBy { it.sourceTaskId to it.destinationTaskId }.eachCount()

        return sortedTransitions.mapNotNull { transition ->
            val total = totalsBySource[transition.sourceTaskId] ?: return@mapNotNull null
            val pairCount = countsByPair[transition.sourceTaskId to transition.destinationTaskId]
                ?: return@mapNotNull null
            val probability = if (total == 0) 0.0 else pairCount.toDouble() / total.toDouble()
            RoutinePredictabilityPoint(
                timestamp = transition.timestamp,
                probability = probability,
                sourceTaskId = transition.sourceTaskId,
                destinationTaskId = transition.destinationTaskId,
                sourceSampleSize = total.toLong(),
                pairOccurrences = pairCount.toLong()
            )
        }
    }

    fun predictabilityBuckets(
        points: List<RoutinePredictabilityPoint>,
        bucketMinutes: Int = 60,
        minSamplesForEstimate: Long = 3,
        smoothingAlpha: Double = 0.3,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<PredictabilityBucket> {
        if (points.isEmpty()) return emptyList()
        val positiveBucketMinutes = if (bucketMinutes <= 0) 60 else bucketMinutes

        val grouped = linkedMapOf<Instant, MutableList<RoutinePredictabilityPoint>>()
        points.sortedBy { it.timestamp }.forEach { point ->
            val zoned = point.timestamp.atZone(zoneId)
            val bucketMinute = (zoned.minute / positiveBucketMinutes) * positiveBucketMinutes
            val bucketStartZoned = zoned
                .withMinute(bucketMinute)
                .withSecond(0)
                .withNano(0)
            val bucketStart = bucketStartZoned.toInstant()
            grouped.getOrPut(bucketStart) { mutableListOf() }.add(point)
        }

        val bucketDuration = positiveBucketMinutes.toLong()
        val results = mutableListOf<PredictabilityBucket>()
        var ema: Double? = null

        grouped.entries.sortedBy { it.key }.forEach { (bucketStart, bucketPoints) ->
            val bucketTransitions = bucketPoints.map { point ->
                BucketTransition(
                    timestamp = point.timestamp,
                    probability = point.probability,
                    sourceTaskId = point.sourceTaskId,
                    destinationTaskId = point.destinationTaskId,
                    sourceSampleSize = point.sourceSampleSize,
                    pairOccurrences = point.pairOccurrences
                )
            }

            val sampleSize = bucketTransitions.size.toLong()
            val uniquePairs = bucketTransitions.map { it.sourceTaskId to it.destinationTaskId }.distinct().size
            val dataStatus = if (sampleSize >= minSamplesForEstimate) {
                PredictabilityDataStatus.VALID
            } else {
                PredictabilityDataStatus.INSUFFICIENT_SAMPLES
            }

            val weightedProbability = if (dataStatus == PredictabilityDataStatus.VALID && sampleSize > 0) {
                bucketTransitions.fold(0.0) { acc, transition -> acc + transition.probability } /
                    sampleSize.toDouble()
            } else {
                null
            }

            if (weightedProbability != null) {
                ema = if (ema == null) {
                    weightedProbability
                } else {
                    smoothingAlpha * weightedProbability + (1.0 - smoothingAlpha) * ema!!
                }
            }

            results += PredictabilityBucket(
                bucketStart = bucketStart,
                bucketEnd = bucketStart.plus(bucketDuration, ChronoUnit.MINUTES),
                sampleSize = sampleSize,
                uniquePairCount = uniquePairs,
                weightedProbability = weightedProbability,
                emaProbability = ema,
                dataStatus = dataStatus,
                transitions = bucketTransitions
            )
        }

        return results
    }

    data class BucketTransition(
        val timestamp: Instant,
        val probability: Double,
        val sourceTaskId: String,
        val destinationTaskId: String,
        val sourceSampleSize: Long,
        val pairOccurrences: Long
    )

    data class PredictabilityBucket(
        val bucketStart: Instant,
        val bucketEnd: Instant,
        val sampleSize: Long,
        val uniquePairCount: Int,
        val weightedProbability: Double?,
        val emaProbability: Double?,
        val dataStatus: PredictabilityDataStatus,
        val transitions: List<BucketTransition>
    )

    enum class PredictabilityDataStatus {
        VALID,
        INSUFFICIENT_SAMPLES
    }

    private fun String.toInstantOrNull(): Instant? =
        runCatching { Instant.parse(this) }.getOrNull()

    private data class TagBucket(
        var count: Long = 0,
        var lastOccurredAt: Instant? = null
    )

    private data class TaskCompletion(
        val timestamp: Instant,
        val taskId: String
    )
}