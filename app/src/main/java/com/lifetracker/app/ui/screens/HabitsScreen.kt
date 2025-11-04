package com.lifetracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifetracker.app.ui.components.HabitCard
import com.lifetracker.app.viewmodel.HabitsUiState
import com.lifetracker.app.viewmodel.HabitsViewModel

/**
 * 習慣トラッキングメイン画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel,
    modifier: Modifier = Modifier,
    onStartPomodoro: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日の習慣") },
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
                Icon(Icons.Default.Add, contentDescription = "習慣を追加")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is HabitsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is HabitsUiState.Success -> {
                    if (state.habits.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        HabitsList(
                            habits = state.habits,
                            completedIds = state.completedHabitIds,
                            onToggle = { habitId ->
                                viewModel.toggleHabitCompletion(habitId)
                            },
                            onStartPomodoro = onStartPomodoro
                        )
                    }
                    
                    // 完了数の表示
                    CompletionSummary(
                        completed = state.completedHabitIds.size,
                        total = state.habits.size,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
                
                is HabitsUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
    
    // 習慣追加ダイアログ
    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, icon ->
                viewModel.addHabit(name, icon)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun HabitsList(
    habits: List<com.lifetracker.core.model.Habit>,
    completedIds: Set<String>,
    onToggle: (String) -> Unit,
    onStartPomodoro: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                isCompleted = habit.id in completedIds,
                onToggle = { onToggle(habit.id) },
                onStartPomodoro = { onStartPomodoro(habit.id) }
            )
        }
    }
}

@Composable
private fun CompletionSummary(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = "今日の達成: $completed / $total",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "習慣がありません",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "右下の + ボタンで習慣を追加しましょう",
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
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var habitName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("✓") }
    
    val iconOptions = listOf("✓", "🏃", "📚", "🧘", "💪", "🎯", "💤", "🍎", "💧", "🚴")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しい習慣") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = habitName,
                    onValueChange = { habitName = it },
                    label = { Text("習慣名") },
                    singleLine = true
                )
                
                Text("アイコンを選択", style = MaterialTheme.typography.labelMedium)
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    iconOptions.take(5).forEach { icon ->
                        IconButton(
                            onClick = { selectedIcon = icon },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (icon == selectedIcon) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(habitName, selectedIcon) },
                enabled = habitName.isNotBlank()
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
