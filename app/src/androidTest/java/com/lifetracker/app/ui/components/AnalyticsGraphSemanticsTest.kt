package com.lifetracker.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifetracker.app.analytics.EventAnalyticsCalculations
import com.lifetracker.app.analytics.EventAnalyticsCalculations.BucketTransition
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityBucket
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityDataStatus
import com.lifetracker.core.analytics.RollingAveragePoint
import com.lifetracker.core.analytics.TagAggregate
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsGraphSemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun predictabilityChart_exposesDetailedContentDescription() {
        val zoneId = ZoneOffset.UTC
        val buckets = listOf(
            bucket(
                start = Instant.parse("2025-01-01T08:00:00Z"),
                probability = 0.62,
                ema = 0.55,
                samples = 6,
                status = EventAnalyticsCalculations.PredictabilityDataStatus.VALID
            ),
            bucket(
                start = Instant.parse("2025-01-01T09:00:00Z"),
                probability = null,
                ema = null,
                samples = 2,
                status = EventAnalyticsCalculations.PredictabilityDataStatus.INSUFFICIENT_SAMPLES
            )
        )
        val expected = buildPredictabilityContentDescription(buckets, zoneId)

        composeRule.setContent {
            PredictabilityChart(
                buckets = buckets,
                descriptionZoneId = zoneId
            )
        }

        composeRule
            .onNode(hasContentDescription(expected))
            .assertExists()
    }

    @Test
    fun rollingAverageChart_exposesDetailedContentDescription() {
        val zoneId = ZoneOffset.UTC
        val points = listOf(
            RollingAveragePoint(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-01-02T00:00:00Z"),
                average = 0.6
            ),
            RollingAveragePoint(
                windowStart = Instant.parse("2025-01-02T00:00:00Z"),
                windowEnd = Instant.parse("2025-01-03T00:00:00Z"),
                average = 0.75
            )
        )
        val expected = buildRollingAverageContentDescription(points, zoneId)

        composeRule.setContent {
            RollingAverageChart(points = points, descriptionZoneId = zoneId)
        }

        composeRule
            .onNode(hasContentDescription(expected))
            .assertExists()
    }

    @Test
    fun quickLogLeaderboard_exposesDetailedContentDescription() {
        val zoneId = ZoneOffset.UTC
        val items = listOf(
            TagAggregate(
                tag = "focus",
                occurrences = 8,
                lastOccurredAt = Instant.parse("2025-01-01T12:00:00Z")
            ),
            TagAggregate(
                tag = "energy",
                occurrences = 4,
                lastOccurredAt = Instant.parse("2025-01-01T10:00:00Z")
            )
        )
        val expected = buildQuickLogLeaderboardDescription(items, zoneId)

        composeRule.setContent {
            QuickLogTagLeaderboard(items = items, zoneId = zoneId)
        }

        composeRule
            .onNode(hasContentDescription(expected))
            .assertExists()
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
