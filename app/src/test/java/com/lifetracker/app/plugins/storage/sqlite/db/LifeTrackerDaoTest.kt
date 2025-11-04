package com.lifetracker.app.plugins.storage.sqlite.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lifetracker.app.plugins.storage.sqlite.db.dao.LifeTrackerDao
import com.lifetracker.app.plugins.storage.sqlite.db.entities.EventEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.OutboxEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LifeTrackerDaoTest {

    private lateinit var database: LifeTrackerDatabase
    private lateinit var dao: LifeTrackerDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LifeTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.lifeTrackerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndFetchTasks_roundTripsInInsertionOrder() = runTest {
        val first = TaskEntity(
            id = "task-1",
            listId = "list-1",
            createdAt = "2025-01-01T08:00:00Z",
            json = "{\"id\":\"task-1\"}"
        )
        val second = TaskEntity(
            id = "task-2",
            listId = "list-1",
            createdAt = "2025-01-02T08:00:00Z",
            json = "{\"id\":\"task-2\"}"
        )

        dao.insertTasks(listOf(first, second))
        val tasks = dao.getTasks()

        assertEquals(2, tasks.size)
        assertEquals(listOf("task-1", "task-2"), tasks.map { it.id })
    }

    @Test
    fun getPendingOutbox_returnsOnlyUnprocessedInCreatedOrder() = runTest {
        val now = System.currentTimeMillis()
        val pendingEarly = OutboxEntity(
            id = "outbox-early",
            payloadJson = "{}",
            createdAt = now - 10_000L
        )
        val pendingRecent = OutboxEntity(
            id = "outbox-recent",
            payloadJson = "{}",
            createdAt = now - 1_000L
        )
        val processed = OutboxEntity(
            id = "outbox-processed",
            payloadJson = "{}",
            createdAt = now - 5_000L,
            processedAt = now - 500L
        )

        dao.insertOutbox(pendingRecent)
        dao.insertOutbox(processed)
        dao.insertOutbox(pendingEarly)

        val pending = dao.getPendingOutbox(limit = 10)
        assertEquals(listOf("outbox-early", "outbox-recent"), pending.map { it.id })

        val processedAt = now
        dao.markOutboxProcessed("outbox-early", processedAt)

        val remaining = dao.getPendingOutbox(limit = 10)
        assertEquals(listOf("outbox-recent"), remaining.map { it.id })
        assertTrue(dao.getPendingOutbox(1).first().processedAt == null)
    }

    @Test
    fun countEventsByType_groupsEventsWithinWindow() = runTest {
        val start = "2025-03-01T00:00:00Z"
        val within = listOf(
            EventEntity(
                eventId = UUID.randomUUID().toString(),
                timestamp = "2025-03-05T10:00:00Z",
                type = "TASK_COMPLETED",
                json = "{}",
                tagsJson = "[]",
                detailsJson = "{}"
            ),
            EventEntity(
                eventId = UUID.randomUUID().toString(),
                timestamp = "2025-03-05T12:00:00Z",
                type = "TASK_COMPLETED",
                json = "{}",
                tagsJson = "[]",
                detailsJson = "{}"
            ),
            EventEntity(
                eventId = UUID.randomUUID().toString(),
                timestamp = "2025-03-06T09:00:00Z",
                type = "LOG_QUICK",
                json = "{}",
                tagsJson = "[]",
                detailsJson = "{}"
            )
        )
        val outside = EventEntity(
            eventId = UUID.randomUUID().toString(),
            timestamp = "2025-04-01T00:00:00Z",
            type = "TASK_COMPLETED",
            json = "{}",
            tagsJson = "[]",
            detailsJson = "{}"
        )

        within.forEach { dao.insertEvent(it) }
        dao.insertEvent(outside)

        val counts = dao.countEventsByType(
            start,
            end = "2025-03-31T23:59:59Z"
        )

        val typeToCount = counts.associate { it.type to it.count }
        assertEquals(2L, typeToCount["TASK_COMPLETED"])
        assertEquals(1L, typeToCount["LOG_QUICK"])
        assertTrue(!typeToCount.containsKey("outside"))
    }
}
