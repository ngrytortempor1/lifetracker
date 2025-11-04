package com.lifetracker.core.repository

import com.lifetracker.core.model.MoodEntry
import com.lifetracker.core.model.SleepSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for mood and sleep data.
 * UI layers consume the exposed flows while concrete implementations handle persistence.
 */
interface WellnessRepository {
    val moodEntries: Flow<List<MoodEntry>>
    val sleepSessions: Flow<List<SleepSession>>

    suspend fun initialize()

    suspend fun recordMoodEntry(entry: MoodEntry)
    suspend fun replaceMoodEntries(entries: List<MoodEntry>)

    suspend fun recordSleepSession(session: SleepSession)
    suspend fun replaceSleepSessions(sessions: List<SleepSession>)
}
