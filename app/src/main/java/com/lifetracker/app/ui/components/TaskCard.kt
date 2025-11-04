package com.lifetracker.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskStep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * タスクカード（Microsoft To Do風）
 */
@Composable
fun TaskCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onToggleMyDay: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onStartPomodoro: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 完了チェックボックス
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (task.isCompleted) "完了" else "未完了",
                    tint = if (task.isCompleted) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // タスク情報
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // タイトル
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) 
                        TextDecoration.LineThrough 
                    else 
                        null,
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                
                // メタ情報（期限、サブタスク）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 期限
                    task.dueDate?.let { dueDate ->
                        DueDateChip(dueDate)
                    }

                    // リマインダー
                    task.reminderTime?.let { reminder ->
                        ReminderChip(reminder)
                    }
                    
                    // サブタスク進捗
                    if (task.steps.isNotEmpty()) {
                        val completedSteps = task.steps.count { it.isCompleted }
                        Text(
                            text = "$completedSteps/${task.steps.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // マイデイマーク
                    if (task.isInMyDay) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = "マイデイ",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // 重要マーク
            onStartPomodoro?.let {
                IconButton(
                    onClick = it,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "ポモドーロを開始",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onToggleImportant,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (task.isImportant) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (task.isImportant) "重要" else "重要でない",
                    tint = if (task.isImportant)
                        Color(0xFFFF9800)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 期限表示チップ
 */
@Composable
private fun DueDateChip(dueDate: String) {
    val date = LocalDate.parse(dueDate)
    val today = LocalDate.now()
    
    val (text, color) = when {
        date.isBefore(today) -> "期限切れ" to Color(0xFFF44336)
        date.isEqual(today) -> "今日" to Color(0xFF2196F3)
        date.isEqual(today.plusDays(1)) -> "明日" to Color(0xFF4CAF50)
        else -> date.format(DateTimeFormatter.ofPattern("M/d")) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ReminderChip(reminderTime: String) {
    val instant = runCatching { Instant.parse(reminderTime) }.getOrNull() ?: return
    val zoned = instant.atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val label = if (zoned.toLocalDate() == today) {
        "通知 ${zoned.toLocalTime().format(timeFormatter)}"
    } else {
        "通知 ${zoned.format(dateFormatter)}"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * サブタスク（ステップ）アイテム
 */
@Composable
fun StepItem(
    step: TaskStep,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (step.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (step.isCompleted)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (step.isCompleted) TextDecoration.LineThrough else null,
            color = if (step.isCompleted)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}
