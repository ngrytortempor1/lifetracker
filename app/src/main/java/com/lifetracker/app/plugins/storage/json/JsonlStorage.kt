package com.lifetracker.app.plugins.storage.json

import android.content.Context
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePluginLogger
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

/**
 * JSONL (JSON Lines) based implementation of [LifeTrackerStorage].
 * Files are written beneath the directory resolved by [StorageLocationManager].
 */
class JsonlStorage(
    private val context: Context,
    private val storageLocationManager: StorageLocationManager,
    private val logger: StoragePluginLogger
) : LifeTrackerStorage {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val baseDir: File
        get() = storageLocationManager.resolveDirectory(context)

    private val eventsFile: File
        get() = File(baseDir, EVENTS_FILE)

    private val habitsFile: File
        get() = File(baseDir, HABITS_FILE)

    private val tagsFile: File
        get() = File(baseDir, TAGS_FILE)

    private val taskListsFile: File
        get() = File(baseDir, TASK_LISTS_FILE)

    private val tasksFile: File
        get() = File(baseDir, TASKS_FILE)

    private val moodEntriesFile: File
        get() = File(baseDir, MOOD_ENTRIES_FILE)

    private val sleepSessionsFile: File
        get() = File(baseDir, SLEEP_SESSIONS_FILE)

    override suspend fun appendEvent(event: Event) = withContext(Dispatchers.IO) {
        val enriched = event.ensureMetadata()
        val jsonLine = json.encodeToString(enriched)
        eventsFile.appendText("$jsonLine\n")
    }

    internal suspend fun readEvents(): List<Event> = withContext(Dispatchers.IO) {
        if (!eventsFile.exists()) return@withContext emptyList()

        eventsFile.readLines()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching { json.decodeFromString<Event>(line).ensureMetadata() }
                    .onFailure {
                        logger.warn(
                            JsonStoragePlugin.id,
                            "Failed to decode event line; skipping",
                            it
                        )
                    }
                    .getOrNull()
            }
            .toList()
    }

    override suspend fun readEventsByDateRange(startDate: Instant, endDate: Instant): List<Event> =
        withContext(Dispatchers.IO) {
            readEvents().filter { event ->
                val timestamp = Instant.parse(event.timestamp)
                timestamp.isAfter(startDate) && timestamp.isBefore(endDate)
            }
        }

    override suspend fun saveHabits(habits: List<Habit>) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(habits)
        habitsFile.writeText(jsonString)
    }

    override suspend fun readHabits(): List<Habit> = withContext(Dispatchers.IO) {
        readJsonList(habitsFile, emptyList<Habit>()) { data ->
            json.decodeFromString<List<Habit>>(data)
        }
    }

    override suspend fun saveTags(tags: List<QuickLogTag>) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(tags)
        tagsFile.writeText(jsonString)
    }

    override suspend fun readTags(): List<QuickLogTag> = withContext(Dispatchers.IO) {
        readJsonList(tagsFile, emptyList<QuickLogTag>()) { data ->
            json.decodeFromString<List<QuickLogTag>>(data)
        }
    }

    override fun getExportFiles(): List<File> =
        listOf(
            eventsFile,
            habitsFile,
            tagsFile,
            taskListsFile,
            tasksFile,
            moodEntriesFile,
            sleepSessionsFile
        )

    override suspend fun saveTaskLists(lists: List<TaskList>) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(lists)
        taskListsFile.writeText(jsonString)
    }

    override suspend fun readTaskLists(): List<TaskList> = withContext(Dispatchers.IO) {
        readJsonList(taskListsFile, emptyList<TaskList>()) { data ->
            json.decodeFromString<List<TaskList>>(data)
        }
    }

    override suspend fun saveTasks(tasks: List<Task>) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(tasks)
        tasksFile.writeText(jsonString)
    }

    override suspend fun readTasks(): List<Task> = withContext(Dispatchers.IO) {
        readJsonList(tasksFile, emptyList<Task>()) { data ->
            json.decodeFromString<List<Task>>(data)
        }
    }

    override suspend fun saveMoodEntries(entries: List<MoodEntry>) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(entries)
        moodEntriesFile.writeText(jsonString)
    }

    override suspend fun readMoodEntries(): List<MoodEntry> = withContext(Dispatchers.IO) {
        readJsonList(moodEntriesFile, emptyList<MoodEntry>()) { data ->
            json.decodeFromString<List<MoodEntry>>(data)
        }
    }

    override suspend fun saveSleepSessions(sessions: List<SleepSession>) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(sessions)
        sleepSessionsFile.writeText(jsonString)
    }

    override suspend fun readSleepSessions(): List<SleepSession> = withContext(Dispatchers.IO) {
        readJsonList(sleepSessionsFile, emptyList<SleepSession>()) { data ->
            json.decodeFromString<List<SleepSession>>(data)
        }
    }

    private inline fun <T> readJsonList(
        file: File,
        defaultValue: T,
        crossinline decoder: (String) -> T
    ): T {
        if (!file.exists()) return defaultValue
        val encoded = runCatching { file.readText() }.getOrElse {
            logger.error(JsonStoragePlugin.id, "Failed to read ${file.name}", it)
            return defaultValue
        }
        if (encoded.isBlank()) return defaultValue
        return runCatching { decoder(encoded) }.getOrElse {
            logger.warn(JsonStoragePlugin.id, "Failed to decode ${file.name}; returning default", it)
            defaultValue
        }
    }

    companion object {
        private const val EVENTS_FILE = "events.jsonl"
        private const val HABITS_FILE = "habits.json"
        private const val TAGS_FILE = "tags.json"
        private const val TASK_LISTS_FILE = "task_lists.json"
        private const val TASKS_FILE = "tasks.json"
        private const val MOOD_ENTRIES_FILE = "mood_entries.json"
        private const val SLEEP_SESSIONS_FILE = "sleep_sessions.json"
    }
}
