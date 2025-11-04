package com.lifetracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskCreationParams
import com.lifetracker.core.model.TaskList
import com.lifetracker.app.ui.components.TaskCard
import com.lifetracker.app.ui.components.TaskCreationForm
import com.lifetracker.app.ui.components.StepItem
import com.lifetracker.app.viewmodel.TaskView
import com.lifetracker.app.viewmodel.TasksUiState
import com.lifetracker.app.viewmodel.TasksViewModel

/**
 * タスク管理メイン画面（Microsoft To Do風）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
    onStartPomodoro: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(getViewTitle(selectedView))
                },
                navigationIcon = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "メニュー")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "タスクを追加")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is TasksUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is TasksUiState.Success -> {
                    if (state.tasks.isEmpty()) {
                        EmptyTasksState(
                            view = selectedView,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        TasksList(
                            tasks = state.tasks,
                            onTaskClick = { task -> selectedTask = task },
                            onToggleComplete = { taskId -> viewModel.toggleTaskCompletion(taskId) },
                            onToggleImportant = { taskId -> viewModel.toggleImportant(taskId) },
                            onToggleMyDay = { taskId -> viewModel.toggleMyDay(taskId) },
                            onStartPomodoro = onStartPomodoro
                        )
                    }
                }
                
                is TasksUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
    
    // タスク追加ダイアログ
    if (showAddDialog) {
        val currentTaskLists = (uiState as? TasksUiState.Success)?.taskLists ?: emptyList()
        val currentSelectedListId = (uiState as? TasksUiState.Success)?.selectedListId
        AddTaskDialog(
            taskLists = currentTaskLists,
            selectedView = selectedView,
            selectedListId = currentSelectedListId,
            onDismiss = { showAddDialog = false },
            onAdd = { params ->
                viewModel.addTask(params)
                showAddDialog = false
            }
        )
    }
    
    // サイドバー（ビュー選択）
    if (showDrawer) {
        ViewDrawer(
            selectedView = selectedView,
            taskLists = (uiState as? TasksUiState.Success)?.taskLists ?: emptyList(),
            onSelectView = { view, listId ->
                viewModel.selectView(view, listId)
                showDrawer = false
            },
            onDismiss = { showDrawer = false }
        )
    }
    
    // タスク詳細シート
    selectedTask?.let { task ->
        TaskDetailSheet(
            task = task,
            onDismiss = { selectedTask = null },
            onAddStep = { title -> viewModel.addStep(task.id, title) },
            onToggleStep = { stepId -> viewModel.toggleStep(task.id, stepId) },
            onDelete = { 
                viewModel.deleteTask(task.id)
                selectedTask = null
            }
        )
    }
}

@Composable
private fun TasksList(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (String) -> Unit,
    onToggleImportant: (String) -> Unit,
    onToggleMyDay: (String) -> Unit,
    onStartPomodoro: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onToggleComplete = { onToggleComplete(task.id) },
                onToggleImportant = { onToggleImportant(task.id) },
                onToggleMyDay = { onToggleMyDay(task.id) },
                onClick = { onTaskClick(task) },
                onStartPomodoro = { onStartPomodoro(task.id) }
            )
        }
    }
}

@Composable
private fun EmptyTasksState(
    view: TaskView,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (view) {
                TaskView.MY_DAY -> "☀️"
                TaskView.IMPORTANT -> "⭐"
                TaskView.PLANNED -> "📅"
                TaskView.COMPLETED -> "✅"
                else -> "📝"
            },
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text = when (view) {
                TaskView.MY_DAY -> "マイデイにタスクがありません"
                TaskView.IMPORTANT -> "重要なタスクがありません"
                TaskView.PLANNED -> "予定されたタスクがありません"
                TaskView.COMPLETED -> "完了したタスクがありません"
                else -> "タスクがありません"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "右下の + ボタンでタスクを追加しましょう",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "エラー: $message",
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(16.dp)
    )
}

@Composable
private fun AddTaskDialog(
    taskLists: List<TaskList>,
    selectedView: TaskView,
    selectedListId: String?,
    onDismiss: () -> Unit,
    onAdd: (TaskCreationParams) -> Unit
) {
    val defaultListId = selectedListId
        ?: taskLists.firstOrNull()?.id
        ?: "tasks"
    val defaultMyDay = selectedView == TaskView.MY_DAY
    val defaultImportant = selectedView == TaskView.IMPORTANT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タスクを追加") },
        text = {
            TaskCreationForm(
                taskLists = taskLists,
                initialListId = defaultListId,
                defaultMyDay = defaultMyDay,
                defaultImportant = defaultImportant,
                onSubmit = onAdd,
                onCancel = onDismiss
            )
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewDrawer(
    selectedView: TaskView,
    taskLists: List<TaskList>,
    onSelectView: (TaskView, String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "LifeTracker",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 標準ビュー
            ViewItem(
                icon = Icons.Filled.WbSunny,
                title = "マイデイ",
                isSelected = selectedView == TaskView.MY_DAY,
                onClick = { onSelectView(TaskView.MY_DAY, null) }
            )
            ViewItem(
                icon = Icons.Filled.Star,
                title = "重要",
                isSelected = selectedView == TaskView.IMPORTANT,
                onClick = { onSelectView(TaskView.IMPORTANT, null) }
            )
            ViewItem(
                icon = Icons.Filled.CalendarToday,
                title = "計画済み",
                isSelected = selectedView == TaskView.PLANNED,
                onClick = { onSelectView(TaskView.PLANNED, null) }
            )
            ViewItem(
                icon = Icons.AutoMirrored.Filled.List,
                title = "すべて",
                isSelected = selectedView == TaskView.ALL,
                onClick = { onSelectView(TaskView.ALL, null) }
            )
            ViewItem(
                icon = Icons.Default.CheckCircle,
                title = "完了済み",
                isSelected = selectedView == TaskView.COMPLETED,
                onClick = { onSelectView(TaskView.COMPLETED, null) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // タスクリスト
            taskLists.forEach { list ->
                ViewItem(
                    icon = null,
                    title = "${list.icon} ${list.name}",
                    isSelected = selectedView == TaskView.TASK_LIST,
                    onClick = { onSelectView(TaskView.TASK_LIST, list.id) }
                )
            }
        }
    }
}

@Composable
private fun ViewItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) 
            MaterialTheme.colorScheme.secondaryContainer 
        else 
            Color.Transparent,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    onAddStep: (String) -> Unit,
    onToggleStep: (String) -> Unit,
    onDelete: () -> Unit
) {
    // TasksViewModelを親から受け取る必要があるため、一時的にローカル状態で管理
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newStepTitle by remember { mutableStateOf("") }
    var editedTitle by remember { mutableStateOf(task.title) }
    var editedNotes by remember { mutableStateOf(task.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // タイトル編集
            OutlinedTextField(
                value = editedTitle,
                onValueChange = { editedTitle = it },
                label = { Text("タスク名") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // メモ編集
            OutlinedTextField(
                value = editedNotes,
                onValueChange = { editedNotes = it },
                label = { Text("メモ") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                minLines = 3
            )
            
            HorizontalDivider()
            
            // 期限設定
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null)
                    Column {
                        Text("期限", style = MaterialTheme.typography.bodyMedium)
                        task.dueDate?.let { 
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (task.dueDate != null) {
                    IconButton(onClick = { /* 期限削除 */ }) {
                        Icon(Icons.Default.Clear, contentDescription = "期限を削除")
                    }
                }
            }
            
            HorizontalDivider()
            
            // マイデイ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Toggle MyDay */ }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null)
                    Text("マイデイに追加", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = task.isInMyDay,
                    onCheckedChange = { /* Toggle */ }
                )
            }
            
            HorizontalDivider()
            
            // サブタスク
            if (task.steps.isNotEmpty()) {
                Text(
                    text = "ステップ",
                    style = MaterialTheme.typography.titleSmall
                )
                task.steps.forEach { step ->
                    StepItem(
                        step = step,
                        onToggle = { onToggleStep(step.id) }
                    )
                }
            }
            
            // サブタスク追加
            OutlinedTextField(
                value = newStepTitle,
                onValueChange = { newStepTitle = it },
                label = { Text("ステップを追加") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (newStepTitle.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onAddStep(newStepTitle)
                                newStepTitle = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "追加")
                        }
                    }
                }
            )
            
            HorizontalDivider()
            
            // 削除ボタン
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("タスクを削除")
            }
        }
    }
    
    // 削除確認
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("タスクを削除") },
            text = { Text("このタスクを削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

private fun getViewTitle(view: TaskView): String {
    return when (view) {
        TaskView.MY_DAY -> "マイデイ"
        TaskView.IMPORTANT -> "重要"
        TaskView.PLANNED -> "計画済み"
        TaskView.ALL -> "すべて"
        TaskView.COMPLETED -> "完了済み"
        TaskView.TASK_LIST -> "タスク"
    }
}
