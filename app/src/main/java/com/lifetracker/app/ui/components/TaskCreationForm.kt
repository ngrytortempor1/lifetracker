package com.lifetracker.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifetracker.core.model.TaskCreationParams
import com.lifetracker.core.model.TaskList
import com.lifetracker.core.model.TaskRepeatRule
import com.lifetracker.app.util.NaturalLanguageParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.collections.buildList

private enum class CreationSection { ATTRIBUTES, DUE, REMINDER, REPEAT }

private data class QuickOption<T>(val label: String, val value: T)

/**
 * タスク作成フォーム。Microsoft To Do のようにコンパクトで階層化された UI。
 * 上段のチップでセクションを展開し、必要な時だけ詳細を表示します。
 */
@Composable
fun TaskCreationForm(
    taskLists: List<TaskList>,
    initialListId: String,
    defaultMyDay: Boolean,
    defaultImportant: Boolean,
    onSubmit: (TaskCreationParams) -> Unit,
    onCancel: (() -> Unit)? = null,
    submitLabel: String = "追加"
) {
    val fallbackListId = taskLists.firstOrNull()?.id ?: "tasks"
    var title by remember { mutableStateOf("") }
    var selectedListId by remember { mutableStateOf(initialListId.ifBlank { fallbackListId }) }
    var isImportant by remember { mutableStateOf(defaultImportant) }
    var addToMyDay by remember { mutableStateOf(defaultMyDay) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var reminderTime by remember { mutableStateOf<Instant?>(null) }
    var repeatRule by remember { mutableStateOf<TaskRepeatRule?>(null) }
    var repeatDetail by remember { mutableStateOf<String?>(null) }
    var dueTouched by remember { mutableStateOf(false) }
    var reminderTouched by remember { mutableStateOf(false) }
    var parserDueOption by remember { mutableStateOf<QuickOption<LocalDate?>?>(null) }
    var parserReminderOption by remember { mutableStateOf<QuickOption<Instant?>?>(null) }
    var parsedEventSentence by remember { mutableStateOf<String?>(null) }
    var parsedReminderSentence by remember { mutableStateOf<String?>(null) }

    var expandedSection by remember { mutableStateOf<CreationSection?>(CreationSection.ATTRIBUTES) }

    val today = remember { LocalDate.now() }
    val nowInstant = remember { Instant.now() }

    val dueFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
    val reminderFormatter = remember { DateTimeFormatter.ofPattern("M/d HH:mm") }

    val dueQuickOptions: List<QuickOption<LocalDate?>> = remember(today) {
        listOf(
            QuickOption<LocalDate?>("なし", null),
            QuickOption("今日", today),
            QuickOption("明日", today.plusDays(1)),
            QuickOption("来週", today.plusWeeks(1))
        )
    }
    var customDueOption by remember { mutableStateOf<QuickOption<LocalDate?>?>(null) }
    var customDueInput by remember { mutableStateOf("") }
    var customDueLabel by remember { mutableStateOf("カスタム") }
    var customDueError by remember { mutableStateOf<String?>(null) }

    val reminderQuickOptions: List<QuickOption<Instant?>> = remember(nowInstant) {
        listOf(
            QuickOption<Instant?>("なし", null),
            QuickOption("数時間後", nowInstant.plus(4, ChronoUnit.HOURS)),
            QuickOption("1日後", nowInstant.plus(1, ChronoUnit.DAYS)),
            QuickOption("来週", nowInstant.plus(7, ChronoUnit.DAYS))
        )
    }
    var customReminderOption by remember { mutableStateOf<QuickOption<Instant?>?>(null) }
    var customReminderLabel by remember { mutableStateOf("カスタム") }
    var customReminderInput by remember { mutableStateOf("") }
    var customReminderError by remember { mutableStateOf<String?>(null) }
    val customReminderFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }

    val repeatQuickOptions: List<QuickOption<TaskRepeatRule?>> = remember {
        listOf(
            QuickOption<TaskRepeatRule?>("なし", null),
            QuickOption("毎日", TaskRepeatRule.DAILY),
            QuickOption("毎週", TaskRepeatRule.WEEKLY),
            QuickOption("毎月", TaskRepeatRule.MONTHLY)
        )
    }
    var customRepeatOption by remember { mutableStateOf<QuickOption<TaskRepeatRule?>?>(null) }
    var customRepeatLabel by remember { mutableStateOf("カスタム") }
    var customRepeatDetailInput by remember { mutableStateOf("") }
    var customRepeatError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(title) {
        if (title.isBlank()) {
            parserDueOption = null
            parserReminderOption = null
            parsedEventSentence = null
            parsedReminderSentence = null
            dueTouched = false
            reminderTouched = false
            return@LaunchedEffect
        }

        val parsed = NaturalLanguageParser.parse(title)
        parsedEventSentence = parsed.eventSentence
        parsedReminderSentence = parsed.reminderSentence

        parserDueOption = parsed.dueDate?.let {
            QuickOption("解析: ${it.format(dueFormatter)}", it)
        }
        if (!dueTouched) {
            dueDate = parsed.dueDate
        }

        parserReminderOption = parsed.reminderTime?.let {
            val zoned = it.atZone(ZoneId.systemDefault())
            QuickOption("解析: ${zoned.format(reminderFormatter)}", it)
        }
        if (!reminderTouched) {
            reminderTime = parsed.reminderTime
        }
    }

    val attributeSummary = remember(selectedListId, taskLists, isImportant, addToMyDay) {
        buildString {
            append(taskLists.firstOrNull { it.id == selectedListId }?.name ?: "タスク")
            if (isImportant) append(" / 重要")
            if (addToMyDay) append(" / マイデー")
        }
    }
    val dueSummary = dueDate?.format(dueFormatter) ?: "なし"
    val reminderSummary = reminderTime?.let {
        reminderFormatter.withZone(ZoneId.systemDefault()).format(it)
    } ?: "なし"
    val repeatSummary = when (repeatRule) {
        null -> "なし"
        TaskRepeatRule.DAILY -> "毎日"
        TaskRepeatRule.WEEKLY -> "毎週"
        TaskRepeatRule.MONTHLY -> "毎月"
        TaskRepeatRule.CUSTOM -> repeatDetail ?: "カスタム"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("タスク名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        parsedEventSentence?.let { sentence ->
            if (sentence.isNotBlank()) {
                Text(
                    text = "解析対象: $sentence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        parsedReminderSentence?.let { sentence ->
            if (sentence.isNotBlank()) {
                Text(
                    text = "解析リマインド: $sentence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(
                label = "属性",
                summary = attributeSummary,
                selected = expandedSection == CreationSection.ATTRIBUTES
            ) {
                expandedSection = toggleSection(expandedSection, CreationSection.ATTRIBUTES)
            }
            ActionChip(
                label = "期限",
                summary = dueSummary,
                selected = expandedSection == CreationSection.DUE
            ) {
                expandedSection = toggleSection(expandedSection, CreationSection.DUE)
            }
            ActionChip(
                label = "通知",
                summary = reminderSummary,
                selected = expandedSection == CreationSection.REMINDER
            ) {
                expandedSection = toggleSection(expandedSection, CreationSection.REMINDER)
            }
            ActionChip(
                label = "繰り返し",
                summary = repeatSummary,
                selected = expandedSection == CreationSection.REPEAT
            ) {
                expandedSection = toggleSection(expandedSection, CreationSection.REPEAT)
            }
        }

        AnimatedVisibility(visible = expandedSection == CreationSection.ATTRIBUTES) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (taskLists.isEmpty()) {
                        FilterChip(
                            selected = selectedListId == fallbackListId,
                            onClick = { selectedListId = fallbackListId },
                            label = { Text("タスク") }
                        )
                    } else {
                        taskLists.forEach { list ->
                            FilterChip(
                                selected = selectedListId == list.id,
                                onClick = { selectedListId = list.id },
                                label = { Text(list.name) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AttributeToggle(
                        label = "重要",
                        checked = isImportant,
                        onCheckedChange = { isImportant = it }
                    )
                    AttributeToggle(
                        label = "マイデー",
                        checked = addToMyDay,
                        onCheckedChange = { addToMyDay = it }
                    )
                }
            }
        }

        AnimatedVisibility(visible = expandedSection == CreationSection.DUE) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val dueOptionsCombined = remember(parserDueOption, customDueOption) {
                    buildList<QuickOption<LocalDate?>> {
                        addAll(dueQuickOptions)
                        parserDueOption?.let { add(it) }
                        customDueOption?.let { add(it) }
                    }
                }
                val dueSelectedLabel = dueOptionsCombined.firstOrNull { it.value == dueDate }?.label
                OptionRow(
                    options = dueOptionsCombined,
                    selectedLabel = dueSelectedLabel,
                    onSelected = { option ->
                        val isParserOption = parserDueOption?.let { it == option } ?: false
                        dueDate = option.value
                        customDueError = null
                        dueTouched = !isParserOption
                        expandedSection = null
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "カスタム日付（例: 2025-12-31）",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = customDueInput,
                        onValueChange = {
                            customDueInput = it
                            customDueError = null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customDueLabel,
                        onValueChange = { customDueLabel = it },
                        label = { Text("ラベル (任意)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customDueError != null) {
                        Text(
                            text = customDueError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            customDueInput = ""
                            customDueLabel = "カスタム"
                            customDueError = null
                        }) {
                            Text("クリア")
                        }
                        Button(
                            onClick = {
                                val parsed = runCatching {
                                    LocalDate.parse(customDueInput.trim())
                                }.getOrNull()

                                if (parsed == null) {
                                    customDueError = "日付は YYYY-MM-DD 形式で入力してください"
                                } else {
                                    val label = customDueLabel.ifBlank { "カスタム" }
                                    val option = QuickOption<LocalDate?>(label, parsed)
                                    customDueOption = option
                                    dueDate = parsed
                                    dueTouched = true
                                    customDueError = null
                                    expandedSection = null
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("設定")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = expandedSection == CreationSection.REMINDER) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val reminderOptionsCombined = remember(parserReminderOption, customReminderOption) {
                    buildList<QuickOption<Instant?>> {
                        addAll(reminderQuickOptions)
                        parserReminderOption?.let { add(it) }
                        customReminderOption?.let { add(it) }
                    }
                }
                val reminderSelectedLabel = reminderOptionsCombined.firstOrNull { it.value == reminderTime }?.label
                OptionRow(
                    options = reminderOptionsCombined,
                    selectedLabel = reminderSelectedLabel,
                    onSelected = { option ->
                        val isParserOption = parserReminderOption?.let { it == option } ?: false
                        reminderTime = option.value
                        customReminderError = null
                        reminderTouched = !isParserOption
                        expandedSection = null
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "カスタム通知（例: 2025-12-31 08:00）",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = customReminderInput,
                        onValueChange = {
                            customReminderInput = it
                            customReminderError = null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customReminderLabel,
                        onValueChange = { customReminderLabel = it },
                        label = { Text("ラベル (任意)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customReminderError != null) {
                        Text(
                            text = customReminderError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            customReminderInput = ""
                            customReminderLabel = "カスタム"
                            customReminderError = null
                        }) {
                            Text("クリア")
                        }
                        Button(
                            onClick = {
                                val parsed = runCatching {
                                    val dateTime = LocalDateTime.parse(
                                        customReminderInput.trim(),
                                        customReminderFormatter
                                    )
                                    dateTime.atZone(ZoneId.systemDefault()).toInstant()
                                }.getOrNull()

                                if (parsed == null) {
                                    customReminderError = "日時は YYYY-MM-DD HH:MM 形式で入力してください"
                                } else {
                                    val label = customReminderLabel.ifBlank { "カスタム" }
                                    val option = QuickOption<Instant?>(label, parsed)
                                    customReminderOption = option
                                    reminderTime = parsed
                                    reminderTouched = true
                                    customReminderError = null
                                    expandedSection = null
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("設定")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = expandedSection == CreationSection.REPEAT) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val repeatOptionsCombined = remember(customRepeatOption) {
                    buildList<QuickOption<TaskRepeatRule?>> {
                        addAll(repeatQuickOptions)
                        customRepeatOption?.let { add(it) }
                    }
                }
                OptionRow<TaskRepeatRule?>(
                    options = repeatOptionsCombined,
                    selectedLabel = repeatQuickOptions.asSequence()
                        .firstOrNull { it.value == repeatRule }?.label
                        ?: customRepeatOption?.takeIf { it.value == repeatRule }?.label,
                    onSelected = { option ->
                        repeatRule = option.value
                        repeatDetail = if (option == customRepeatOption) {
                            customRepeatDetailInput.ifBlank { null }
                        } else {
                            null
                        }
                        customRepeatError = null
                        expandedSection = null
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "カスタム繰り返し（例: 平日 / 第1月曜日など）",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = customRepeatDetailInput,
                        onValueChange = { newValue ->
                            customRepeatDetailInput = newValue
                            customRepeatError = null
                            if (repeatRule == TaskRepeatRule.CUSTOM && customRepeatOption != null) {
                                repeatDetail = newValue.ifBlank { null }
                            }
                        },
                        placeholder = { Text("詳細を入力") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customRepeatLabel,
                        onValueChange = { customRepeatLabel = it },
                        label = { Text("ラベル (任意)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customRepeatError != null) {
                        Text(
                            text = customRepeatError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            customRepeatDetailInput = ""
                            customRepeatLabel = "カスタム"
                            customRepeatError = null
                        }) {
                            Text("クリア")
                        }
                        Button(
                            onClick = {
                                if (customRepeatDetailInput.isBlank()) {
                                    customRepeatError = "繰り返し内容を入力してください"
                                } else {
                                    val label = customRepeatLabel.ifBlank { "カスタム" }
                                    val option = QuickOption<TaskRepeatRule?>(label, TaskRepeatRule.CUSTOM)
                                    customRepeatOption = option
                                    repeatRule = TaskRepeatRule.CUSTOM
                                    repeatDetail = customRepeatDetailInput.trim()
                                    customRepeatError = null
                                    expandedSection = null
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("設定")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            onCancel?.let { cancel ->
                TextButton(onClick = cancel) {
                    Text("キャンセル")
                }
            }
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val parsed = NaturalLanguageParser.parse(title)
                    val finalTitle = parsed.cleanedTitle.ifBlank { title.trim() }
                    val finalDueDate = when {
                        dueTouched -> dueDate
                        dueDate != null -> dueDate
                        else -> parsed.dueDate
                    }
                    val finalReminder = when {
                        reminderTouched -> reminderTime
                        reminderTime != null -> reminderTime
                        else -> parsed.reminderTime
                    }
                    onSubmit(
                        TaskCreationParams(
                            title = finalTitle,
                            listId = selectedListId,
                            isImportant = isImportant,
                            addToMyDay = addToMyDay,
                            dueDate = finalDueDate,
                            reminderTime = finalReminder,
                            repeatRule = repeatRule,
                            repeatDetail = repeatDetail
                        )
                    )
                    title = ""
                    parserDueOption = null
                    parserReminderOption = null
                    parsedEventSentence = null
                    parsedReminderSentence = null
                    dueTouched = false
                    reminderTouched = false
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(submitLabel)
            }
        }
    }
}

private fun toggleSection(current: CreationSection?, target: CreationSection): CreationSection? {
    return if (current == target) null else target
}

@Composable
private fun ActionChip(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "$label: $summary",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun AttributeToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> OptionRow(
    options: List<QuickOption<T>>,
    selectedLabel: String?,
    onSelected: (QuickOption<T>) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selectedLabel == option.label,
                onClick = { onSelected(option) },
                label = { Text(option.label) }
            )
        }
    }
}
