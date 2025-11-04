package com.lifetracker.app.plugins.storage.sqlite.db

import androidx.room.TypeConverter
import com.lifetracker.core.model.EventType
import com.lifetracker.core.model.LogType
import com.lifetracker.core.model.TaskRepeatRule

class LifeTrackerTypeConverters {
    @TypeConverter
    fun fromLogType(type: LogType?): String? = type?.name

    @TypeConverter
    fun toLogType(value: String?): LogType? = value?.let { LogType.valueOf(it) }

    @TypeConverter
    fun fromTaskRepeatRule(rule: TaskRepeatRule?): String? = rule?.name

    @TypeConverter
    fun toTaskRepeatRule(value: String?): TaskRepeatRule? =
        value?.let { TaskRepeatRule.valueOf(it) }

    @TypeConverter
    fun fromEventType(type: EventType?): String? = type?.name

    @TypeConverter
    fun toEventType(value: String?): EventType? =
        value?.let { EventType.valueOf(it) }
}
