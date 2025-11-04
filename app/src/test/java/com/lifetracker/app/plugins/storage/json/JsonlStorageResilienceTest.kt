package com.lifetracker.app.plugins.storage.json

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifetracker.app.core.storage.JsonlStorageLocation
import com.lifetracker.app.core.storage.StorageLocationManager
import com.lifetracker.app.core.storage.StoragePluginLogger
import com.lifetracker.core.model.Event
import com.lifetracker.core.model.EventPayload
import com.lifetracker.core.model.EventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class JsonlStorageResilienceTest {

    private lateinit var context: Context
    private lateinit var storageLocationManager: StorageLocationManager
    private lateinit var baseDir: File
    private lateinit var logger: RecordingLogger
    private lateinit var storage: JsonlStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageLocationManager = StorageLocationManager(context)
        storageLocationManager.setLocation(context, JsonlStorageLocation.INTERNAL_PRIVATE)
        baseDir = storageLocationManager.resolveDirectory(context)
        baseDir.listFiles()?.forEach { it.deleteRecursively() }
        logger = RecordingLogger()
        storage = JsonlStorage(context, storageLocationManager, logger)
    }

    @After
    fun tearDown() {
        baseDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test
    fun readEvents_skipsCorruptLinesAndKeepsValidOnes() = runTest {
        val first = Event(
            eventId = "event-1",
            timestamp = Instant.parse("2025-03-10T10:00:00Z").toString(),
            type = EventType.LOG_QUICK,
            payload = EventPayload.QuickLog(tag = "energy", value = 4.0)
        )
        val second = Event(
            eventId = "event-2",
            timestamp = Instant.parse("2025-03-10T12:00:00Z").toString(),
            type = EventType.TASK_COMPLETED,
            payload = EventPayload.TaskCompleted(taskId = "task-123")
        )

        storage.appendEvent(first)
        baseDir.resolve("events.jsonl").appendText("not-json\n")
        storage.appendEvent(second)

        val events = storage.readEvents()

        assertEquals(2, events.size)
        assertEquals(setOf("event-1", "event-2"), events.map { it.eventId }.toSet())
        assertTrue(logger.warnMessages.any { it.contains("Failed to decode event line") })
    }

    @Test
    fun readTaskLists_returnsDefaultAndLogsWhenJsonCorrupt() = runTest {
        baseDir.resolve("task_lists.json").writeText("{INVALID JSON")

        val lists = storage.readTaskLists()

        assertTrue(lists.isEmpty())
        assertTrue(logger.warnMessages.any { it.contains("task_lists.json") })
    }

    @Test
    fun readEvents_enrichesLegacyEntriesWithMetadata() = runTest {
        val legacyJson = """
            {"eventId":"legacy","timestamp":"2025-03-10T09:00:00Z","source":"android","type":"LOG_QUICK","payload":{"type":"com.lifetracker.core.model.EventPayload.QuickLog","tag":"focus","value":2.0,"context":null}}
        """.trimIndent()
        baseDir.resolve("events.jsonl").writeText("$legacyJson\n")

        val events = storage.readEvents()
        val event = events.single()

        assertTrue(event.metadata.tags.containsAll(listOf("quick-log", "focus")))
        assertTrue(event.metadata.details.containsKey("tag"))
    }

    private class RecordingLogger : StoragePluginLogger {
        val warnMessages = mutableListOf<String>()
        val errorMessages = mutableListOf<String>()

        override fun info(pluginId: String, message: String) = Unit

        override fun warn(pluginId: String, message: String, throwable: Throwable?) {
            val suffix = throwable?.let { ": ${it.message}" } ?: ""
            warnMessages += "$message$suffix"
        }

        override fun error(pluginId: String, message: String, throwable: Throwable?) {
            val suffix = throwable?.let { ": ${it.message}" } ?: ""
            errorMessages += "$message$suffix"
        }
    }
}
