package com.lifetracker.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.core.repository.EventRepository
import com.lifetracker.core.model.Habit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 習慣トラッキング画面のViewModel
 */
class HabitsViewModel(
    private val repository: EventRepository
) : ViewModel() {
    
    // UI状態
    private val _uiState = MutableStateFlow<HabitsUiState>(HabitsUiState.Loading)
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()
    
    init {
        loadHabits()
    }
    
    /**
     * 習慣一覧と今日の完了状況を読み込み
     */
    private fun loadHabits() {
        viewModelScope.launch {
            try {
                repository.initialize()
                
                val habits = repository.habits.first()
                    .filterNot { it.isArchived }
                
                val completedIds = repository.getTodayCompletedHabits()
                
                _uiState.value = HabitsUiState.Success(
                    habits = habits,
                    completedHabitIds = completedIds
                )
            } catch (e: Exception) {
                _uiState.value = HabitsUiState.Error(e.message ?: "エラーが発生しました")
            }
        }
    }
    
    /**
     * 習慣完了をトグル（チェック/未チェック）
     */
    fun toggleHabitCompletion(habitId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value as? HabitsUiState.Success ?: return@launch
            
            if (habitId in currentState.completedHabitIds) {
                // すでに完了している場合は何もしない（Phase 1では削除機能なし）
                return@launch
            }
            
            // イベント記録
            repository.logHabitCompletion(habitId)
            
            // UI更新
            _uiState.value = currentState.copy(
                completedHabitIds = currentState.completedHabitIds + habitId
            )
        }
    }
    
    /**
     * 習慣を追加
     */
    fun addHabit(name: String, icon: String = "✓", color: String = "#6200EE") {
        viewModelScope.launch {
            val habit = Habit(
                id = "habit-${System.currentTimeMillis()}",
                name = name,
                icon = icon,
                color = color,
                createdAt = java.time.Instant.now().toString()
            )
            
            repository.addHabit(habit)
            loadHabits() // 再読み込み
        }
    }
}

/**
 * UI状態の定義
 */
sealed class HabitsUiState {
    object Loading : HabitsUiState()
    data class Success(
        val habits: List<Habit>,
        val completedHabitIds: Set<String>
    ) : HabitsUiState()
    data class Error(val message: String) : HabitsUiState()
}
