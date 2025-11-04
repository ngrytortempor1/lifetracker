package com.lifetracker.app.export.dashboard

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class DashboardSnapshot(
    val generatedAt: Instant,
    val timezone: ZoneId,
    val locale: Locale,
    val metrics: List<DashboardMetric>
)

data class DashboardMetric(
    val key: String,
    val title: String,
    val value: String,
    val description: String
)
