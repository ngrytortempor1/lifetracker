package com.lifetracker.app.data.local

import com.lifetracker.app.core.storage.LifeTrackerStorage
import com.lifetracker.core.model.MoodEntry
import com.lifetracker.core.model.SleepSession
import com.lifetracker.core.repository.WellnessRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JSON-backed implementation of [WellnessRepository].
 * Internally keeps in-memory caches so UI can react to updates immediately.
 */
class JsonWellnessRepository(
    private val storage: LifeTrackerStorage
) : WellnessRepository {

    private val mutex = Mutex()

    private val _moodEntries = MutableStateFlow<List<MoodEntry>>(emptyList())
    override val moodEntries: Flow<List<MoodEntry>> = _moodEntries.asStateFlow()

    private val _sleepSessions = MutableStateFlow<List<SleepSession>>(emptyList())
    override val sleepSessions: Flow<List<SleepSession>> = _sleepSessions.asStateFlow()

    override suspend fun initialize() = mutex.withLock {
        _moodEntries.value = storage.readMoodEntries().sortedByMostRecent()
        _sleepSessions.value = storage.readSleepSessions().sortedBySleepStart()
    }

    override suspend fun recordMoodEntry(entry: MoodEntry) = mutex.withLock {
        val updated = (_moodEntries.value.filterNot { it.entryId == entry.entryId } + entry)
            .sortedByMostRecent()
        _moodEntries.value = updated
        storage.saveMoodEntries(updated)
    }

    override suspend fun replaceMoodEntries(entries: List<MoodEntry>) = mutex.withLock {
        val ordered = entries.sortedByMostRecent()
        _moodEntries.value = ordered
        storage.saveMoodEntries(ordered)
    }

    override suspend fun recordSleepSession(session: SleepSession) = mutex.withLock {
        val updated = (
            _sleepSessions.value.filterNot { it.sessionId == session.sessionId } + session
        ).sortedBySleepStart()
        _sleepSessions.value = updated
        storage.saveSleepSessions(updated)
    }

    override suspend fun replaceSleepSessions(sessions: List<SleepSession>) = mutex.withLock {
        val ordered = sessions.sortedBySleepStart()
        _sleepSessions.value = ordered
        storage.saveSleepSessions(ordered)
    }

    private fun List<MoodEntry>.sortedByMostRecent(): List<MoodEntry> =
        sortedByDescending { it.recordedAt.parseInstantFallback() }

    private fun List<SleepSession>.sortedBySleepStart(): List<SleepSession> =
        sortedByDescending { it.startedAt.parseInstantFallback() }

    private fun String.parseInstantFallback(): Instant =
        runCatching { Instant.parse(this) }.getOrElse { Instant.EPOCH }
}
