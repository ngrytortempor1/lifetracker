package com.lifetracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetracker.app.dashboard.DashboardMetricFormatter
import com.lifetracker.app.export.dashboard.DashboardExportEvent
import com.lifetracker.app.export.dashboard.DashboardExportFormat
import com.lifetracker.app.export.dashboard.DashboardExportViewModel
import com.lifetracker.app.viewmodel.DashboardUiState
import com.lifetracker.app.viewmodel.DashboardViewModel
import androidx.core.app.ShareCompat
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    exportViewModel: DashboardExportViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var exportMenuExpanded by remember { mutableStateOf(false) }

    val readyState = state as? DashboardUiState.Ready

    LaunchedEffect(exportState.errorMessage) {
        exportState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            exportViewModel.clearError()
        }
    }

    LaunchedEffect(exportViewModel) {
        exportViewModel.shareEvents.collect { event ->
            exportMenuExpanded = false
            when (event) {
                is DashboardExportEvent.Share -> {
                    val shareIntent = ShareCompat.IntentBuilder(context)
                        .setType(event.mimeType)
                        .setStream(event.uri)
                        .setChooserTitle("ダッシュボードを共有")
                        .createChooserIntent()
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(shareIntent)
                    snackbarHostState.showSnackbar("エクスポートファイルを共有できます (${event.fileName})")
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "ダッシュボード") },
                actions = {
                    Box {
                        IconButton(
                            onClick = { exportMenuExpanded = true },
                            enabled = readyState != null && !exportState.isExporting
                        ) {
                            if (exportState.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "エクスポート"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("CSV としてエクスポート") },
                                onClick = {
                                    exportMenuExpanded = false
                                    readyState?.let { exportViewModel.export(it, DashboardExportFormat.CSV) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("PDF としてエクスポート") },
                                onClick = {
                                    exportMenuExpanded = false
                                    readyState?.let { exportViewModel.export(it, DashboardExportFormat.PDF) }
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        DashboardScreenContent(
            state = state,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun DashboardScreenContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier
) {
    when (state) {
        DashboardUiState.Loading -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text(
                    text = "読み込み中...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        is DashboardUiState.Error -> {
            val message = state.message
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        is DashboardUiState.Ready -> {
            val data = state
            val items = buildList {
                DashboardCardData(
                    title = "My Day",
                    value = "${data.myDayCount}",
                    description = "今日集中すべきタスク",
                    icon = Icons.Default.Today
                ).also(::add)
                DashboardCardData(
                    title = "期限 (今日/遅延)",
                    value = "${data.todayDueCount} / ${data.overdueCount}",
                    description = "今日の期限と期限切れタスク",
                    icon = Icons.Default.History
                ).also(::add)
                DashboardCardData(
                    title = "完了タスク (今日)",
                    value = "${data.completedTodayCount}",
                    description = "今日完了済みのタスク数",
                    icon = Icons.Default.CheckCircle
                ).also(::add)
                DashboardCardData(
                    title = "習慣達成",
                    value = "${data.habitCompleted} / ${data.habitTotal}",
                    description = "今日の習慣達成状況",
                    icon = Icons.Default.CheckCircle
                ).also(::add)

                data.latestMood?.let { mood ->
                    DashboardCardData(
                        title = "最新の気分",
                        value = mood.score.toString(),
                        description = DashboardMetricFormatter.moodDescription(mood),
                        icon = Icons.Default.Mood
                    ).also(::add)
                }

                data.lastSleep?.let { sleep ->
                    DashboardCardData(
                        title = "最新の睡眠",
                        value = DashboardMetricFormatter.sleepDurationLabel(sleep),
                        description = DashboardMetricFormatter.sleepDescription(sleep),
                        icon = Icons.Default.Bedtime
                    ).also(::add)
                }
            }

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("dashboard_summary_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "今日の概要",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "タスクと習慣の進捗をまとめて確認できます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(items) { card ->
                    DashboardCard(card)
                }
            }
        }
    }
}
private data class DashboardCardData(
    val title: String,
    val value: String,
    val description: String,
    val icon: ImageVector
)

@Composable
private fun DashboardCard(data: DashboardCardData) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = data.value,
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
