package com.lifetracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetracker.core.model.PomodoroTargetType
import com.lifetracker.app.viewmodel.PomodoroPhase
import com.lifetracker.app.viewmodel.PomodoroTarget
import com.lifetracker.app.viewmodel.PomodoroTimerUiState
import com.lifetracker.app.viewmodel.PomodoroTimerViewModel

@Composable
fun PomodoroTimerScreen(
    viewModel: PomodoroTimerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimerHeader(uiState = uiState)
            TargetSelector(
                uiState = uiState,
                onTargetTypeSelected = viewModel::selectTargetType,
                onTargetSelected = viewModel::selectTargetId
            )
            DurationSelector(
                focusMinutes = uiState.focusDurationMinutes,
                breakMinutes = uiState.breakDurationMinutes,
                onFocusSelected = viewModel::selectFocusDuration,
                onBreakSelected = viewModel::selectBreakDuration
            )
            TimerActions(
                uiState = uiState,
                onStart = viewModel::startTimer,
                onPause = viewModel::pauseTimer,
                onResume = viewModel::resumeTimer,
                onStop = viewModel::stopTimer,
                onSkipBreak = viewModel::skipBreak
            )
            SessionSummary(uiState = uiState)
        }
    }
}

@Composable
private fun TimerHeader(uiState: PomodoroTimerUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when (uiState.phase) {
                    PomodoroPhase.IDLE -> "待機中"
                    PomodoroPhase.FOCUS -> if (uiState.isPaused) "集中 (一時停止中)" else "集中モード"
                    PomodoroPhase.BREAK -> if (uiState.isPaused) "休憩 (一時停止中)" else "休憩モード"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = formatSeconds(uiState.remainingSeconds),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            Text(
                text = buildString {
                    append("集中: ${uiState.focusDurationMinutes}分 / 休憩: ${uiState.breakDurationMinutes}分")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TargetSelector(
    uiState: PomodoroTimerUiState,
    onTargetTypeSelected: (PomodoroTargetType) -> Unit,
    onTargetSelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "対象",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PomodoroTargetType.values().forEach { type ->
                FilterChip(
                    selected = uiState.selectedTargetType == type,
                    onClick = { onTargetTypeSelected(type) },
                    label = {
                        Text(
                            text = when (type) {
                                PomodoroTargetType.NONE -> "未選択"
                                PomodoroTargetType.TASK -> "タスク"
                                PomodoroTargetType.HABIT -> "習慣"
                            }
                        )
                    }
                )
            }
        }

        when (uiState.selectedTargetType) {
            PomodoroTargetType.TASK -> TargetDropdown(
                title = "対象タスク",
                items = uiState.availableTaskTargets,
                selectedId = uiState.selectedTargetId,
                onSelected = onTargetSelected
            )

            PomodoroTargetType.HABIT -> TargetDropdown(
                title = "対象習慣",
                items = uiState.availableHabitTargets,
                selectedId = uiState.selectedTargetId,
                onSelected = onTargetSelected
            )

            else -> Unit
        }
    }
}

@Composable
private fun TargetDropdown(
    title: String,
    items: List<PomodoroTarget>,
    selectedId: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.firstOrNull { it.id == selectedId }?.name ?: "選択してください"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = !expanded }
        ) {
            Text(
                text = selectedName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column {
                    items.forEach { target ->
                        Text(
                            text = target.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(target.id)
                                    expanded = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (target.id == selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = "未選択に戻す",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(null)
                                expanded = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationSelector(
    focusMinutes: Int,
    breakMinutes: Int,
    onFocusSelected: (Int) -> Unit,
    onBreakSelected: (Int) -> Unit
) {
    val focusOptions = listOf(15, 20, 25, 30, 45)
    val breakOptions = listOf(3, 5, 10, 15)
    var showFocusCustomDialog by remember { mutableStateOf(false) }
    var showBreakCustomDialog by remember { mutableStateOf(false) }
    var customFocusInput by remember { mutableStateOf(focusMinutes.toString()) }
    var customBreakInput by remember { mutableStateOf(breakMinutes.toString()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "時間設定", style = MaterialTheme.typography.titleMedium)

        Text(text = "集中時間", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            focusOptions.forEach { minutes ->
                FilterChip(
                    selected = focusMinutes == minutes,
                    onClick = { onFocusSelected(minutes) },
                    label = { Text("${minutes}分") }
                )
            }
            FilterChip(
                selected = focusMinutes !in focusOptions,
                onClick = {
                    customFocusInput = focusMinutes.toString()
                    showFocusCustomDialog = true
                },
                label = { Text("カスタム") }
            )
        }

        Text(text = "休憩時間", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            breakOptions.forEach { minutes ->
                FilterChip(
                    selected = breakMinutes == minutes,
                    onClick = { onBreakSelected(minutes) },
                    label = { Text("${minutes}分") }
                )
            }
            FilterChip(
                selected = breakMinutes !in breakOptions,
                onClick = {
                    customBreakInput = breakMinutes.toString()
                    showBreakCustomDialog = true
                },
                label = { Text("カスタム") }
            )
        }

        if (showFocusCustomDialog) {
            CustomDurationDialog(
                title = "集中時間を設定",
                inputValue = customFocusInput,
                onInputChange = { customFocusInput = it },
                onConfirm = {
                    customFocusInput.toIntOrNull()?.let { minutes ->
                        onFocusSelected(minutes.coerceAtLeast(1))
                    }
                    showFocusCustomDialog = false
                },
                onDismiss = { showFocusCustomDialog = false }
            )
        }

        if (showBreakCustomDialog) {
            CustomDurationDialog(
                title = "休憩時間を設定",
                inputValue = customBreakInput,
                onInputChange = { customBreakInput = it },
                onConfirm = {
                    customBreakInput.toIntOrNull()?.let { minutes ->
                        onBreakSelected(minutes.coerceAtLeast(1))
                    }
                    showBreakCustomDialog = false
                },
                onDismiss = { showBreakCustomDialog = false }
            )
        }
    }
}

@Composable
private fun CustomDurationDialog(
    title: String,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("分単位で入力してください")
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    suffix = { Text("分") },
                    singleLine = true
                )
            }
        }
    )
}

@Composable
private fun TimerActions(
    uiState: PomodoroTimerUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkipBreak: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.phase == PomodoroPhase.IDLE) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRunning
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("開始")
                }
            } else {
                if (uiState.isPaused) {
                    ElevatedButton(
                        onClick = onResume,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("再開")
                    }
                } else {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("一時停止")
                    }
                }

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("停止")
                }
            }
        }

        if (uiState.phase == PomodoroPhase.BREAK) {
            OutlinedButton(
                onClick = onSkipBreak,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("休憩をスキップ")
            }
        }
    }
}

@Composable
private fun SessionSummary(uiState: PomodoroTimerUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "セッション概要",
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider()
            Text(
                text = "今日の完了数: ${uiState.completedSessionsToday}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "累積サイクル: ${uiState.cycleCount}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
