package com.lifetracker.app.ui.quickadd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.LifeTrackerApplication
import com.lifetracker.core.model.TaskCreationParams
import com.lifetracker.app.ui.components.TaskCreationForm
import com.lifetracker.app.ui.theme.LifeTrackerTheme
import com.lifetracker.app.viewmodel.TaskView
import com.lifetracker.app.viewmodel.TasksUiState
import com.lifetracker.app.viewmodel.TasksViewModel

class TaskQuickAddActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as LifeTrackerApplication).appContainer
        val taskRepository = appContainer.taskRepository

        setContent {
            LifeTrackerTheme {
                val viewModel = viewModel<TasksViewModel> {
                    TasksViewModel(taskRepository)
                }
                TaskQuickAddScreen(
                viewModel = viewModel,
                onAdded = { finish() },
                onCancel = { finish() }
            )
            }
        }
    }
}

@Composable
private fun TaskQuickAddScreen(
    viewModel: TasksViewModel,
    onAdded: () -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()

    val taskLists = when (val state = uiState) {
        is TasksUiState.Success -> state.taskLists
        else -> emptyList()
    }
    val defaultListId = when (val state = uiState) {
        is TasksUiState.Success -> state.selectedListId ?: taskLists.firstOrNull()?.id ?: "tasks"
        else -> taskLists.firstOrNull()?.id ?: "tasks"
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        TaskCreationForm(
            taskLists = taskLists,
            initialListId = defaultListId,
            defaultMyDay = selectedView == TaskView.MY_DAY,
            defaultImportant = selectedView == TaskView.IMPORTANT,
            onSubmit = { params: TaskCreationParams ->
                viewModel.addTask(params)
                android.widget.Toast.makeText(context, "タスクを追加しました", android.widget.Toast.LENGTH_SHORT).show()
                onAdded()
            },
            onCancel = onCancel,
            submitLabel = "追加"
        )
    }
}
