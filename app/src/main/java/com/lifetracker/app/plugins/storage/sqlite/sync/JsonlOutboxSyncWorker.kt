package com.lifetracker.app.plugins.storage.sqlite.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifetracker.app.core.storage.AndroidStoragePluginLogger
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.plugins.storage.json.JsonStoragePlugin
import com.lifetracker.app.plugins.storage.sqlite.SqliteStoragePlugin
import com.lifetracker.app.plugins.storage.sqlite.db.LifeTrackerDatabase
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.ensureMetadata
import kotlinx.serialization.json.Json

class JsonlOutboxSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val logger = AndroidStoragePluginLogger()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun doWork(): Result {
        return runCatching {
            val database = LifeTrackerDatabase.getInstance(applicationContext)
            val dao = database.lifeTrackerDao()
            val pending = dao.getPendingOutbox(MAX_BATCH)
            if (pending.isEmpty()) {
                return Result.success()
            }

            val storageLocationManager = StorageLocationManager(applicationContext)
            val jsonStorage = JsonStoragePlugin.createStorage(
                applicationContext,
                storageLocationManager,
                logger
            )

            pending.forEach { entry ->
                val event = json.decodeFromString<Event>(entry.payloadJson).ensureMetadata()
                jsonStorage.appendEvent(event)
                dao.markOutboxProcessed(entry.id, System.currentTimeMillis())
            }
            logger.info(SqliteStoragePlugin.PLUGIN_ID, "Synced ${pending.size} events to JSONL.")
            Result.success()
        }.getOrElse { throwable ->
            logger.error(SqliteStoragePlugin.PLUGIN_ID, "Outbox sync failed", throwable)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sqlite_jsonl_outbox_sync"
        private const val MAX_BATCH = 128
    }
}
