package com.lifetracker.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifetracker.app.analytics.EventAnalyticsCalculations.BucketTransition
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityBucket
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityDataStatus
import com.lifetracker.app.viewmodel.AnalyticsGraphScreenState
import com.lifetracker.app.viewmodel.PredictabilityGraphUiState
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsGraphDetailDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun detailDialogAppearsWithTransitions() {
        val zoneId = ZoneOffset.UTC
        val transitions = listOf(
            BucketTransition(
                timestamp = Instant.parse("2025-01-01T08:05:00Z"),
                probability = 0.7,
                sourceTaskId = "Review",
                destinationTaskId = "Plan",
                sourceSampleSize = 10,
                pairOccurrences = 7
            ),
            BucketTransition(
                timestamp = Instant.parse("2025-01-01T08:25:00Z"),
                probability = 0.4,
                sourceTaskId = "Plan",
                destinationTaskId = "Execute",
                sourceSampleSize = 5,
                pairOccurrences = 2
            )
        )
        val bucket = PredictabilityBucket(
            bucketStart = Instant.parse("2025-01-01T08:00:00Z"),
            bucketEnd = Instant.parse("2025-01-01T09:00:00Z"),
            sampleSize = transitions.size.toLong(),
            uniquePairCount = 2,
            weightedProbability = 0.55,
            emaProbability = 0.52,
            dataStatus = PredictabilityDataStatus.VALID,
            transitions = transitions
        )
        val state = PredictabilityGraphUiState(
            buckets = listOf(bucket),
            lastRange = Instant.parse("2025-01-01T08:00:00Z")..Instant.parse("2025-01-01T09:00:00Z"),
            availableRangeOptions = listOf(7),
            availableBucketOptions = listOf(60)
        )

        val screenState = AnalyticsGraphScreenState(predictability = state)

        composeRule.setContent {
            AnalyticsGraphContent(
                state = screenState,
                onRefresh = {},
                onRangeSelected = {},
                onBucketSelected = {},
                onMinSamplesSelected = {},
                onSmoothingSelected = {},
                onRollingWindowSelected = {},
                onRollingTypeSelected = {},
                onQuickLogLimitSelected = {},
                onTogglePresentationMode = {}
            )
        }

        composeRule.onNodeWithTag("analytics_graph_list")
            .performScrollToNode(hasTestTag("predictability_detail_chip"))

        composeRule.onNodeWithTag("predictability_detail_chip")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText("バケット詳細").assertIsDisplayed()
        composeRule.onNodeWithText("Review → Plan").assertIsDisplayed()
        composeRule.onNodeWithText("確率: 70% / サンプル: 7 / 総数: 10").assertIsDisplayed()
        composeRule.onNodeWithText("確率: 40% / サンプル: 2 / 総数: 5").assertIsDisplayed()

        composeRule.onNodeWithText("閉じる").performClick()
        composeRule.onNodeWithText("Review → Plan").assertDoesNotExistCompat()
    }
}

private fun SemanticsNodeInteraction.assertDoesNotExistCompat() {
    val doesExist = runCatching { assertIsDisplayed() }.isSuccess
    if (doesExist) {
        throw AssertionError("Expected node to be absent, but it was found.")
    }
}
