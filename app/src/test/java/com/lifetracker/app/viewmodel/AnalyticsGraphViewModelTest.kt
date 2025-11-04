package com.lifetracker.app.viewmodel

import com.lifetracker.app.analytics.EventAnalyticsCalculations
import com.lifetracker.app.testing.MainDispatcherRule
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.analytics.RollingAveragePoint
import com.lifetracker.core.analytics.RoutinePredictabilityPoint
import com.lifetracker.core.analytics.TagAggregate
import com.lifetracker.core.analytics.TaskTransition
import com.lifetracker.core.model.EventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsGraphViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val referenceEnd = Instant.parse("2025-01-02T00:00:00Z")

    @Test
    fun `refresh populates buckets and insufficiency count`() = runTest {
        val transitions = listOf(
            transitionAt("2025-01-01T08:05:00Z", "A", "B"),
            transitionAt("2025-01-01T08:45:00Z", "A", "B"),
            transitionAt("2025-01-01T08:55:00Z", "A", "B"),
            transitionAt("2025-01-01T09:15:00Z", "A", "C"),
            transitionAt("2025-01-01T10:00:00Z", "C", "A")
        )
        val repository = FakeEventAnalyticsRepository(transitions)
        val viewModel = AnalyticsGraphViewModel(
            analyticsRepository = repository,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            zoneId = ZoneOffset.UTC,
            timeProvider = { referenceEnd }
        )

        viewModel.selectRange(1)
        advanceUntilIdle()

        val state = viewModel.screenState.value.predictability
        assertFalse(state.isLoading)
        val expectedStart = referenceEnd.minus(1, ChronoUnit.DAYS)
        assertEquals(expectedStart..referenceEnd, state.lastRange)
        assertEquals(1, state.selectedRangeDays)
        assertEquals(3, state.buckets.size)
        assertEquals(2, state.insufficientBucketCount)
        assertTrue(state.buckets.first().weightedProbability != null)
    }

    @Test
    fun `sanitizes sample smoothing and bucket parameters`() = runTest {
        val transitions = listOf(transitionAt("2025-01-01T08:05:00Z", "A", "B"))
        val repository = FakeEventAnalyticsRepository(transitions)
        val viewModel = AnalyticsGraphViewModel(
            analyticsRepository = repository,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            zoneId = ZoneOffset.UTC,
            timeProvider = { referenceEnd }
        )

        viewModel.selectRange(1)
        advanceUntilIdle()

        viewModel.selectMinSamples(0)
        viewModel.selectSmoothingAlpha(2.0)
        viewModel.selectBucketMinutes(5)
        advanceUntilIdle()

        val state = viewModel.screenState.value.predictability
        assertEquals(AnalyticsGraphViewModel.MIN_BUCKET_MINUTES, state.selectedBucketMinutes)
        assertEquals(AnalyticsGraphViewModel.MIN_SAMPLE_THRESHOLD, state.selectedMinSamples)
        assertEquals(AnalyticsGraphViewModel.MAX_SMOOTHING_ALPHA, state.selectedSmoothingAlpha, 0.0)
    }

    private fun transitionAt(timestamp: String, source: String, destination: String): TaskTransition =
        TaskTransition(
            timestamp = Instant.parse(timestamp),
            sourceTaskId = source,
            destinationTaskId = destination
        )

    private class FakeEventAnalyticsRepository(
        private val transitions: List<TaskTransition>
    ) : EventAnalyticsRepository {
        override suspend fun countEventsByType(start: Instant, end: Instant): Map<EventType, Long> = emptyMap()

        override suspend fun rollingAverage(
            type: EventType,
            windowDays: Int,
            start: Instant,
            end: Instant
        ): List<RollingAveragePoint> = emptyList()

        override suspend fun topQuickLogTags(
            limit: Int,
            start: Instant,
            end: Instant
        ): List<TagAggregate> = emptyList()

        override suspend fun routinePredictability(start: Instant, end: Instant): List<RoutinePredictabilityPoint> =
            EventAnalyticsCalculations.predictabilityPoints(transitions)

        override suspend fun taskCompletionTransitions(start: Instant, end: Instant): List<TaskTransition> = transitions
    }
}
