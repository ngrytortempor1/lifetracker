package com.lifetracker.app.export.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardPdfFormatterInstrumentedTest {

    private val formatter = DashboardPdfFormatter()

    @Test
    fun format_producesPdfBytes() {
        val snapshot = DashboardSnapshot(
            generatedAt = Instant.parse("2025-03-10T09:15:00Z"),
            timezone = ZoneId.of("UTC"),
            locale = Locale.JAPAN,
            metrics = listOf(
                DashboardMetric("overview", "今日の概要", "", "タスクと習慣の進捗をまとめて確認できます。"),
                DashboardMetric("my_day", "My Day", "3", "今日集中すべきタスク")
            )
        )

        val bytes = formatter.format(snapshot)
        val header = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
        val footer = bytes.takeLast(5).toByteArray().toString(Charsets.US_ASCII)

        assertTrue(bytes.isNotEmpty())
        assertTrue(header.startsWith("%PDF"))
        assertTrue(footer.contains("%%EOF"))
    }
}
