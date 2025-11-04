package com.lifetracker.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.core.model.MoodEntry
import com.lifetracker.core.model.MoodSlot
import com.lifetracker.core.model.SleepQuality
import com.lifetracker.core.model.SleepSession
import com.lifetracker.core.model.SleepSessionSource
import com.lifetracker.core.model.Task
import com.lifetracker.core.repository.EventRepository
import com.lifetracker.core.repository.TaskRepository
import com.lifetracker.core.repository.WellnessRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration

/**
 * ホームダッシュボード用の集計ViewModel。
 * 既存リポジトリから軽量なメタ情報だけを組み合わせ、起動をブロックしないよう注意している。
 */
class DashboardViewModel(
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository,
    private val wellnessRepository: WellnessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            try {
                // リポジトリ初期化はIOディスパッチャで実行し、UIスレッドをブロックしない
                withContext(Dispatchers.IO) {
                    taskRepository.initialize()
                    eventRepository.initialize()
                    wellnessRepository.initialize()
                }

                combine(
                    taskRepository.tasks,
                    eventRepository.habits,
                    wellnessRepository.moodEntries,
                    wellnessRepository.sleepSessions
                ) { tasks, habits, moods, sleeps ->
                    val today = LocalDate.now()
                    val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

                    val myDay = tasks.count { it.isInMyDay && !it.isCompleted }
                    val dueToday = tasks.count { it.dueDate?.let { LocalDate.parse(it) == today } == true && !it.isCompleted }
                    val overdue = tasks.count { it.dueDate?.let { LocalDate.parse(it) < today } == true && !it.isCompleted }
                    val completedToday = tasks.count { completedToday(it, todayStart, todayEnd) }

                    val completedHabits = runCatching {
                        eventRepository.getTodayCompletedHabits()
                    }.getOrElse { emptySet() }

                    val latestMood = moods.maxByOrNull { it.recordedAt.parseInstantOrNull() ?: Instant.EPOCH }
                        ?.let { entry -> entry.toSummary() }

                    val lastSleep = sleeps.maxByOrNull { it.endedAt.parseInstantOrNull() ?: Instant.EPOCH }
                        ?.let { session -> session.toSummary() }

                    DashboardUiState.Ready(
                        myDayCount = myDay,
                        todayDueCount = dueToday,
                        overdueCount = overdue,
                        completedTodayCount = completedToday,
                        habitTotal = habits.count { !it.isArchived },
                        habitCompleted = completedHabits.size,
                        latestMood = latestMood,
                        lastSleep = lastSleep
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ex: Exception) {
                _uiState.value = DashboardUiState.Error(ex.message ?: "読み込みに失敗しました")
            }
        }
    }

    private fun completedToday(task: Task, start: Instant, end: Instant): Boolean {
        val completedAt = task.completedAt ?: return false
        return runCatching {
            val instant = Instant.parse(completedAt)
            (instant == start || instant.isAfter(start)) && instant.isBefore(end)
        }.getOrDefault(false)
    }

    private fun String?.parseInstantOrNull(): Instant? = this?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    }

    private fun MoodEntry.toSummary(): MoodSummary {
        val recorded = recordedAt.parseInstantOrNull() ?: Instant.EPOCH
        return MoodSummary(
            slot = slot,
            score = score,
            recordedAt = recorded,
            note = note,
            tags = tags
        )
    }

    private fun SleepSession.toSummary(): SleepSummary {
        val start = startedAt.parseInstantOrNull() ?: Instant.EPOCH
        val end = endedAt.parseInstantOrNull() ?: start
        val rawDuration = runCatching { Duration.between(start, end) }.getOrDefault(Duration.ZERO)
        val duration = if (rawDuration.isNegative) Duration.ZERO else rawDuration
        return SleepSummary(
            startedAt = start,
            endedAt = end,
            duration = duration,
            source = source,
            quality = quality,
            note = note
        )
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Ready(
        val myDayCount: Int,
        val todayDueCount: Int,
        val overdueCount: Int,
        val completedTodayCount: Int,
        val habitTotal: Int,
        val habitCompleted: Int,
        val latestMood: MoodSummary?,
        val lastSleep: SleepSummary?
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

data class MoodSummary(
    val slot: MoodSlot,
    val score: Int,
    val recordedAt: Instant,
    val note: String?,
    val tags: List<String>
)

data class SleepSummary(
    val startedAt: Instant,
    val endedAt: Instant,
    val duration: Duration,
    val source: SleepSessionSource,
    val quality: SleepQuality?,
    val note: String?
)
