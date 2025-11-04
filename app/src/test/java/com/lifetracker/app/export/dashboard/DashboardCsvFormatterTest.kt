package com.lifetracker.app.export.dashboard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class DashboardCsvFormatterTest {

    private val formatter = DashboardCsvFormatter()

    @Test
    fun `format returns csv with header and metadata rows`() {
        val snapshot = DashboardSnapshot(
            generatedAt = Instant.parse("2025-03-10T09:15:00Z"),
            timezone = ZoneId.of("UTC"),
            locale = Locale.JAPAN,
            metrics = listOf(
                DashboardMetric("overview", "今日の概要", "", "タスクと習慣の進捗をまとめて確認できます。"),
                DashboardMetric("my_day", "My Day", "3", "今日集中すべきタスク")
            )
        )

        val csv = formatter.format(snapshot).toString(Charsets.UTF_8)
        val rows = csv.trim().lines()

    assertTrue(rows.first().contains("metric_key"))
    assertTrue(rows[1].contains("\"overview\""))
    assertTrue(rows[2].contains("\"my_day\""))
        assertTrue(rows.any { it.contains("generated_at") })
        assertTrue(rows.any { it.contains("locale") })
    }
}
