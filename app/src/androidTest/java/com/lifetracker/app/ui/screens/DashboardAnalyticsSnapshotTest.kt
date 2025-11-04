package com.lifetracker.app.ui.screens

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifetracker.app.viewmodel.DashboardUiState
import com.lifetracker.app.viewmodel.MoodSummary
import com.lifetracker.app.viewmodel.SleepSummary
import com.lifetracker.core.model.MoodSlot
import com.lifetracker.core.model.SleepQuality
import com.lifetracker.core.model.SleepSessionSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DashboardAnalyticsSnapshotTest {

    // TODO(lifetracker-dashboard-snapshot):
    // 1. Trim the scroll iteration now that unmerged semantics capture all cards reliably.
    // 2. Replace manual node lookups with a helper that targets specific dashboard rows.
    // 3. Remove temporary logging utilities once the snapshot stabilises.

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboardReadyState_matchesSemanticSnapshot() {
        val state = DashboardUiState.Ready(
            myDayCount = 3,
            todayDueCount = 2,
            overdueCount = 1,
            completedTodayCount = 4,
            habitTotal = 5,
            habitCompleted = 3,
            latestMood = MoodSummary(
                slot = MoodSlot.MORNING,
                score = 7,
                recordedAt = Instant.parse("2025-03-10T09:15:00Z"),
                note = "集中できた",
                tags = listOf("energy:high")
            ),
            lastSleep = SleepSummary(
                startedAt = Instant.parse("2025-03-09T22:00:00Z"),
                endedAt = Instant.parse("2025-03-10T06:30:00Z"),
                duration = Duration.ofHours(8).plusMinutes(30),
                source = SleepSessionSource.DEVICE_USAGE,
                quality = SleepQuality.GOOD,
                note = null
            )
        )

        composeRule.setContent {
            DashboardScreenContent(state = state)
        }

        composeRule.onRoot().printToLog("DashboardTree")

        composeRule.onNodeWithTag("dashboard_summary_list")
            .performScrollToNode(hasText("最新の睡眠"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_summary_list")
            .performScrollToNode(hasText("端末推定", substring = true))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("端末推定", substring = true)
            .assertIsDisplayed()

        val snapshot = collectTextSnapshot(composeRule, state)
        Log.i("DashboardTest", "\nDASHBOARD_SNAPSHOT:\n$snapshot")
        val expected = loadGolden("dashboard_ready_semantics.txt").trimEnd()

        assertEquals(expected, snapshot)
    }

    private fun loadGolden(name: String): String {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets
            .open("goldens/$name")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText().replace("\r\n", "\n") }
    }

    private fun collectTextSnapshot(
        rule: AndroidComposeTestRule<*, *>,
        state: DashboardUiState.Ready
    ): String {
        rule.waitForIdle()

        fun collectOnce(): List<String> {
            val texts = mutableListOf<String>()
            val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            collectTexts(root, texts)
            return texts
        }

        val merged = mutableListOf<String>()
        fun addLines(lines: Collection<String>) {
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isNotEmpty() && !merged.contains(line)) {
                    merged += line
                }
            }
        }

        fun addTextNode(text: String, substring: Boolean = false) {
            runCatching {
                val node = rule.onNodeWithText(
                    text = text,
                    substring = substring,
                    useUnmergedTree = true
                ).fetchSemanticsNode()
                val buffer = mutableListOf<String>()
                collectTexts(node, buffer)
                addLines(buffer)
            }
        }

        val listNode = rule.onNodeWithTag("dashboard_summary_list")
        val cardCount = baseCardCount(state)
        val totalItems = 1 + cardCount

        for (index in 0 until totalItems) {
            runCatching {
                listNode.performScrollToIndex(index)
                rule.waitForIdle()
                addLines(collectOnce())
            }
        }

        addTextNode("今日の概要")
        addTextNode("タスクと習慣の進捗をまとめて確認できます。")
        addTextNode("端末推定", substring = true)

        return merged.joinToString(separator = "\n").trimEnd()
    }

    private fun baseCardCount(state: DashboardUiState.Ready): Int {
        var count = 4
        if (state.latestMood != null) count += 1
        if (state.lastSleep != null) count += 1
        return count
    }

    private fun collectTexts(node: SemanticsNode, accumulator: MutableList<String>) {
        val annotatedStrings = node.config.getOrNull(SemanticsProperties.Text)
        if (!annotatedStrings.isNullOrEmpty()) {
            annotatedStrings.forEach { annotated ->
                val line = annotated.text.trim()
                if (line.isNotEmpty() && !accumulator.contains(line)) {
                    Log.i("DashboardSemantics", "line=$line")
                    accumulator += line
                }
            }
        }
        val contentDescriptions = node.config.getOrNull(SemanticsProperties.ContentDescription)
        if (!contentDescriptions.isNullOrEmpty()) {
            val line = contentDescriptions.joinToString(separator = "") { it }.trim()
            if (line.isNotEmpty() && !accumulator.contains(line)) {
                Log.i("DashboardSemantics", "contentDesc=$line")
                accumulator += line
            }
        }
        node.children.forEach { child -> collectTexts(child, accumulator) }
    }
}
