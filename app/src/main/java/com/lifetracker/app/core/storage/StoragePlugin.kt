package com.lifetracker.app.core.storage

import android.content.Context

/**
 * Represents a pluggable persistence backend for [LifeTrackerStorage].
 */
interface StoragePlugin {
    val id: String
    val displayName: String
    val description: String

    fun createStorage(
        context: Context,
        locationManager: StorageLocationManager,
        logger: StoragePluginLogger
    ): LifeTrackerStorage
}
