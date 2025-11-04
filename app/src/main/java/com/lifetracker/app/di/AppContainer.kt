package com.lifetracker.app.di

import android.content.Context
import com.lifetracker.app.data.analytics.JsonEventAnalyticsRepository
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePlugin
import com.lifetracker.app.core.storage.StoragePluginLogger
import com.lifetracker.app.data.local.JsonEventRepository
import com.lifetracker.app.data.local.JsonTaskRepository
import com.lifetracker.app.data.local.JsonWellnessRepository
import com.lifetracker.app.export.dashboard.DashboardCsvFormatter
import com.lifetracker.app.export.dashboard.DashboardExportRepository
import com.lifetracker.app.export.dashboard.DashboardPdfFormatter
import com.lifetracker.app.export.dashboard.DashboardSnapshotMapper
import com.lifetracker.app.settings.NaturalLanguageSettingsManager
import com.lifetracker.app.plugins.storage.sqlite.SqliteStoragePlugin
import com.lifetracker.app.plugins.storage.sqlite.analytics.SqliteEventAnalyticsRepository
import com.lifetracker.app.plugins.storage.sqlite.db.LifeTrackerDatabase
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.repository.EventRepository
import com.lifetracker.core.repository.TaskRepository
import com.lifetracker.core.repository.WellnessRepository

/**
 * Simple dependency container that wires core contracts to storage plugins.
 * Swapping implementations becomes a matter of changing this wiring.
 */
class AppContainer(
    private val context: Context,
    private val storageLocationManager: StorageLocationManager,
    private val storagePlugin: StoragePlugin,
    private val storageLogger: StoragePluginLogger
) {
    private val storage: LifeTrackerStorage by lazy {
        storagePlugin.createStorage(context, storageLocationManager, storageLogger)
    }

    val eventRepository: EventRepository by lazy { JsonEventRepository(storage) }
    val taskRepository: TaskRepository by lazy { JsonTaskRepository(storage) }
    val wellnessRepository: WellnessRepository by lazy { JsonWellnessRepository(storage) }
    val eventAnalyticsRepository: EventAnalyticsRepository by lazy {
        if (storagePlugin.id == SqliteStoragePlugin.PLUGIN_ID) {
            val database = LifeTrackerDatabase.getInstance(context)
            SqliteEventAnalyticsRepository(database.lifeTrackerDao())
        } else {
            JsonEventAnalyticsRepository(storage)
        }
    }
    val settingsManager: NaturalLanguageSettingsManager by lazy { NaturalLanguageSettingsManager(context) }
    val dashboardSnapshotMapper: DashboardSnapshotMapper by lazy { DashboardSnapshotMapper() }
    val dashboardCsvFormatter: DashboardCsvFormatter by lazy { DashboardCsvFormatter() }
    val dashboardPdfFormatter: DashboardPdfFormatter by lazy { DashboardPdfFormatter() }
    val dashboardExportRepository: DashboardExportRepository by lazy {
        DashboardExportRepository(context.applicationContext)
    }
}
