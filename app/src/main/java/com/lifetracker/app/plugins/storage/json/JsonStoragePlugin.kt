package com.lifetracker.app.plugins.storage.json

import android.content.Context
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePluginLogger
import com.lifetracker.app.core.storage.StoragePlugin

/**
 * Default JSONL-backed storage plugin.
 */
object JsonStoragePlugin : StoragePlugin {
    override val id: String = "jsonl"
    override val displayName: String = "JSONL Storage"
    override val description: String = "Stores data as JSON lines files. Lightweight and portable."

    override fun createStorage(
        context: Context,
        locationManager: StorageLocationManager,
        logger: StoragePluginLogger
    ): LifeTrackerStorage = JsonlStorage(context, locationManager, logger)
}
