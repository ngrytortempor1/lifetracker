package com.lifetracker.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.core.repository.EventRepository
import com.lifetracker.core.repository.TaskRepository
import com.lifetracker.core.model.PomodoroTargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class PomodoroTimerViewModel(
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_FOCUS_MINUTES = 25
        private const val DEFAULT_BREAK_MINUTES = 5
    }

    private val _uiState = MutableStateFlow(
        PomodoroTimerUiState(
            focusDurationMinutes = DEFAULT_FOCUS_MINUTES,
            breakDurationMinutes = DEFAULT_BREAK_MINUTES,
            remainingSeconds = DEFAULT_FOCUS_MINUTES * 60
        )
    )
    val uiState: StateFlow<PomodoroTimerUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var focusStartedAt: Instant? = null

    init {
        initializeRepositories()
        observeTargets()
    }

    fun prepareTarget(type: PomodoroTargetType, id: String?) {
        _uiState.update {
            it.copy(
                selectedTargetType = type,
                selectedTargetId = if (type == PomodoroTargetType.NONE) null else id
            )
        }
    }

    private fun initializeRepositories() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { taskRepository.initialize() }
            runCatching { eventRepository.initialize() }
        }
    }

    private fun observeTargets() {
        viewModelScope.launch {
            combine(
                taskRepository.tasks,
                eventRepository.habits
            ) { tasks, habits ->
                PomodoroTargets(
                    tasks = tasks.map { PomodoroTarget(it.id, it.title) },
                    habits = habits
                        .filterNot { it.isArchived }
                        .map { PomodoroTarget(it.id, it.name) }
                )
            }.collect { targets ->
                _uiState.update { current ->
                    var next = current

                    if (!targetsEqual(current.availableTaskTargets, targets.tasks)) {
                        next = next.copy(availableTaskTargets = targets.tasks)
                        if (next.selectedTargetType == PomodoroTargetType.TASK &&
                            targets.tasks.none { it.id == next.selectedTargetId }
                        ) {
                            next = next.copy(selectedTargetId = null)
                        }
                    }

                    if (!targetsEqual(current.availableHabitTargets, targets.habits)) {
                        next = next.copy(availableHabitTargets = targets.habits)
                        if (next.selectedTargetType == PomodoroTargetType.HABIT &&
                            targets.habits.none { it.id == next.selectedTargetId }
                        ) {
                            next = next.copy(selectedTargetId = null)
                        }
                    }

                    next
                }
            }
        }
    }

    fun selectTargetType(type: PomodoroTargetType) {
        _uiState.update {
            it.copy(
                selectedTargetType = type,
                selectedTargetId = if (type == PomodoroTargetType.NONE) null else it.selectedTargetId
            )
        }
    }

    fun selectTargetId(id: String?) {
        _uiState.update { it.copy(selectedTargetId = id) }
    }

    fun selectFocusDuration(minutes: Int) {
        _uiState.update {
            val seconds = minutes.coerceAtLeast(1) * 60
            val updatedRemaining = if (it.phase == PomodoroPhase.IDLE) seconds else it.remainingSeconds
            it.copy(
                focusDurationMinutes = minutes.coerceAtLeast(1),
                remainingSeconds = updatedRemaining
            )
        }
    }

    fun selectBreakDuration(minutes: Int) {
        _uiState.update {
            it.copy(breakDurationMinutes = minutes.coerceAtLeast(1))
        }
    }

    fun startTimer() {
        val current = _uiState.value
        if (current.phase != PomodoroPhase.IDLE || current.isRunning) return

        val focusDurationSeconds = current.focusDurationMinutes * 60
        focusStartedAt = Instant.now()
        _uiState.update {
            it.copy(
                phase = PomodoroPhase.FOCUS,
                isRunning = true,
                isPaused = false,
                remainingSeconds = focusDurationSeconds
            )
        }
        startCountdown(PomodoroPhase.FOCUS)
    }

    fun pauseTimer() {
        val current = _uiState.value
        if (!current.isRunning || current.isPaused) return
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(isPaused = true, isRunning = false) }
    }

    fun resumeTimer() {
        val current = _uiState.value
        if (!current.isPaused) return
        _uiState.update { it.copy(isPaused = false, isRunning = true) }
        startCountdown(current.phase)
    }

    fun stopTimer() {
        countdownJob?.cancel()
        countdownJob = null
        focusStartedAt = null
        _uiState.update {
            it.copy(
                phase = PomodoroPhase.IDLE,
                isRunning = false,
                isPaused = false,
                remainingSeconds = it.focusDurationMinutes * 60
            )
        }
    }

    fun skipBreak() {
        val current = _uiState.value
        if (current.phase != PomodoroPhase.BREAK) return
        countdownJob?.cancel()
        countdownJob = null
        startIdlePhase()
    }

    private fun startCountdown(phase: PomodoroPhase) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val remaining = _uiState.value.remainingSeconds
                if (remaining <= 1) {
                    _uiState.update { it.copy(remainingSeconds = 0) }
                    break
                } else {
                    _uiState.update { it.copy(remainingSeconds = remaining - 1) }
                }
            }
            when (phase) {
                PomodoroPhase.FOCUS -> handleFocusCompleted()
                PomodoroPhase.BREAK -> handleBreakCompleted()
                PomodoroPhase.IDLE -> startIdlePhase()
            }
        }
    }

    private fun handleFocusCompleted() {
        viewModelScope.launch {
            val stateSnapshot = _uiState.value
            val focusDurationSeconds = stateSnapshot.focusDurationMinutes * 60
            val breakDurationSeconds = stateSnapshot.breakDurationMinutes * 60
            val startedAt = focusStartedAt ?: Instant.now().minusSeconds(focusDurationSeconds.toLong())
            val endedAt = Instant.now()

            runCatching {
                eventRepository.logPomodoroCompletion(
                    targetType = stateSnapshot.selectedTargetType,
                    targetId = stateSnapshot.selectedTargetId,
                    focusDurationSeconds = focusDurationSeconds,
                    breakDurationSeconds = breakDurationSeconds,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    interrupted = false
                )
            }

            _uiState.update {
                it.copy(
                    completedSessionsToday = it.completedSessionsToday + 1,
                    cycleCount = it.cycleCount + 1
                )
            }

            startBreakPhase()
        }
    }

    private fun handleBreakCompleted() {
        startIdlePhase()
    }

    private fun startBreakPhase() {
        focusStartedAt = null
        _uiState.update {
            it.copy(
                phase = PomodoroPhase.BREAK,
                isRunning = true,
                isPaused = false,
                remainingSeconds = it.breakDurationMinutes * 60
            )
        }
        startCountdown(PomodoroPhase.BREAK)
    }

    private fun startIdlePhase() {
        focusStartedAt = null
        _uiState.update {
            it.copy(
                phase = PomodoroPhase.IDLE,
                isRunning = false,
                isPaused = false,
                remainingSeconds = it.focusDurationMinutes * 60
            )
        }
    }

}

private data class PomodoroTargets(
    val tasks: List<PomodoroTarget>,
    val habits: List<PomodoroTarget>
)

data class PomodoroTarget(
    val id: String,
    val name: String
)

enum class PomodoroPhase {
    IDLE,
    FOCUS,
    BREAK
}

data class PomodoroTimerUiState(
    val focusDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val remainingSeconds: Int,
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val selectedTargetType: PomodoroTargetType = PomodoroTargetType.NONE,
    val selectedTargetId: String? = null,
    val availableTaskTargets: List<PomodoroTarget> = emptyList(),
    val availableHabitTargets: List<PomodoroTarget> = emptyList(),
    val completedSessionsToday: Int = 0,
    val cycleCount: Int = 0
)

private fun targetsEqual(a: List<PomodoroTarget>, b: List<PomodoroTarget>): Boolean {
    if (a.size != b.size) return false
    return a.zip(b).all { (first, second) -> first == second }
}
