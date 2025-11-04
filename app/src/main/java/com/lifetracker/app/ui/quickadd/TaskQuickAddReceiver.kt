package com.lifetracker.app.ui.quickadd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.lifetracker.app.LifeTrackerApplication
import com.lifetracker.app.util.NaturalLanguageParser
import com.lifetracker.core.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

/**
 *  通知上のインライン入力でタスクを追加するレシーバー。
 *  UI を開かずにクイック追加できるようにする。
 */
class TaskQuickAddReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INLINE_ADD = "com.lifetracker.app.ui.quickadd.ACTION_INLINE_ADD"
        const val REMOTE_INPUT_KEY = "quick_add_inline_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INLINE_ADD) return

        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val rawInput = results.getCharSequence(REMOTE_INPUT_KEY)?.toString()?.trim().orEmpty()
        if (rawInput.isBlank()) return

        val pendingResult = goAsync()

        val application = context.applicationContext as? LifeTrackerApplication
        if (application == null) {
            pendingResult.finish()
            return
        }

        val repository = application.appContainer.taskRepository

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.initialize()

                val lists = repository.taskLists.firstOrNull().orEmpty()
                val defaultListId = lists.firstOrNull()?.id ?: "tasks"

                val parsed = NaturalLanguageParser.parse(rawInput, ZonedDateTime.now())
                val finalTitle = parsed.cleanedTitle.ifBlank { rawInput }

                val task = Task(
                    id = "task-${System.currentTimeMillis()}",
                    listId = defaultListId,
                    title = finalTitle,
                    isImportant = false,
                    isInMyDay = false,
                    dueDate = parsed.dueDate?.toString(),
                    reminderTime = parsed.reminderTime?.toString(),
                    createdAt = Instant.now().toString()
                )

                repository.addTask(task)

                TaskQuickAddNotifier.showQuickAddNotification(
                    context = context,
                    lastAddedTitle = finalTitle
                )
            } catch (_: Exception) {
                // 失敗しても通知自体は再掲しておく
                TaskQuickAddNotifier.showQuickAddNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
