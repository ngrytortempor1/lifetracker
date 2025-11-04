package com.lifetracker.app.plugins.storage.sqlite

import android.content.Context
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePluginLogger
import com.lifetracker.app.plugins.storage.json.JsonStoragePlugin
import com.lifetracker.app.plugins.storage.json.JsonlStorage
import com.lifetracker.app.plugins.storage.sqlite.db.LifeTrackerDatabase
import com.lifetracker.app.plugins.storage.sqlite.db.dao.LifeTrackerDao
import com.lifetracker.app.plugins.storage.sqlite.db.entities.EventEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.HabitEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.MoodEntryEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.OutboxEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.QuickLogTagEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.SleepSessionEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskListEntity
import com.lifetracker.app.plugins.storage.sqlite.sync.JsonlOutboxSyncWorker
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.Habit
import com.lifetracker.core.model.MoodEntry
import com.lifetracker.core.model.QuickLogTag
import com.lifetracker.core.model.SleepSession
import com.lifetracker.core.model.Task
import com.lifetracker.core.model.TaskList
import com.lifetracker.core.model.ensureMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class SqliteStorage(
    context: Context,
    private val storageLocationManager: StorageLocationManager,
    private val logger: StoragePluginLogger
) : LifeTrackerStorage {

    private val applicationContext = context.applicationContext
    private val database: LifeTrackerDatabase = LifeTrackerDatabase.getInstance(applicationContext)
    private val dao: LifeTrackerDao = database.lifeTrackerDao()
    private val jsonStorage: LifeTrackerStorage by lazy {
        JsonStoragePlugin.createStorage(applicationContext, storageLocationManager, logger)
    }
    private val workManager: WorkManager = WorkManager.getInstance(applicationContext)
    private val seeded = AtomicBoolean(false)

    override suspend fun appendEvent(event: Event) = withContext(Dispatchers.IO) {
        val enriched = event.ensureMetadata()
        val payload = json.encodeToString(enriched)
        database.withTransaction {
            dao.insertEvent(enriched.toEntity(payload))
            dao.insertOutbox(
                OutboxEntity(
                    id = enriched.eventId,
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        scheduleOutboxSync()
        logger.info(SqliteStoragePlugin.PLUGIN_ID, "Event appended and enqueued for JSONL sync.")
    }

    override suspend fun readEventsByDateRange(startDate: Instant, endDate: Instant): List<Event> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            dao.getEventsBetween(startDate.toString(), endDate.toString())
                .mapNotNull { entity ->
                    runCatching { json.decodeFromString<Event>(entity.json).ensureMetadata() }
                        .onFailure {
                            logger.warn(
                                SqliteStoragePlugin.PLUGIN_ID,
                                "Failed to decode event ${entity.eventId}",
                                it
                            )
                        }
                        .getOrNull()
                }
        }

    override fun getExportFiles(): List<File> {
        val databaseFile = LifeTrackerDatabase.databaseFile(applicationContext)
        val files = mutableListOf<File>()
        if (databaseFile.exists()) {
            files += databaseFile
        }
        files += jsonStorage.getExportFiles()
        return files
    }

    override suspend fun saveHabits(habits: List<Habit>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearHabits()
            dao.insertHabits(habits.map { it.toEntity() })
        }
        jsonStorage.saveHabits(habits)
    }

    override suspend fun readHabits(): List<Habit> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getHabits().mapNotNull { entity ->
            runCatching { json.decodeFromString<Habit>(entity.json) }
                .onFailure {
                    logger.warn(
                        SqliteStoragePlugin.PLUGIN_ID,
                        "Failed to decode habit ${entity.id}",
                        it
                    )
                }
                .getOrNull()
        }
    }

    override suspend fun saveTags(tags: List<QuickLogTag>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearQuickLogTags()
            dao.insertQuickLogTags(tags.map { it.toEntity() })
        }
        jsonStorage.saveTags(tags)
    }

    override suspend fun readTags(): List<QuickLogTag> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getQuickLogTags().mapNotNull { entity ->
            runCatching { json.decodeFromString<QuickLogTag>(entity.json) }
                .onFailure {
                    logger.warn(
                        SqliteStoragePlugin.PLUGIN_ID,
                        "Failed to decode tag ${entity.id}",
                        it
                    )
                }
                .getOrNull()
        }
    }

    override suspend fun saveTaskLists(lists: List<TaskList>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearTaskLists()
            dao.insertTaskLists(lists.map { it.toEntity() })
        }
        jsonStorage.saveTaskLists(lists)
    }

    override suspend fun readTaskLists(): List<TaskList> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getTaskLists().mapNotNull { entity ->
            runCatching { json.decodeFromString<TaskList>(entity.json) }
                .onFailure {
                    logger.warn(
                        SqliteStoragePlugin.PLUGIN_ID,
                        "Failed to decode task list ${entity.id}",
                        it
                    )
                }
                .getOrNull()
        }
    }

    override suspend fun saveTasks(tasks: List<Task>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearTasks()
            dao.insertTasks(tasks.map { it.toEntity() })
        }
        jsonStorage.saveTasks(tasks)
    }

    override suspend fun readTasks(): List<Task> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getTasks().mapNotNull { entity ->
            runCatching { json.decodeFromString<Task>(entity.json) }
                .onFailure {
                    logger.warn(
                        SqliteStoragePlugin.PLUGIN_ID,
                        "Failed to decode task ${entity.id}",
                        it
                    )
                }
                .getOrNull()
        }
    }

    override suspend fun saveMoodEntries(entries: List<MoodEntry>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearMoodEntries()
            dao.insertMoodEntries(entries.map { it.toEntity() })
        }
        jsonStorage.saveMoodEntries(entries)
    }

    override suspend fun readMoodEntries(): List<MoodEntry> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getMoodEntries().mapNotNull { entity ->
            runCatching { json.decodeFromString<MoodEntry>(entity.json) }
                .onFailure {
                    logger.warn(
                        SqliteStoragePlugin.PLUGIN_ID,
                        "Failed to decode mood entry ${entity.entryId}",
                        it
                    )
                }
                .getOrNull()
        }
    }

    override suspend fun saveSleepSessions(sessions: List<SleepSession>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearSleepSessions()
            dao.insertSleepSessions(sessions.map { it.toEntity() })
        }
        jsonStorage.saveSleepSessions(sessions)
    }

    override suspend fun readSleepSessions(): List<SleepSession> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getSleepSessions().mapNotNull { entity ->
            runCatching { json.decodeFromString<SleepSession>(entity.json) }
                .onFailure {
                    logger.warn(
                        SqliteStoragePlugin.PLUGIN_ID,
                        "Failed to decode sleep session ${entity.sessionId}",
                        it
                    )
                }
                .getOrNull()
        }
    }

    private fun scheduleOutboxSync() {
        val request = OneTimeWorkRequestBuilder<JsonlOutboxSyncWorker>().build()
        workManager.enqueueUniqueWork(
            JsonlOutboxSyncWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private suspend fun ensureSeeded() {
        if (!seeded.compareAndSet(false, true)) return
        runCatching {
            withContext(Dispatchers.IO) {
                val listsToSeed = if (dao.getTaskLists().isEmpty()) {
                    jsonStorage.readTaskLists()
                } else {
                    emptyList()
                }
                val tasksToSeed = if (dao.getTasks().isEmpty()) {
                    jsonStorage.readTasks()
                } else {
                    emptyList()
                }
                val habitsToSeed = if (dao.getHabits().isEmpty()) {
                    jsonStorage.readHabits()
                } else {
                    emptyList()
                }
                val tagsToSeed = if (dao.getQuickLogTags().isEmpty()) {
                    jsonStorage.readTags()
                } else {
                    emptyList()
                }
                val eventsToSeed = if (dao.countEvents() == 0) {
                    (jsonStorage as? JsonlStorage)?.readEvents().orEmpty()
                } else {
                    emptyList()
                }
                val moodsToSeed = if (dao.getMoodEntries().isEmpty()) {
                    jsonStorage.readMoodEntries()
                } else {
                    emptyList()
                }
                val sleepsToSeed = if (dao.getSleepSessions().isEmpty()) {
                    jsonStorage.readSleepSessions()
                } else {
                    emptyList()
                }

                database.withTransaction {
                    if (listsToSeed.isNotEmpty()) {
                        dao.insertTaskLists(listsToSeed.map { it.toEntity() })
                    }
                    if (tasksToSeed.isNotEmpty()) {
                        dao.insertTasks(tasksToSeed.map { it.toEntity() })
                    }
                    if (habitsToSeed.isNotEmpty()) {
                        dao.insertHabits(habitsToSeed.map { it.toEntity() })
                    }
                    if (tagsToSeed.isNotEmpty()) {
                        dao.insertQuickLogTags(tagsToSeed.map { it.toEntity() })
                    }
                    if (eventsToSeed.isNotEmpty()) {
                        eventsToSeed.map { it.ensureMetadata() }.forEach { dao.insertEvent(it.toEntity()) }
                    }
                    if (moodsToSeed.isNotEmpty()) {
                        dao.insertMoodEntries(moodsToSeed.map { it.toEntity() })
                    }
                    if (sleepsToSeed.isNotEmpty()) {
                        dao.insertSleepSessions(sleepsToSeed.map { it.toEntity() })
                    }
                }
            }
        }.onFailure { throwable ->
            seeded.set(false)
            logger.error(
                SqliteStoragePlugin.PLUGIN_ID,
                "Failed to seed SQLite storage from JSONL",
                throwable
            )
        }
    }

    private fun TaskList.toEntity(): TaskListEntity = TaskListEntity(
        id = id,
        sortOrder = sortOrder,
        json = json.encodeToString(this)
    )

    private fun Task.toEntity(): TaskEntity = TaskEntity(
        id = id,
        listId = listId,
        createdAt = createdAt,
        json = json.encodeToString(this)
    )

    private fun Habit.toEntity(): HabitEntity = HabitEntity(
        id = id,
        createdAt = createdAt,
        json = json.encodeToString(this)
    )

    private fun QuickLogTag.toEntity(): QuickLogTagEntity = QuickLogTagEntity(
        id = id,
        createdAt = createdAt,
        json = json.encodeToString(this)
    )

    private fun Event.toEntity(payload: String = json.encodeToString(this)): EventEntity =
        EventEntity(
            eventId = eventId,
            timestamp = timestamp,
            type = type.name,
            json = payload,
            tagsJson = json.encodeToString(metadata.tags),
            detailsJson = json.encodeToString(metadata.details)
        )

    private fun MoodEntry.toEntity(): MoodEntryEntity = MoodEntryEntity(
        entryId = entryId,
        recordedAt = recordedAt,
        slot = slot.name,
        json = json.encodeToString(this)
    )

    private fun SleepSession.toEntity(): SleepSessionEntity = SleepSessionEntity(
        sessionId = sessionId,
        startedAt = startedAt,
        endedAt = endedAt,
        source = source.name,
        json = json.encodeToString(this)
    )

    companion object {
        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
