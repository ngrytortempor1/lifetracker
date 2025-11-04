package com.lifetracker.app.export.dashboard

import com.lifetracker.app.dashboard.DashboardMetricFormatter
import com.lifetracker.app.viewmodel.DashboardUiState
import java.time.Clock
import java.time.ZoneId
import java.util.Locale

class DashboardSnapshotMapper(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val localeProvider: () -> Locale = { Locale.getDefault() }
) {
    fun map(state: DashboardUiState.Ready): DashboardSnapshot {
        val zone = zoneProvider()
        val locale = localeProvider()
        val metrics = buildList {
            add(
                DashboardMetric(
                    key = "overview",
                    title = "今日の概要",
                    value = "",
                    description = "タスクと習慣の進捗をまとめて確認できます。"
                )
            )
            add(
                DashboardMetric(
                    key = "my_day",
                    title = "My Day",
                    value = state.myDayCount.toString(),
                    description = "今日集中すべきタスク"
                )
            )
            add(
                DashboardMetric(
                    key = "due",
                    title = "期限 (今日/遅延)",
                    value = "${state.todayDueCount} / ${state.overdueCount}",
                    description = "今日の期限と期限切れタスク"
                )
            )
            add(
                DashboardMetric(
                    key = "completed_today",
                    title = "完了タスク (今日)",
                    value = state.completedTodayCount.toString(),
                    description = "今日完了済みのタスク数"
                )
            )
            add(
                DashboardMetric(
                    key = "habits",
                    title = "習慣達成",
                    value = "${state.habitCompleted} / ${state.habitTotal}",
                    description = "今日の習慣達成状況"
                )
            )

            state.latestMood?.let { mood ->
                add(
                    DashboardMetric(
                        key = "latest_mood",
                        title = "最新の気分",
                        value = mood.score.toString(),
                        description = DashboardMetricFormatter.moodDescription(mood, zone)
                    )
                )
            }

            state.lastSleep?.let { sleep ->
                add(
                    DashboardMetric(
                        key = "latest_sleep",
                        title = "最新の睡眠",
                        value = DashboardMetricFormatter.sleepDurationLabel(sleep),
                        description = DashboardMetricFormatter.sleepDescription(sleep, zone)
                    )
                )
            }
        }

        return DashboardSnapshot(
            generatedAt = clock.instant(),
            timezone = zone,
            locale = locale,
            metrics = metrics
        )
    }
}
