package com.lifetracker.app.analytics

import com.lifetracker.core.analytics.TaskTransition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EventAnalyticsCalculationsPredictabilityTest {

    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val baseDate: LocalDate = LocalDate.of(2025, 1, 1)

    @Test
    fun `predictability points include probability and sample metadata`() {
        val transitions = listOf(
            transitionAt(8, 5, "A", "B"),
            transitionAt(8, 40, "A", "B"),
            transitionAt(9, 10, "A", "C"),
            transitionAt(10, 0, "C", "A")
        )

        val points = EventAnalyticsCalculations.predictabilityPoints(transitions)

        assertEquals(4, points.size)

        val firstPair = points.first { it.destinationTaskId == "B" }
        assertEquals(2L, firstPair.pairOccurrences)
        assertEquals(3L, firstPair.sourceSampleSize)
        assertEquals(2.0 / 3.0, firstPair.probability, 0.0001)

        val crossPair = points.first { it.destinationTaskId == "C" }
        assertEquals(1L, crossPair.pairOccurrences)
        assertEquals(3L, crossPair.sourceSampleSize)
        assertEquals(1.0 / 3.0, crossPair.probability, 0.0001)
    }

    @Test
    fun `predictability buckets aggregate by hour and smooth values`() {
        val transitions = listOf(
            transitionAt(8, 5, "A", "B"),
            transitionAt(8, 40, "A", "B"),
            transitionAt(9, 10, "A", "C"),
            transitionAt(10, 0, "C", "A")
        )
        val points = EventAnalyticsCalculations.predictabilityPoints(transitions)

        val buckets = EventAnalyticsCalculations.predictabilityBuckets(
            points = points,
            bucketMinutes = 60,
            minSamplesForEstimate = 2,
            smoothingAlpha = 0.5,
            zoneId = zoneId
        )

        assertEquals(3, buckets.size)

        val eightAm = buckets[0]
        assertEquals(2L, eightAm.sampleSize)
        assertEquals(EventAnalyticsCalculations.PredictabilityDataStatus.VALID, eightAm.dataStatus)
        assertNotNull(eightAm.weightedProbability)
        assertEquals(2.0 / 3.0, eightAm.weightedProbability!!, 0.0001)
        assertEquals(eightAm.weightedProbability, eightAm.emaProbability)

        val nineAm = buckets[1]
        assertEquals(1L, nineAm.sampleSize)
        assertEquals(EventAnalyticsCalculations.PredictabilityDataStatus.INSUFFICIENT_SAMPLES, nineAm.dataStatus)
        assertNull(nineAm.weightedProbability)
        assertNotNull(nineAm.emaProbability)
        assertEquals(eightAm.emaProbability, nineAm.emaProbability)

        val tenAm = buckets[2]
        assertEquals(EventAnalyticsCalculations.PredictabilityDataStatus.INSUFFICIENT_SAMPLES, tenAm.dataStatus)
        assertNull(tenAm.weightedProbability)
        assertEquals(nineAm.emaProbability, tenAm.emaProbability)
    }

    private fun transitionAt(hour: Int, minute: Int, source: String, destination: String): TaskTransition {
        val instant: Instant = baseDate.atTime(hour, minute).toInstant(ZoneOffset.UTC)
        return TaskTransition(
            timestamp = instant,
            sourceTaskId = source,
            destinationTaskId = destination
        )
    }
}
