package com.lifetracker.app.analytics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.app.data.analytics.JsonEventAnalyticsRepository
import com.lifetracker.app.plugins.storage.sqlite.analytics.SqliteEventAnalyticsRepository
import com.lifetracker.app.plugins.storage.sqlite.db.LifeTrackerDatabase
import com.lifetracker.app.plugins.storage.sqlite.db.entities.EventEntity
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.EventPayload
import com.lifetracker.core.model.EventType
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsRepositoryPerformanceTest {

    private lateinit var context: Context
    private lateinit var database: LifeTrackerDatabase
    private lateinit var jsonRepository: EventAnalyticsRepository
    private lateinit var sqliteRepository: EventAnalyticsRepository
    private lateinit var storage: InMemoryStorage
    private lateinit var generatedEvents: List<Event>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LifeTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = InMemoryStorage()
        jsonRepository = JsonEventAnalyticsRepository(storage)
        sqliteRepository = SqliteEventAnalyticsRepository(database.lifeTrackerDao())

        generatedEvents = generateEvents(count = 1200)
        storage.seed(generatedEvents)
        runBlocking {
            val dao = database.lifeTrackerDao()
            generatedEvents.forEach { event ->
                dao.insertEvent(event.toEntity())
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun largeDatasetQueriesRemainResponsive() = runBlocking {
        val start = Instant.parse(generatedEvents.first().timestamp)
        val end = Instant.parse(generatedEvents.last().timestamp)

        // warm-up
        jsonRepository.taskCompletionTransitions(start, end)
        sqliteRepository.taskCompletionTransitions(start, end)

        val jsonDuration = measureTimeMillis {
            jsonRepository.taskCompletionTransitions(start, end)
        }
        val sqliteDuration = measureTimeMillis {
            sqliteRepository.taskCompletionTransitions(start, end)
        }

        val jsonTransitions = jsonRepository.taskCompletionTransitions(start, end)
        val sqliteTransitions = sqliteRepository.taskCompletionTransitions(start, end)
        assertEquals(jsonTransitions.size, sqliteTransitions.size)

        val slower = max(jsonDuration, sqliteDuration)
        val faster = min(jsonDuration, sqliteDuration)
        val ratio = slower.toDouble() / faster.toDouble()

        assertTrue(
            "Expected both repositories to finish within 600ms, got json=${jsonDuration}ms sqlite=${sqliteDuration}ms",
            jsonDuration <= 600 && sqliteDuration <= 600
        )
        assertTrue(
            "Expected runtime parity ratio <= 2.5, got $ratio",
            ratio <= 2.5
        )
    }

    private fun generateEvents(count: Int): List<Event> {
        val base = Instant.parse("2025-01-01T00:00:00Z")
        return List(count) { index ->
            val timestamp = base.plus(index.toLong() * 15, ChronoUnit.MINUTES)
            val taskId = "task-${index % 20}"
            Event(
                timestamp = timestamp.toString(),
                type = EventType.TASK_COMPLETED,
                payload = EventPayload.TaskCompleted(taskId = taskId)
            )
        }
    }

    private fun Event.toEntity(): EventEntity {
        val jsonString = json.encodeToString(this)
        return EventEntity(
            eventId = eventId,
            timestamp = timestamp,
            type = type.name,
            json = jsonString,
            tagsJson = "[]",
            detailsJson = "{}"
        )
    }

    private class InMemoryStorage : LifeTrackerStorage {
        private val events = mutableListOf<Event>()

        fun seed(seedEvents: List<Event>) {
            events.clear()
            events.addAll(seedEvents)
        }

        override suspend fun appendEvent(event: Event) {
            events.add(event)
        }

        override suspend fun readEventsByDateRange(startDate: Instant, endDate: Instant): List<Event> =
            events.filter { event ->
                val instant = Instant.parse(event.timestamp)
                !instant.isBefore(startDate) && !instant.isAfter(endDate)
            }

        override fun getExportFiles(): List<File> = emptyList()

        override suspend fun saveHabits(habits: List<com.lifetracker.core.model.Habit>) = Unit
        override suspend fun readHabits(): List<com.lifetracker.core.model.Habit> = emptyList()
        override suspend fun saveTags(tags: List<com.lifetracker.core.model.QuickLogTag>) = Unit
        override suspend fun readTags(): List<com.lifetracker.core.model.QuickLogTag> = emptyList()
        override suspend fun saveTaskLists(lists: List<com.lifetracker.core.model.TaskList>) = Unit
        override suspend fun readTaskLists(): List<com.lifetracker.core.model.TaskList> = emptyList()
        override suspend fun saveTasks(tasks: List<com.lifetracker.core.model.Task>) = Unit
        override suspend fun readTasks(): List<com.lifetracker.core.model.Task> = emptyList()
        override suspend fun saveMoodEntries(entries: List<com.lifetracker.core.model.MoodEntry>) = Unit
        override suspend fun readMoodEntries(): List<com.lifetracker.core.model.MoodEntry> = emptyList()
        override suspend fun saveSleepSessions(sessions: List<com.lifetracker.core.model.SleepSession>) = Unit
        override suspend fun readSleepSessions(): List<com.lifetracker.core.model.SleepSession> = emptyList()
    }

    companion object {
        private val json = Json { encodeDefaults = true }
    }
}
