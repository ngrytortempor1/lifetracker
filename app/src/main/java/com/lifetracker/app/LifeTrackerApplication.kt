package com.lifetracker.app

import android.app.Application
import com.lifetracker.app.core.storage.AndroidStoragePluginLogger
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePlugin
import com.lifetracker.app.core.storage.StoragePluginLogger
import com.lifetracker.app.core.storage.StoragePluginRegistry
import com.lifetracker.app.di.AppContainer

class LifeTrackerApplication : Application() {
    val storageLocationManager: StorageLocationManager by lazy { StorageLocationManager(this) }
    val storagePluginRegistry: StoragePluginRegistry by lazy { StoragePluginRegistry() }
    val activeStoragePlugin: StoragePlugin by lazy { storagePluginRegistry.defaultPlugin }
    val storagePluginLogger: StoragePluginLogger by lazy { AndroidStoragePluginLogger() }
    val appContainer: AppContainer by lazy {
        AppContainer(this, storageLocationManager, activeStoragePlugin, storagePluginLogger)
    }

    override fun onCreate() {
        super.onCreate()
        storageLocationManager
    }
}
