package com.lifetracker.app.core.storage

interface StoragePluginLogger {
    fun info(pluginId: String, message: String)
    fun warn(pluginId: String, message: String, throwable: Throwable? = null)
    fun error(pluginId: String, message: String, throwable: Throwable? = null)
}
