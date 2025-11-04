package com.lifetracker.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifetracker.app.viewmodel.SettingsUiState
import com.lifetracker.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsContent(
        state = uiState,
        onEventPrefixChange = viewModel::onEventPrefixChange,
        onReminderPrefixChange = viewModel::onReminderPrefixChange,
        onDueHourChange = viewModel::onDueHourChange,
        onDueMinuteChange = viewModel::onDueMinuteChange,
        onReminderLeadMinutesChange = viewModel::onReminderLeadMinutesChange,
        onToggleGeneralSection = viewModel::onToggleGeneralSection,
        onToggleDictionarySection = viewModel::onToggleDictionarySection,
        onToggleAnalyticsGuideSection = viewModel::onToggleAnalyticsGuideSection,
        onToggleAdvancedSection = viewModel::onToggleAdvancedSection,
        onAddRelativeDayEntry = viewModel::onAddRelativeDayEntry,
        onRemoveRelativeDayEntry = viewModel::onRemoveRelativeDayEntry,
        onRelativeDayLabelChange = viewModel::onRelativeDayLabelChange,
        onRelativeDayOffsetChange = viewModel::onRelativeDayOffsetChange,
        onSave = viewModel::saveChanges,
        onReset = viewModel::resetChanges,
        onNavigateToDashboard = onNavigateToDashboard,
        onNavigateToAnalytics = onNavigateToAnalytics
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onEventPrefixChange: (String) -> Unit,
    onReminderPrefixChange: (String) -> Unit,
    onDueHourChange: (String) -> Unit,
    onDueMinuteChange: (String) -> Unit,
    onReminderLeadMinutesChange: (String) -> Unit,
    onToggleGeneralSection: () -> Unit,
    onToggleDictionarySection: () -> Unit,
    onToggleAnalyticsGuideSection: () -> Unit,
    onToggleAdvancedSection: () -> Unit,
    onAddRelativeDayEntry: () -> Unit,
    onRemoveRelativeDayEntry: (Long) -> Unit,
    onRelativeDayLabelChange: (Long, String) -> Unit,
    onRelativeDayOffsetChange: (Long, String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "設定",
            style = MaterialTheme.typography.headlineSmall
        )

        SettingsSection(
            title = "解析グラフの見方",
            description = "ダッシュボードに表示されるグラフの読み解き方や活用のポイントです。使いながら迷ったときはここに戻って確認してください。",
            expanded = state.analyticsGuideSectionExpanded,
            onToggle = onToggleAnalyticsGuideSection
        ) {
            Text(
                text = "・横軸は時間、縦軸はトラッキングしている指標ごとの割合や回数です。直近7日と28日の2本が重ねて表示されるため、短期と中期の傾向を一目で比較できます。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "・タスクの完了や未完了、習慣の継続など、イベントの種類ごとに色分けされています。色の凡例はグラフ右上を確認してください。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "・山が急に伸びたり沈んだりした区間は、ルーティンの変化点です。影響が出た日付をタップし、ノートやタスク履歴と並べて振り返ると対策が立てやすくなります。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "・グラフ下部のスイッチで表示する指標を切り替えられます。最初は「完了率」と「未着手率」の2つだけに絞り、慣れてきたらコンテキスト別の見方（時間帯やタグごとの偏り）を追加すると過負荷になりません。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "・変化があった週は、同じ期間のポモドーロ数やコンディションメモも併せて確認しましょう。複数データを重ねると、単なる数値の変動ではなく行動パターンとして把握できます。",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsSection(
            title = "高度な機能",
            description = "AI連携やテクニカルなデータ分析のための導線をまとめています。通常の使い方に慣れてきたら、必要なものから順に活用してください。",
            expanded = state.advancedSectionExpanded,
            onToggle = onToggleAdvancedSection
        ) {
            AdvancedFeatureItem(
                title = "ダッシュボードのエクスポート",
                description = "CSV/PDF形式でダッシュボードのサマリーを出力できます。外部AIツールに渡したり、チームへの共有レポートに活用してください。",
                actionLabel = "ダッシュボードを開く",
                onAction = onNavigateToDashboard
            )

            AdvancedFeatureItem(
                title = "イベント分析ビュー",
                description = "イベント遷移やルーティンの切り替えを時系列で可視化する高度グラフです。変化点の検証や仮説の裏付けに使えます。",
                actionLabel = "分析ビューを開く",
                onAction = onNavigateToAnalytics
            )
        }

        SettingsSection(
            title = "解析の基本",
            description = "タスク名に含める記号やデフォルト時刻を調整できます。複数文字を設定する場合はスペース無しで続けて入力してください。",
            expanded = state.generalSectionExpanded,
            onToggle = onToggleGeneralSection
        ) {
            OutlinedTextField(
                value = state.eventPrefixInput,
                onValueChange = onEventPrefixChange,
                label = { Text("イベント用プレフィックス（例: ！）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.reminderPrefixInput,
                onValueChange = onReminderPrefixChange,
                label = { Text("リマインド用プレフィックス（例: ？）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.dueHourInput,
                    onValueChange = onDueHourChange,
                    label = { Text("期限: 時") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.dueMinuteInput,
                    onValueChange = onDueMinuteChange,
                    label = { Text("期限: 分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = state.reminderLeadMinutesInput,
                onValueChange = onReminderLeadMinutesChange,
                label = { Text("リマインダーのリードタイム（分）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsSection(
            title = "辞書のカスタマイズ",
            description = "「明日」「明後日」など、タスク入力で使うキーワードと日数オフセットを編集できます。",
            expanded = state.dictionarySectionExpanded,
            onToggle = onToggleDictionarySection
        ) {
            if (state.relativeDayEntries.isEmpty()) {
                Text(
                    text = "キーワードが未設定です。「追加」から新しいエントリを作成してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "オフセットは「今日=0」で未来を正、過去を負の整数で指定します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.relativeDayEntries.forEach { entry ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = entry.label,
                            onValueChange = { onRelativeDayLabelChange(entry.id, it) },
                            label = { Text("キーワード") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f)
                        )
                        OutlinedTextField(
                            value = entry.offset,
                            onValueChange = { onRelativeDayOffsetChange(entry.id, it) },
                            label = { Text("日数オフセット") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemoveRelativeDayEntry(entry.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "削除"
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onAddRelativeDayEntry,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("追加")
            }
        }

        state.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        state.successMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onReset,
                enabled = state.isDirty && !state.isSaving
            ) {
                Text("リセット")
            }
            Button(
                onClick = onSave,
                enabled = state.isDirty && !state.isSaving,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("保存")
                }
            }
        }

        if (!state.isDirty && !state.isSaving && state.successMessage == null && state.errorMessage == null) {
            Text(
                text = "現在の設定が適用されています。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "折りたたむ" else "展開する"
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AdvancedFeatureItem(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
