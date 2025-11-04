package com.lifetracker.app.ui.components

import com.lifetracker.app.analytics.EventAnalyticsCalculations.BucketTransition
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityBucket
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityDataStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictabilityChartSemanticsTest {

    private val zoneId = ZoneOffset.UTC

    @Test
    fun buildDescriptionIncludesBucketSummaries() {
        val buckets = listOf(
            bucket(
                start = Instant.parse("2025-01-01T08:00:00Z"),
                probability = 0.6,
                ema = 0.5,
                samples = 4,
                status = PredictabilityDataStatus.VALID
            ),
            bucket(
                start = Instant.parse("2025-01-01T09:00:00Z"),
                probability = null,
                ema = null,
                samples = 2,
                status = PredictabilityDataStatus.INSUFFICIENT_SAMPLES
            )
        )

        val description = buildPredictabilityContentDescription(buckets, zoneId)

        assertTrue(description.contains("Predictability chart with 2 buckets"))
        assertTrue(description.contains("1/1 08:00"))
        assertTrue(description.contains("60 percent"))
        assertTrue(description.contains("trend rising"))
        assertTrue(description.contains("low confidence"))
    }

    @Test
    fun buildDescriptionHandlesEmptyBuckets() {
        val description = buildPredictabilityContentDescription(emptyList(), zoneId)
        assertEquals("Predictability chart has no data.", description)
    }

    private fun bucket(
        start: Instant,
        probability: Double?,
        ema: Double?,
        samples: Long,
        status: PredictabilityDataStatus
    ): PredictabilityBucket {
        val end = start.plus(1, ChronoUnit.HOURS)
        return PredictabilityBucket(
            bucketStart = start,
            bucketEnd = end,
            sampleSize = samples,
            uniquePairCount = 1,
            weightedProbability = probability,
            emaProbability = ema,
            dataStatus = status,
            transitions = emptyList<BucketTransition>()
        )
    }
}
