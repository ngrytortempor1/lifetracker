package com.lifetracker.app.dashboard

import com.lifetracker.app.viewmodel.MoodSummary
import com.lifetracker.app.viewmodel.SleepSummary
import com.lifetracker.core.model.MoodSlot
import com.lifetracker.core.model.SleepQuality
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DashboardMetricFormatter {
    private val moodFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
    private val sleepStartFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
    private val sleepEndFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun moodDescription(summary: MoodSummary, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val slotLabel = when (summary.slot) {
            MoodSlot.MORNING -> "朝"
            MoodSlot.NOON -> "昼"
            MoodSlot.NIGHT -> "夜"
        }
        val formattedTime = summary.recordedAt.atZone(zoneId).format(moodFormatter)
        return "$slotLabel $formattedTime"
    }

    fun sleepDurationLabel(summary: SleepSummary): String {
        val hours = summary.duration.toHours()
        val minutes = summary.duration.toMinutes() - (hours * 60)
        return buildString {
            append(hours)
            append("h ")
            append(minutes)
            append("m")
        }
    }

    fun sleepDescription(
        summary: SleepSummary,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val start = summary.startedAt.atZone(zoneId).format(sleepStartFormatter)
        val end = summary.endedAt.atZone(zoneId).format(sleepEndFormatter)
        val source = when (summary.source) {
            com.lifetracker.core.model.SleepSessionSource.MANUAL -> "手動"
            com.lifetracker.core.model.SleepSessionSource.DEVICE_USAGE -> "端末推定"
            com.lifetracker.core.model.SleepSessionSource.HEALTH_CONNECT -> "Health Connect"
        }
        val quality = summary.quality?.let { " / ${qualityLabel(it)}" } ?: ""
        return "$start - $end ($source$quality)"
    }

    private fun qualityLabel(quality: SleepQuality): String = when (quality) {
        SleepQuality.POOR -> "低"
        SleepQuality.OKAY -> "普通"
        SleepQuality.GOOD -> "良"
    }
}
