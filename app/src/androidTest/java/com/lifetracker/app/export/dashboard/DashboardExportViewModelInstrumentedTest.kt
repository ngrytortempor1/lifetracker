package com.lifetracker.app.export.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifetracker.app.viewmodel.DashboardUiState
import com.lifetracker.app.viewmodel.MoodSummary
import com.lifetracker.app.viewmodel.SleepSummary
import com.lifetracker.core.model.MoodSlot
import com.lifetracker.core.model.SleepQuality
import com.lifetracker.core.model.SleepSessionSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DashboardExportViewModelInstrumentedTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scope: TestScope

    private val snapshotMapper = DashboardSnapshotMapper(
        clock = Clock.fixed(Instant.parse("2025-03-10T09:15:00Z"), ZoneId.of("UTC")),
        zoneProvider = { ZoneId.of("UTC") },
        localeProvider = { Locale.JAPAN }
    )
    private val csvFormatter = DashboardCsvFormatter()
    private val pdfFormatter = DashboardPdfFormatter()
    private lateinit var repository: DashboardExportRepository
    private lateinit var viewModel: DashboardExportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scope = TestScope(testDispatcher)
        repository = DashboardExportRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        viewModel = DashboardExportViewModel(
            snapshotMapper,
            csvFormatter,
            pdfFormatter,
            repository,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportCsv_emitsShareEventWithExpectedMetadata() = scope.runTest {
        val readyState = sampleState()

        viewModel.export(readyState, DashboardExportFormat.CSV)

        val event = viewModel.shareEvents.first()
        val share = event as DashboardExportEvent.Share
        assertEquals("text/csv", share.mimeType)
        assertTrue(share.fileName.endsWith(".csv"))

        val inputStream = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .contentResolver
            .openInputStream(share.uri)
        assertNotNull(inputStream)
        inputStream!!.use { stream ->
            val body = stream.readBytes().toString(Charsets.UTF_8)
            assertTrue(body.contains("my_day"))
        }
    }

    private fun sampleState(): DashboardUiState.Ready = DashboardUiState.Ready(
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
}
