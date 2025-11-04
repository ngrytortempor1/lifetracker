package com.lifetracker.app.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetracker.app.analytics.EventAnalyticsCalculations.BucketTransition
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityBucket
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityDataStatus
import com.lifetracker.app.ui.components.PredictabilityChart
import com.lifetracker.app.ui.components.QuickLogTagLeaderboard
import com.lifetracker.app.ui.components.RollingAverageChart
import com.lifetracker.app.viewmodel.AnalyticsGraphScreenState
import com.lifetracker.app.viewmodel.AnalyticsGraphViewModel
import com.lifetracker.app.viewmodel.AnalyticsPresentationMode
import com.lifetracker.app.viewmodel.PredictabilityGraphUiState
import com.lifetracker.app.viewmodel.QuickLogLeaderboardUiState
import com.lifetracker.app.viewmodel.RollingAverageGraphUiState
import com.lifetracker.core.model.EventType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalyticsGraphScreen(
    viewModel: AnalyticsGraphViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    AnalyticsGraphContent(
        state = state,
        onRefresh = { viewModel.refresh(force = true) },
        onRangeSelected = viewModel::selectRange,
        onBucketSelected = viewModel::selectBucketMinutes,
        onMinSamplesSelected = viewModel::selectMinSamples,
        onSmoothingSelected = viewModel::selectSmoothingAlpha,
        onRollingWindowSelected = viewModel::selectRollingWindow,
        onRollingTypeSelected = viewModel::selectRollingAverageType,
        onQuickLogLimitSelected = viewModel::selectQuickLogLimit,
        onTogglePresentationMode = viewModel::togglePresentationMode,
        modifier = modifier
    )
}

@VisibleForTesting
@Composable
internal fun AnalyticsGraphContent(
    state: AnalyticsGraphScreenState,
    onRefresh: () -> Unit,
    onRangeSelected: (Int) -> Unit,
    onBucketSelected: (Int) -> Unit,
    onMinSamplesSelected: (Long) -> Unit,
    onSmoothingSelected: (Double) -> Unit,
    onRollingWindowSelected: (Int) -> Unit,
    onRollingTypeSelected: (EventType) -> Unit,
    onQuickLogLimitSelected: (Int) -> Unit,
    onTogglePresentationMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val predictability = state.predictability
    val rolling = state.rollingAverage
    val quickLog = state.quickLog

    val showInitialLoader = state.lastUpdatedAt == null &&
        predictability.isLoading && rolling.isLoading && quickLog.isLoading

    val rangeFormatter = remember {
        DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())
    }

    when {
        showInitialLoader -> LoadingState(modifier)
        predictability.errorMessage != null && predictability.buckets.isEmpty() ->
            ErrorState(message = predictability.errorMessage, onRefresh = onRefresh, modifier = modifier)
        else -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("analytics_graph_list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                item { AnalyticsHeader(state = state, onTogglePresentationMode = onTogglePresentationMode) }
                item {
                    FilterSection(
                        predictability = predictability,
                        rolling = rolling,
                        quickLog = quickLog,
                        onRangeSelected = onRangeSelected,
                        onBucketSelected = onBucketSelected,
                        onMinSamplesSelected = onMinSamplesSelected,
                        onSmoothingSelected = onSmoothingSelected,
                        onRollingWindowSelected = onRollingWindowSelected,
                        onRollingTypeSelected = onRollingTypeSelected,
                        onQuickLogLimitSelected = onQuickLogLimitSelected
                    )
                }
                item {
                    SummaryCard(
                        state = state,
                        rangeFormatter = rangeFormatter,
                        onRefresh = onRefresh
                    )
                }

                if (state.presentationMode == AnalyticsPresentationMode.TEXT) {
                    item {
                        TextReportCard(
                            predictability = predictability,
                            rolling = rolling,
                            quickLog = quickLog,
                            rangeFormatter = rangeFormatter
                        )
                    }
                } else {
                    item {
                        AdaptiveChartRow(
                            predictability = predictability,
                            rolling = rolling,
                            rangeFormatter = rangeFormatter
                        )
                    }
                }

                item {
                    QuickLogCard(
                        state = quickLog,
                        rangeFormatter = rangeFormatter
                    )
                }

                if (predictability.buckets.isEmpty()) {
                    item { EmptyStateCard(onRefresh = onRefresh) }
                } else {
                    items(predictability.buckets) { bucket ->
                        PredictabilityBucketCard(
                            bucket = bucket,
                            formatter = rangeFormatter
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "分析データを読み込み中…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ErrorState(message: String, onRefresh: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onRefresh) {
            Text("再読み込み")
        }
    }
}

@Composable
private fun AnalyticsHeader(
    state: AnalyticsGraphScreenState,
    onTogglePresentationMode: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "行動遷移とログの洞察",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "時間帯ごとの予測可能性、行動の濃淡、頻出タグをまとめて可視化します。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onTogglePresentationMode) {
                val (icon, label) = when (state.presentationMode) {
                    AnalyticsPresentationMode.CHART -> Icons.Filled.TableRows to "テキストレポート"
                    AnalyticsPresentationMode.TEXT -> Icons.Filled.BarChart to "グラフビュー"
                }
                Icon(imageVector = icon, contentDescription = label)
            }
        }
        state.lastUpdatedAt?.let { updatedAt ->
            Text(
                text = "最終更新: ${DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault()).format(updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterSection(
    predictability: PredictabilityGraphUiState,
    rolling: RollingAverageGraphUiState,
    quickLog: QuickLogLeaderboardUiState,
    onRangeSelected: (Int) -> Unit,
    onBucketSelected: (Int) -> Unit,
    onMinSamplesSelected: (Long) -> Unit,
    onSmoothingSelected: (Double) -> Unit,
    onRollingWindowSelected: (Int) -> Unit,
    onRollingTypeSelected: (EventType) -> Unit,
    onQuickLogLimitSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterGroup(
            label = "対象期間",
            options = predictability.availableRangeOptions,
            selected = predictability.selectedRangeDays,
            labelBuilder = { "${it}日" },
            onSelected = onRangeSelected
        )

        FilterGroup(
            label = "バケット幅",
            options = predictability.availableBucketOptions,
            selected = predictability.selectedBucketMinutes,
            labelBuilder = { option ->
                when (option) {
                    60 -> "1h"
                    45 -> "45m"
                    30 -> "30m"
                    else -> "${option}m"
                }
            },
            onSelected = onBucketSelected
        )

        FilterGroup(
            label = "最低サンプル数",
            options = predictability.availableMinSampleOptions,
            selected = predictability.selectedMinSamples,
            labelBuilder = { ">=${it}件" },
            onSelected = onMinSamplesSelected
        )

        FilterGroup(
            label = "平滑化係数",
            options = predictability.availableSmoothingOptions,
            selected = predictability.selectedSmoothingAlpha,
            labelBuilder = { "alpha=${String.format(Locale.JAPAN, "%.2f", it)}" },
            onSelected = onSmoothingSelected
        )

        FilterGroup(
            label = "イベントタイプ (ローリング平均)",
            options = rolling.availableTypeOptions,
            selected = rolling.selectedType,
            labelBuilder = { eventTypeLabel(it) },
            onSelected = onRollingTypeSelected
        )

        FilterGroup(
            label = "ウィンドウ幅 (ローリング平均)",
            options = rolling.availableWindowOptions,
            selected = rolling.selectedWindowDays,
            labelBuilder = { "${it}日" },
            onSelected = onRollingWindowSelected
        )

        FilterGroup(
            label = "タグ表示数",
            options = quickLog.availableLimitOptions,
            selected = quickLog.limit,
            labelBuilder = { "トップ${it}" },
            onSelected = onQuickLogLimitSelected
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterGroup(
    label: String,
    options: List<T>,
    selected: T,
    labelBuilder: (T) -> String,
    onSelected: (T) -> Unit
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(labelBuilder(option)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    state: AnalyticsGraphScreenState,
    rangeFormatter: DateTimeFormatter,
    onRefresh: () -> Unit
) {
    val predictability = state.predictability
    val rolling = state.rollingAverage
    val quickLog = state.quickLog

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rangeLabel = predictability.lastRange?.let { range ->
                "${rangeFormatter.format(range.start)} - ${rangeFormatter.format(range.endInclusive)}"
            } ?: "直近${predictability.selectedRangeDays}日"

            Text(
                text = "対象期間: $rangeLabel",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "予測バケット: ${predictability.buckets.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "観測不足バケット: ${predictability.insufficientBucketCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (predictability.insufficientBucketCount > 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "ローリング平均 (${eventTypeLabel(rolling.selectedType)}) / ウィンドウ: ${rolling.selectedWindowDays}日",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "トップタグ上位 ${quickLog.limit} 件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onRefresh) { Text("更新") }
            }
        }
    }
}

@Composable
private fun AdaptiveChartRow(
    predictability: PredictabilityGraphUiState,
    rolling: RollingAverageGraphUiState,
    rangeFormatter: DateTimeFormatter
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val isWide = LocalConfiguration.current.screenWidthDp >= 720
        val spacing = 16.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                PredictabilityChartCard(
                    modifier = Modifier.weight(1f),
                    state = predictability
                )
                RollingAverageChartCard(
                    modifier = Modifier.weight(1f),
                    state = rolling,
                    rangeFormatter = rangeFormatter
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                PredictabilityChartCard(
                    modifier = Modifier.fillMaxWidth(),
                    state = predictability
                )
                RollingAverageChartCard(
                    modifier = Modifier.fillMaxWidth(),
                    state = rolling,
                    rangeFormatter = rangeFormatter
                )
            }
        }
    }
}

@Composable
private fun PredictabilityChartCard(
    modifier: Modifier,
    state: PredictabilityGraphUiState
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "予測可能性プロット",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            PredictabilityChart(buckets = state.buckets)
            Text(
                text = "青い点が推定値、線はEMA。グレーは観測不足、点の大きさはサンプル数の比率です。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RollingAverageChartCard(
    modifier: Modifier,
    state: RollingAverageGraphUiState,
    rangeFormatter: DateTimeFormatter
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ローリング平均",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            RollingAverageChart(points = state.points)
            val range = state.lastRange
            val label = range?.let { "${rangeFormatter.format(it.start)} - ${rangeFormatter.format(it.endInclusive)}" }
            Text(
                text = buildString {
                    append(eventTypeLabel(state.selectedType))
                    append(" / ウィンドウ ")
                    append(state.selectedWindowDays)
                    append("日")
                    label?.let {
                        append(" / ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickLogCard(
    state: QuickLogLeaderboardUiState,
    rangeFormatter: DateTimeFormatter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "クイックログのトップタグ",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.lastRange?.let {
                        Text(
                            text = "${rangeFormatter.format(it.start)} - ${rangeFormatter.format(it.endInclusive)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            QuickLogTagLeaderboard(items = state.items)
        }
    }
}

@VisibleForTesting
@Composable
internal fun PredictabilityBucketCard(
    bucket: PredictabilityBucket,
    formatter: DateTimeFormatter
) {
    val probabilityText = bucket.weightedProbability?.let { "${(it * 100).roundToInt()}%" } ?: "観測不足"
    val emaText = bucket.emaProbability?.let { "EMA: ${(it * 100).roundToInt()}%" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${formatter.format(bucket.bucketStart)} - ${formatter.format(bucket.bucketEnd)}",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "サンプル数: ${bucket.sampleSize} / ペア: ${bucket.uniquePairCount}",
                style = MaterialTheme.typography.bodyMedium
            )

            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = {
                    bucket.weightedProbability?.toFloat()?.coerceIn(0f, 1f) ?: 0f
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = probabilityText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when (bucket.dataStatus) {
                        PredictabilityDataStatus.VALID -> MaterialTheme.colorScheme.primary
                        PredictabilityDataStatus.INSUFFICIENT_SAMPLES -> MaterialTheme.colorScheme.secondary
                    }
                )
                emaText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val topTransitions = remember(bucket.transitions) {
                bucket.transitions
                    .groupBy { it.sourceTaskId to it.destinationTaskId }
                    .map { (pair, items) ->
                        val representative = items.first()
                        Triple(pair.first, pair.second, representative.probability)
                    }
                    .sortedByDescending { it.third }
                    .take(3)
            }

            if (topTransitions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "主な遷移",
                        style = MaterialTheme.typography.labelLarge
                    )
                    topTransitions.forEach { (source, destination, probability) ->
                        Text(
                            text = "${source} → ${destination} (${(probability * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (bucket.dataStatus == PredictabilityDataStatus.INSUFFICIENT_SAMPLES) {
                Text(
                    text = "※ 観測データが少ないため推定値は参考程度としてください",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            PredictabilityBucketDetailDialogLauncher(bucket = bucket)
        }
    }
}

@Composable
private fun PredictabilityBucketDetailDialogLauncher(
    bucket: PredictabilityBucket
) {
    var showDialog by remember { mutableStateOf(false) }

    if (bucket.transitions.isEmpty()) {
        ElevatedAssistChip(
            onClick = {},
            enabled = false,
            label = { Text("詳細なし") }
        )
        return
    }

    Box(modifier = Modifier.testTag("predictability_detail_chip")) {
        ElevatedAssistChip(
            onClick = { showDialog = true },
            label = { Text("詳細を表示") }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("閉じる")
                }
            },
            title = { Text("バケット詳細") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bucket.transitions
                        .sortedByDescending { it.probability }
                        .forEach { transition ->
                            BucketTransitionRow(transition)
                        }
                }
            }
        )
    }
}

@Composable
private fun BucketTransitionRow(transition: BucketTransition) {
    val probabilityPercent = (transition.probability * 100).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${transition.sourceTaskId} → ${transition.destinationTaskId}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "確率: ${probabilityPercent}% / サンプル: ${transition.pairOccurrences} / 総数: ${transition.sourceSampleSize}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStateCard(onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "まだ行動データが十分にありません。タスク完了やクイックログを記録すると傾向が見えてきます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRefresh) {
                Text("もう一度読み込む")
            }
        }
    }
}

@Composable
private fun TextReportCard(
    predictability: PredictabilityGraphUiState,
    rolling: RollingAverageGraphUiState,
    quickLog: QuickLogLeaderboardUiState,
    rangeFormatter: DateTimeFormatter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "テキストレポート",
                style = MaterialTheme.typography.titleMedium
            )

            val validBuckets = predictability.buckets.filter { it.weightedProbability != null }
            val hottestBucket = validBuckets.maxByOrNull { it.weightedProbability ?: 0.0 }
            val coolestBucket = validBuckets.minByOrNull { it.weightedProbability ?: 1.0 }

            hottestBucket?.let {
                Text(
                    text = "最も自動化された時間帯: ${rangeFormatter.format(it.bucketStart)} - ${rangeFormatter.format(it.bucketEnd)} (${((it.weightedProbability ?: 0.0) * 100).roundToInt()}%)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            coolestBucket?.let {
                Text(
                    text = "変動が大きい時間帯: ${rangeFormatter.format(it.bucketStart)} - ${rangeFormatter.format(it.bucketEnd)} (${((it.weightedProbability ?: 0.0) * 100).roundToInt()}%)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            rolling.points.takeIf { it.isNotEmpty() }?.let { points ->
                val latest = points.maxByOrNull { it.windowEnd }
                latest?.let {
                    Text(
                        text = "最新のローリング平均 (${eventTypeLabel(rolling.selectedType)}): ${(it.average * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (quickLog.items.isNotEmpty()) {
                Text(
                    text = "トップタグ: ${quickLog.items.first().tag} (${quickLog.items.first().occurrences} 回)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (predictability.insufficientBucketCount > 0) {
                Text(
                    text = "観測不足の時間帯が ${predictability.insufficientBucketCount} 件あります。追加の記録で精度が向上します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "グラフビューに戻すとEMAの推移とサンプル密度を視覚的に確認できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun eventTypeLabel(type: EventType): String = when (type) {
    EventType.TASK_COMPLETED -> "タスク完了"
    EventType.HABIT_COMPLETED -> "習慣完了"
    EventType.LOG_QUICK -> "クイックログ"
    EventType.POMODORO_COMPLETED -> "ポモドーロ"
}
