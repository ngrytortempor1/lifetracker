package com.lifetracker.app.settings

import android.content.Context
import androidx.core.content.edit
import com.lifetracker.app.util.NaturalLanguageConfig
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * 永続化された自然言語解析設定を管理し、ランタイムへ反映する。
 */
class NaturalLanguageSettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        val loaded = loadFromPreferences()
        NaturalLanguageConfig.updateParserConfig { loaded }
    }

    private fun loadFromPreferences(): NaturalLanguageConfig.ParserConfig {
        val eventPrefixes = prefs.getString(KEY_EVENT_PREFIXES, DEFAULT_EVENT_PREFIX) ?: DEFAULT_EVENT_PREFIX
        val reminderPrefixes = prefs.getString(KEY_REMINDER_PREFIXES, DEFAULT_REMINDER_PREFIX) ?: DEFAULT_REMINDER_PREFIX
        val dueHour = prefs.getInt(KEY_DEFAULT_DUE_HOUR, DEFAULT_DUE_HOUR).coerceIn(0, 23)
        val dueMinute = prefs.getInt(KEY_DEFAULT_DUE_MINUTE, DEFAULT_DUE_MINUTE).coerceIn(0, 59)
        val reminderLead = prefs.getLong(KEY_REMINDER_LEAD_MINUTES, DEFAULT_REMINDER_LEAD_MINUTES).coerceAtLeast(0L)
        val relativeDaysSerialized = prefs.getString(KEY_RELATIVE_DAY_MAPPINGS, null)
        val relativeDays = deserializeRelativeDayMappings(relativeDaysSerialized)

        val eventChars = eventPrefixes.filter { !it.isWhitespace() }.toCollection(LinkedHashSet())
        val reminderChars = reminderPrefixes.filter { !it.isWhitespace() }.toCollection(LinkedHashSet())

        return NaturalLanguageConfig.ParserConfig(
            eventPrefixes = if (eventChars.isEmpty()) listOf(DEFAULT_EVENT_PREFIX.first()) else eventChars.toList(),
            reminderPrefixes = if (reminderChars.isEmpty()) listOf(DEFAULT_REMINDER_PREFIX.first()) else reminderChars.toList(),
            segmentTerminators = NaturalLanguageConfig.parserConfig.segmentTerminators,
            defaultDueHour = dueHour,
            defaultDueMinute = dueMinute,
            defaultReminderLeadMinutes = reminderLead,
            relativeDayMappings = relativeDays
        )
    }

    fun getCurrentConfig(): NaturalLanguageConfig.ParserConfig = NaturalLanguageConfig.parserConfig

    fun updateConfig(newConfig: NaturalLanguageConfig.ParserConfig) {
        val eventString = newConfig.eventPrefixes.joinToString("")
        val reminderString = newConfig.reminderPrefixes.joinToString("")

        prefs.edit {
            putString(KEY_EVENT_PREFIXES, eventString)
            putString(KEY_REMINDER_PREFIXES, reminderString)
            putInt(KEY_DEFAULT_DUE_HOUR, newConfig.defaultDueHour.coerceIn(0, 23))
            putInt(KEY_DEFAULT_DUE_MINUTE, newConfig.defaultDueMinute.coerceIn(0, 59))
            putLong(KEY_REMINDER_LEAD_MINUTES, newConfig.defaultReminderLeadMinutes.coerceAtLeast(0L))
            putString(KEY_RELATIVE_DAY_MAPPINGS, serializeRelativeDayMappings(newConfig.relativeDayMappings))
        }
        NaturalLanguageConfig.updateParserConfig { current ->
            current.copy(
                eventPrefixes = newConfig.eventPrefixes,
                reminderPrefixes = newConfig.reminderPrefixes,
                defaultDueHour = newConfig.defaultDueHour.coerceIn(0, 23),
                defaultDueMinute = newConfig.defaultDueMinute.coerceIn(0, 59),
                defaultReminderLeadMinutes = newConfig.defaultReminderLeadMinutes.coerceAtLeast(0L),
                relativeDayMappings = LinkedHashMap(newConfig.relativeDayMappings)
            )
        }
    }

    companion object {
        private const val PREF_NAME = "natural_language_settings"
        private const val KEY_EVENT_PREFIXES = "event_prefixes"
        private const val KEY_REMINDER_PREFIXES = "reminder_prefixes"
        private const val KEY_DEFAULT_DUE_HOUR = "default_due_hour"
        private const val KEY_DEFAULT_DUE_MINUTE = "default_due_minute"
        private const val KEY_REMINDER_LEAD_MINUTES = "default_reminder_lead_minutes"
        private const val KEY_RELATIVE_DAY_MAPPINGS = "relative_day_mappings"

        private const val DEFAULT_EVENT_PREFIX = "！"
        private const val DEFAULT_REMINDER_PREFIX = "？"
        private const val DEFAULT_DUE_HOUR = 9
        private const val DEFAULT_DUE_MINUTE = 0
        private const val DEFAULT_REMINDER_LEAD_MINUTES = 30L

        private val DEFAULT_RELATIVE_DAY_MAPPINGS: LinkedHashMap<String, Long> = linkedMapOf(
            "今日" to 0L,
            "明日" to 1L,
            "明後日" to 2L,
            "明々後日" to 3L
        )

        private fun serializeRelativeDayMappings(map: LinkedHashMap<String, Long>): String {
            return map.entries.joinToString(separator = "|") { "${it.key}=${it.value}" }
        }

        private fun deserializeRelativeDayMappings(raw: String?): LinkedHashMap<String, Long> {
            if (raw.isNullOrBlank()) return LinkedHashMap(DEFAULT_RELATIVE_DAY_MAPPINGS)
            val result = LinkedHashMap<String, Long>()
            raw.split("|").forEach { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].toLongOrNull()
                    if (key.isNotEmpty() && value != null) {
                        result[key] = value
                    }
                }
            }
            return if (result.isEmpty()) LinkedHashMap(DEFAULT_RELATIVE_DAY_MAPPINGS) else result
        }
    }
}
