package com.lifetracker.app.plugins.storage.sqlite

import android.content.Context
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePlugin
import com.lifetracker.app.core.storage.StoragePluginLogger

object SqliteStoragePlugin : StoragePlugin {
    const val PLUGIN_ID = "sqlite"

    override val id: String = PLUGIN_ID
    override val displayName: String = "SQLite Storage"
    override val description: String = "Persists data using Room-backed SQLite with JSONL outbox sync."

    override fun createStorage(
        context: Context,
        locationManager: StorageLocationManager,
        logger: StoragePluginLogger
    ): LifeTrackerStorage = SqliteStorage(context, locationManager, logger)
}
