package com.lifetracker.app.core.storage

import com.lifetracker.app.plugins.storage.json.JsonStoragePlugin
import com.lifetracker.app.plugins.storage.sqlite.SqliteStoragePlugin

class StoragePluginRegistry {
    private val plugins: List<StoragePlugin> = listOf(
        SqliteStoragePlugin,
        JsonStoragePlugin
    )

    val defaultPlugin: StoragePlugin = SqliteStoragePlugin

    fun listPlugins(): List<StoragePlugin> = plugins

    fun pluginById(id: String): StoragePlugin? = plugins.firstOrNull { it.id == id }
}
