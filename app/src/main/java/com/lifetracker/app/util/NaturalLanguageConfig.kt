package com.lifetracker.app.util

import java.util.LinkedHashMap

/**
 * 言語解析に関する設定値をまとめて管理するためのコンフィグ。
 * 必要に応じて [updateParserConfig] を呼び出すことで動的に調整可能。
 */
object NaturalLanguageConfig {

    data class ParserConfig(
        val eventPrefixes: List<Char> = listOf('！'),
        val reminderPrefixes: List<Char> = listOf('？'),
        val segmentTerminators: Set<Char> = setOf('、', '。', '！', '!', '？', '?', '…', '　', ' '),
        val defaultDueHour: Int = 9,
        val defaultDueMinute: Int = 0,
        val defaultReminderLeadMinutes: Long = 30,
        val relativeDayMappings: LinkedHashMap<String, Long> = linkedMapOf(
            "今日" to 0L,
            "明日" to 1L,
            "明後日" to 2L,
            "明々後日" to 3L
        )
    )

    @Volatile
    private var _parserConfig: ParserConfig = ParserConfig()

    val parserConfig: ParserConfig
        get() = _parserConfig

    fun updateParserConfig(transform: (ParserConfig) -> ParserConfig) {
        _parserConfig = transform(_parserConfig)
    }
}
