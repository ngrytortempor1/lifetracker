package com.lifetracker.app.export.dashboard

import com.lifetracker.app.viewmodel.DashboardUiState
import com.lifetracker.app.viewmodel.MoodSummary
import com.lifetracker.app.viewmodel.SleepSummary
import com.lifetracker.core.model.MoodSlot
import com.lifetracker.core.model.SleepQuality
import com.lifetracker.core.model.SleepSessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class DashboardSnapshotMapperTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2025-03-10T09:15:00Z"), ZoneId.of("UTC"))
    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val locale: Locale = Locale.JAPAN
    private val mapper = DashboardSnapshotMapper(
        clock = fixedClock,
        zoneProvider = { zoneId },
        localeProvider = { locale }
    )

    @Test
    fun `map builds metrics with expected ordering and formatting`() {
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
                note = null,
                tags = emptyList()
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

        val snapshot = mapper.map(state)

        assertEquals(fixedClock.instant(), snapshot.generatedAt)
        assertEquals(zoneId, snapshot.timezone)
        assertEquals(locale, snapshot.locale)
        assertEquals(7, snapshot.metrics.size)
        assertEquals("overview", snapshot.metrics.first().key)
        assertEquals("latest_sleep", snapshot.metrics.last().key)

        val moodMetric = snapshot.metrics.first { it.key == "latest_mood" }
        assertEquals("最新の気分", moodMetric.title)
        assertEquals("7", moodMetric.value)
        assertTrue(moodMetric.description.startsWith("朝 3/10 09:15"))

        val sleepMetric = snapshot.metrics.first { it.key == "latest_sleep" }
        assertEquals("8h 30m", sleepMetric.value)
        assertEquals("3/9 22:00 - 06:30 (端末推定 / 良)", sleepMetric.description)
    }
}
