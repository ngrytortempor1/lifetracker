package com.lifetracker.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskCreationParams
import com.lifetracker.core.model.TaskList
import com.lifetracker.core.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * タスク管理画面のViewModel
 */
class TasksViewModel(
    private val repository: TaskRepository
) : ViewModel() {
    
    // 選択中のビュー
    private val _selectedView = MutableStateFlow(TaskView.MY_DAY)
    val selectedView: StateFlow<TaskView> = _selectedView.asStateFlow()
    
    // 選択中のタスクリスト
    private val _selectedListId = MutableStateFlow<String?>(null)
    
    // UI状態
    private val _uiState = MutableStateFlow<TasksUiState>(TasksUiState.Loading)
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()
    
    init {
        loadTasks()
    }
    
    /**
     * タスク読み込み
     */
    private fun loadTasks() {
        viewModelScope.launch {
            try {
                repository.initialize()
                
                // タスクリストとタスクの変更を監視
                combine(
                    repository.taskLists,
                    repository.tasks,
                    _selectedView,
                    _selectedListId
                ) { lists, tasks, view, listId ->
                    val filteredTasks = filterTasks(tasks, view, listId)
                    TasksUiState.Success(
                        taskLists = lists,
                        tasks = filteredTasks,
                        selectedView = view,
                        selectedListId = listId
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = TasksUiState.Error(e.message ?: "エラーが発生しました")
            }
        }
    }
    
    /**
     * タスクをフィルタリング
     */
    private fun filterTasks(tasks: List<Task>, view: TaskView, listId: String?): List<Task> {
        return when (view) {
            TaskView.MY_DAY -> tasks.filter { it.isInMyDay && !it.isCompleted }
            TaskView.IMPORTANT -> tasks.filter { it.isImportant && !it.isCompleted }
            TaskView.PLANNED -> tasks.filter { it.dueDate != null && !it.isCompleted }
            TaskView.ALL -> tasks.filterNot { it.isCompleted }
            TaskView.COMPLETED -> tasks.filter { it.isCompleted }
            TaskView.TASK_LIST -> tasks.filter { it.listId == listId && !it.isCompleted }
        }
    }
    
    /**
     * ビューを切り替え
     */
    fun selectView(view: TaskView, listId: String? = null) {
        _selectedView.value = view
        _selectedListId.value = listId
    }
    
    /**
     * タスクを追加
     */
    fun addTask(params: TaskCreationParams) {
        viewModelScope.launch {
            val task = Task(
                id = "task-${System.currentTimeMillis()}",
                listId = params.listId.ifBlank { "tasks" },
                title = params.title,
                isImportant = params.isImportant,
                isInMyDay = params.addToMyDay,
                dueDate = params.dueDate?.toString(),
                reminderTime = params.reminderTime?.toString(),
                repeatRule = params.repeatRule,
                repeatDetail = params.repeatDetail,
                createdAt = Instant.now().toString()
            )
            repository.addTask(task)
        }
    }
    
    /**
     * タスクの完了をトグル
     */
    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId)
        }
    }
    
    /**
     * タスクを削除
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }
    
    /**
     * 「マイデイ」に追加/削除
     */
    fun toggleMyDay(taskId: String) {
        viewModelScope.launch {
            repository.toggleMyDay(taskId)
        }
    }
    
    /**
     * 重要度をトグル
     */
    fun toggleImportant(taskId: String) {
        viewModelScope.launch {
            repository.toggleImportant(taskId)
        }
    }
    
    /**
     * サブタスクを追加
     */
    fun addStep(taskId: String, stepTitle: String) {
        viewModelScope.launch {
            repository.addStep(taskId, stepTitle)
        }
    }
    
    /**
     * サブタスクをトグル
     */
    fun toggleStep(taskId: String, stepId: String) {
        viewModelScope.launch {
            repository.toggleStep(taskId, stepId)
        }
    }
    
    /**
     * 期限を設定
     */
    fun setDueDate(taskId: String, dueDate: LocalDate?) {
        viewModelScope.launch {
            repository.setDueDate(taskId, dueDate)
        }
    }
    
    /**
     * メモを更新
     */
    fun updateNotes(taskId: String, notes: String) {
        viewModelScope.launch {
            repository.updateNotes(taskId, notes)
        }
    }
    
    /**
     * リマインダーを設定
     */
    fun setReminder(taskId: String, reminderTime: String?) {
        viewModelScope.launch {
            repository.setReminder(taskId, reminderTime)
        }
    }
    
    /**
     * タスクのタイトルを更新
     */
    fun updateTaskTitle(taskId: String, title: String) {
        viewModelScope.launch {
            repository.updateTaskTitle(taskId, title)
        }
    }
    
    /**
     * タスクリストを追加
     */
    fun addTaskList(name: String, icon: String, color: String) {
        viewModelScope.launch {
            repository.addTaskList(name, icon, color)
        }
    }
    
    /**
     * タスクリストを更新
     */
    fun updateTaskList(listId: String, name: String, icon: String, color: String) {
        viewModelScope.launch {
            repository.updateTaskList(listId, name, icon, color)
        }
    }
    
    /**
     * タスクリストを削除
     */
    fun deleteTaskList(listId: String) {
        viewModelScope.launch {
            repository.deleteTaskList(listId)
        }
    }
}

/**
 * タスクビューの種類
 */
enum class TaskView {
    MY_DAY,      // マイデイ
    IMPORTANT,   // 重要
    PLANNED,     // 計画済み
    ALL,         // すべて
    COMPLETED,   // 完了済み
    TASK_LIST    // 特定のリスト
}

/**
 * UI状態
 */
sealed class TasksUiState {
    object Loading : TasksUiState()
    data class Success(
        val taskLists: List<TaskList>,
        val tasks: List<Task>,
        val selectedView: TaskView,
        val selectedListId: String?
    ) : TasksUiState()
    data class Error(val message: String) : TasksUiState()
}
