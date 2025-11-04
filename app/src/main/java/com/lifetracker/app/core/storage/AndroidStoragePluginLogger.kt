package com.lifetracker.app.core.storage

import android.util.Log

class AndroidStoragePluginLogger(
    private val tag: String = "StoragePlugin"
) : StoragePluginLogger {

    override fun info(pluginId: String, message: String) {
        Log.i(tag, format(pluginId, message))
    }

    override fun warn(pluginId: String, message: String, throwable: Throwable?) {
        Log.w(tag, format(pluginId, message), throwable)
    }

    override fun error(pluginId: String, message: String, throwable: Throwable?) {
        Log.e(tag, format(pluginId, message), throwable)
    }

    private fun format(pluginId: String, message: String): String =
        "[$pluginId] $message"
}
