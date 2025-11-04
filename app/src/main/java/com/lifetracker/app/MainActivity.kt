package com.lifetracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.core.model.PomodoroTargetType
import com.lifetracker.app.export.dashboard.DashboardExportViewModel
import com.lifetracker.app.ui.quickadd.TaskQuickAddNotifier
import com.lifetracker.app.ui.screens.AnalyticsGraphScreen
import com.lifetracker.app.ui.screens.DashboardScreen
import com.lifetracker.app.ui.screens.HabitsScreen
import com.lifetracker.app.ui.screens.PomodoroTimerScreen
import com.lifetracker.app.ui.screens.SettingsScreen
import com.lifetracker.app.ui.screens.StorageLocationSelectionScreen
import com.lifetracker.app.ui.screens.TasksScreen
import com.lifetracker.app.ui.theme.LifeTrackerTheme
import com.lifetracker.app.settings.NaturalLanguageSettingsManager
import com.lifetracker.app.viewmodel.AnalyticsGraphViewModel
import com.lifetracker.app.viewmodel.DashboardViewModel
import com.lifetracker.app.viewmodel.HabitsViewModel
import com.lifetracker.app.viewmodel.PomodoroTimerViewModel
import com.lifetracker.app.viewmodel.SettingsViewModel
import com.lifetracker.app.viewmodel.TasksViewModel
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.repository.TaskRepository
import com.lifetracker.core.repository.EventRepository
import com.lifetracker.core.repository.WellnessRepository

class MainActivity : ComponentActivity() {

    private val app: LifeTrackerApplication
        get() = application as LifeTrackerApplication

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                TaskQuickAddNotifier.showQuickAddNotification(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()

        setContent {
            LifeTrackerTheme {
                var hasLocation by remember { mutableStateOf(app.storageLocationManager.hasLocation()) }

                if (!hasLocation) {
                    StorageLocationSelectionScreen { location ->
                        app.storageLocationManager.setLocation(applicationContext, location)
                        hasLocation = true
                    }
                } else {
                    val appContainer = remember { app.appContainer }
                    MainScreen(
                        eventRepository = appContainer.eventRepository,
                        taskRepository = appContainer.taskRepository,
                        wellnessRepository = appContainer.wellnessRepository,
                        eventAnalyticsRepository = appContainer.eventAnalyticsRepository,
                        settingsManager = appContainer.settingsManager,
                        dashboardSnapshotMapper = appContainer.dashboardSnapshotMapper,
                        dashboardCsvFormatter = appContainer.dashboardCsvFormatter,
                        dashboardPdfFormatter = appContainer.dashboardPdfFormatter,
                        dashboardExportRepository = appContainer.dashboardExportRepository
                    )
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    TaskQuickAddNotifier.showQuickAddNotification(this)
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            TaskQuickAddNotifier.showQuickAddNotification(this)
        }
    }
}

@Composable
fun MainScreen(
    eventRepository: EventRepository,
    taskRepository: TaskRepository,
    wellnessRepository: WellnessRepository,
    eventAnalyticsRepository: EventAnalyticsRepository,
    settingsManager: NaturalLanguageSettingsManager,
    dashboardSnapshotMapper: com.lifetracker.app.export.dashboard.DashboardSnapshotMapper,
    dashboardCsvFormatter: com.lifetracker.app.export.dashboard.DashboardCsvFormatter,
    dashboardPdfFormatter: com.lifetracker.app.export.dashboard.DashboardPdfFormatter,
    dashboardExportRepository: com.lifetracker.app.export.dashboard.DashboardExportRepository
) {
    val selectedTab = remember { mutableIntStateOf(0) }

    val pomodoroViewModel: PomodoroTimerViewModel = viewModel {
        PomodoroTimerViewModel(taskRepository, eventRepository)
    }
    val dashboardViewModel: DashboardViewModel = viewModel {
        DashboardViewModel(taskRepository, eventRepository, wellnessRepository)
    }
    val dashboardExportViewModel: DashboardExportViewModel = viewModel {
        DashboardExportViewModel(
            dashboardSnapshotMapper,
            dashboardCsvFormatter,
            dashboardPdfFormatter,
            dashboardExportRepository
        )
    }
    val habitsViewModel: HabitsViewModel = viewModel {
        HabitsViewModel(eventRepository)
    }
    val tasksViewModel: TasksViewModel = viewModel {
        TasksViewModel(taskRepository)
    }
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(settingsManager)
    }
    val analyticsViewModel: AnalyticsGraphViewModel = viewModel {
        AnalyticsGraphViewModel(eventAnalyticsRepository)
    }

    val switchToTimer: (PomodoroTargetType, String?) -> Unit = { type, id ->
        pomodoroViewModel.prepareTarget(type, id)
        selectedTab.intValue = 1
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab.intValue == 0,
                    onClick = { selectedTab.intValue = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Timer") },
                    label = { Text("Timer") },
                    selected = selectedTab.intValue == 1,
                    onClick = { selectedTab.intValue = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Habits") },
                    label = { Text("Habits") },
                    selected = selectedTab.intValue == 2,
                    onClick = { selectedTab.intValue = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    selected = selectedTab.intValue == 3,
                    onClick = { selectedTab.intValue = 3 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Analytics") },
                    label = { Text("Analytics") },
                    selected = selectedTab.intValue == 4,
                    onClick = { selectedTab.intValue = 4 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab.intValue == 5,
                    onClick = { selectedTab.intValue = 5 }
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedTab.intValue) {
                0 -> {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        exportViewModel = dashboardExportViewModel
                    )
                }

                1 -> {
                    PomodoroTimerScreen(viewModel = pomodoroViewModel)
                }

                2 -> {
                    HabitsScreen(
                        viewModel = habitsViewModel,
                        onStartPomodoro = { habitId ->
                            switchToTimer(PomodoroTargetType.HABIT, habitId)
                        }
                    )
                }

                3 -> {
                    TasksScreen(
                        viewModel = tasksViewModel,
                        onStartPomodoro = { taskId ->
                            switchToTimer(PomodoroTargetType.TASK, taskId)
                        }
                    )
                }

                4 -> {
                    AnalyticsGraphScreen(viewModel = analyticsViewModel)
                }

                5 -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToDashboard = { selectedTab.intValue = 0 },
                        onNavigateToAnalytics = { selectedTab.intValue = 4 }
                    )
                }
            }
        }
    }
}
