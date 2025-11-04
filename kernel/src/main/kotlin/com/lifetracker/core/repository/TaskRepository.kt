package com.lifetracker.core.repository

import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskList
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Core task repository contract.
 * Plugins can provide their own implementations (JSONL, database, cloud, etc.).
 */
interface TaskRepository {
    val taskLists: Flow<List<TaskList>>
    val tasks: Flow<List<Task>>

    suspend fun initialize()

    suspend fun addTaskList(list: TaskList)
    suspend fun addTask(task: Task)
    suspend fun updateTask(taskId: String, update: (Task) -> Task)
    suspend fun toggleTaskCompletion(taskId: String)
    suspend fun deleteTask(taskId: String)
    suspend fun toggleMyDay(taskId: String)
    suspend fun toggleImportant(taskId: String)
    suspend fun addStep(taskId: String, stepTitle: String)
    suspend fun toggleStep(taskId: String, stepId: String)
    suspend fun setDueDate(taskId: String, dueDate: LocalDate?)
    suspend fun updateNotes(taskId: String, notes: String)
    suspend fun setReminder(taskId: String, reminderTime: String?)
    suspend fun updateTaskTitle(taskId: String, title: String)
    suspend fun addTaskList(name: String, icon: String, color: String)
    suspend fun updateTaskList(listId: String, name: String, icon: String, color: String)
    suspend fun deleteTaskList(listId: String)
}
