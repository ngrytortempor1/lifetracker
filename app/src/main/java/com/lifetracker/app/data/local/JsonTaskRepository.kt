package com.lifetracker.app.data.local

import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskList
import com.lifetracker.core.model.TaskStep
import com.lifetracker.core.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * JSONL-backed task repository implementation.
 * Acts as a plugin that satisfies the core [TaskRepository] contract.
 */
class JsonTaskRepository(
    private val storage: LifeTrackerStorage
) : TaskRepository {

    // タスクリストのキャッシュ
    private val _taskLists = MutableStateFlow<List<TaskList>>(emptyList())
    override val taskLists: Flow<List<TaskList>> = _taskLists.asStateFlow()

    // タスクのキャッシュ
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    override val tasks: Flow<List<Task>> = _tasks.asStateFlow()

    override suspend fun initialize() {
        _taskLists.value = storage.readTaskLists()
        _tasks.value = storage.readTasks()

        if (_taskLists.value.isEmpty()) {
            createDefaultLists()
        }
    }

    override suspend fun addTaskList(list: TaskList) {
        val updated = _taskLists.value + list
        _taskLists.value = updated
        storage.saveTaskLists(updated)
    }

    override suspend fun addTask(task: Task) {
        val updated = _tasks.value + task
        _tasks.value = updated
        storage.saveTasks(updated)
    }

    override suspend fun updateTask(taskId: String, update: (Task) -> Task) {
        val updated = _tasks.value.map {
            if (it.id == taskId) update(it) else it
        }
        _tasks.value = updated
        storage.saveTasks(updated)
    }

    override suspend fun toggleTaskCompletion(taskId: String) {
        updateTask(taskId) { task ->
            task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) Instant.now().toString() else null
            )
        }
    }

    override suspend fun deleteTask(taskId: String) {
        val updated = _tasks.value.filterNot { it.id == taskId }
        _tasks.value = updated
        storage.saveTasks(updated)
    }

    override suspend fun toggleMyDay(taskId: String) {
        updateTask(taskId) { task ->
            task.copy(isInMyDay = !task.isInMyDay)
        }
    }

    override suspend fun toggleImportant(taskId: String) {
        updateTask(taskId) { task ->
            task.copy(isImportant = !task.isImportant)
        }
    }

    override suspend fun addStep(taskId: String, stepTitle: String) {
        updateTask(taskId) { task ->
            val newStep = TaskStep(
                id = "step-${System.currentTimeMillis()}",
                title = stepTitle,
                isCompleted = false
            )
            task.copy(steps = task.steps + newStep)
        }
    }

    override suspend fun toggleStep(taskId: String, stepId: String) {
        updateTask(taskId) { task ->
            val updatedSteps = task.steps.map { step ->
                if (step.id == stepId) step.copy(isCompleted = !step.isCompleted) else step
            }
            task.copy(steps = updatedSteps)
        }
    }

    override suspend fun setDueDate(taskId: String, dueDate: LocalDate?) {
        updateTask(taskId) { task ->
            task.copy(dueDate = dueDate?.toString())
        }
    }

    override suspend fun updateNotes(taskId: String, notes: String) {
        updateTask(taskId) { task ->
            task.copy(notes = notes.ifBlank { null })
        }
    }

    override suspend fun setReminder(taskId: String, reminderTime: String?) {
        updateTask(taskId) { task ->
            task.copy(reminderTime = reminderTime)
        }
    }

    override suspend fun updateTaskTitle(taskId: String, title: String) {
        updateTask(taskId) { task ->
            task.copy(title = title)
        }
    }

    override suspend fun addTaskList(name: String, icon: String, color: String) {
        withContext(Dispatchers.IO) {
            val lists = _taskLists.value.toMutableList()
            val newList = TaskList(
                id = "list_${System.currentTimeMillis()}",
                name = name,
                icon = icon,
                color = color,
                createdAt = Instant.now().toString(),
                sortOrder = lists.size
            )
            lists.add(newList)
            storage.saveTaskLists(lists)
            _taskLists.value = lists
        }
    }

    override suspend fun updateTaskList(listId: String, name: String, icon: String, color: String) {
        withContext(Dispatchers.IO) {
            val lists = _taskLists.value.toMutableList()
            val index = lists.indexOfFirst { it.id == listId }
            if (index >= 0) {
                lists[index] = lists[index].copy(
                    name = name,
                    icon = icon,
                    color = color
                )
                storage.saveTaskLists(lists)
                _taskLists.value = lists
            }
        }
    }

    override suspend fun deleteTaskList(listId: String) {
        withContext(Dispatchers.IO) {
            val tasks = _tasks.value.filter { it.listId != listId }
            storage.saveTasks(tasks)
            _tasks.value = tasks

            val lists = _taskLists.value.filter { it.id != listId }
            storage.saveTaskLists(lists)
            _taskLists.value = lists
        }
    }

    private suspend fun createDefaultLists() {
        val defaults = listOf(
            TaskList(
                id = "tasks",
                name = "タスク",
                icon = "📝",
                color = "#2196F3",
                createdAt = Instant.now().toString(),
                sortOrder = 0
            ),
            TaskList(
                id = "personal",
                name = "個人",
                icon = "👤",
                color = "#9C27B0",
                createdAt = Instant.now().toString(),
                sortOrder = 1
            ),
            TaskList(
                id = "work",
                name = "仕事",
                icon = "💼",
                color = "#FF9800",
                createdAt = Instant.now().toString(),
                sortOrder = 2
            )
        )
        _taskLists.value = defaults
        storage.saveTaskLists(defaults)
    }
}
