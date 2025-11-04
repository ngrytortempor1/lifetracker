package com.lifetracker.core.model

/**
 * Ensures an [Event] contains derived metadata for analytics queries while
 * keeping compatibility with legacy payloads lacking explicit metadata.
 */
fun Event.ensureMetadata(): Event {
    if (metadata.tags.isNotEmpty() || metadata.details.isNotEmpty()) {
        return this
    }
    val derived = deriveMetadata(payload)
    return if (derived.tags.isEmpty() && derived.details.isEmpty()) {
        this
    } else {
        copy(metadata = derived)
    }
}

private fun deriveMetadata(payload: EventPayload): EventMetadata = when (payload) {
    is EventPayload.HabitCompleted -> EventMetadata(
        tags = listOf("habit", payload.habitId),
        details = detailMap(
            "habitId" to payload.habitId,
            "notes" to payload.notes
        )
    )
    is EventPayload.TaskCompleted -> EventMetadata(
        tags = buildList {
            add("task")
            add(payload.taskId)
            payload.projectId?.let { add(it) }
        },
        details = detailMap(
            "taskId" to payload.taskId,
            "projectId" to payload.projectId,
            "notes" to payload.completionNotes
        )
    )
    is EventPayload.QuickLog -> EventMetadata(
        tags = listOf("quick-log", payload.tag),
        details = detailMap(
            "tag" to payload.tag,
            "value" to payload.value,
            "context" to payload.context
        )
    )
    is EventPayload.PomodoroCompleted -> EventMetadata(
        tags = buildList {
            add("pomodoro")
            add(payload.targetType.name.lowercase())
            payload.targetId?.let { add(it) }
        },
        details = detailMap(
            "targetType" to payload.targetType.name,
            "targetId" to payload.targetId,
            "focusSeconds" to payload.focusDurationSeconds,
            "breakSeconds" to payload.breakDurationSeconds,
            "startedAt" to payload.startedAt,
            "endedAt" to payload.endedAt,
            "interrupted" to payload.interrupted
        )
    )
}

private fun detailMap(vararg entries: Pair<String, Any?>): Map<String, String> =
    entries.mapNotNull { (key, value) ->
        value?.let { key to it.toString() }
    }.toMap()
