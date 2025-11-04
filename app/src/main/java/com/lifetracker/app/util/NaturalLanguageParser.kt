package com.lifetracker.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class ParsedTaskMetadata(
    val cleanedTitle: String,
    val dueDate: LocalDate?,
    val reminderTime: Instant?,
    val eventSentence: String?,
    val reminderSentence: String? = null
)

object NaturalLanguageParser {

    private val dateWithYear = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日")
    private val dateMonthDay = Regex("(\\d{1,2})月(\\d{1,2})日")
    private val dateSlash = Regex("(\\d{1,2})/(\\d{1,2})")
    private val daysLater = Regex("(\\d+)日後")
    private val hoursLater = Regex("(\\d+)時間後")
    private val minutesLater = Regex("(\\d+)分後")

    private val hoursBefore = Regex("(\\d+)時間前")
    private val minutesBefore = Regex("(\\d+)分前")
    private val daysBefore = Regex("(\\d+)日前")

    private val weekKeywords = mapOf(
        "週末" to DayOfWeek.SATURDAY
    )

    private val weekdayRegex = Regex("(今週|来週|再来週)?(?:の)?(月|火|水|木|金|土|日)(?:曜日|曜)?")

    private val timeRegex = Regex("(午前|午後)?\\s*(\\d{1,2})(?:時|:)(?:(\\d{1,2})分?)?(半)?")
    private val hmRegex = Regex("(午前|午後)?\\s*(\\d{1,2}):(\\d{2})")
    private val noonRegex = Regex("正午")

    fun parse(rawText: String, now: ZonedDateTime = ZonedDateTime.now()): ParsedTaskMetadata {
        val trimmedInput = rawText.trim()
        if (trimmedInput.isEmpty()) {
            return ParsedTaskMetadata(rawText, null, null, null, null)
        }

        val config = NaturalLanguageConfig.parserConfig
        val terminatorSet = config.segmentTerminators

        val zone = now.zone
        var dueDateTime: ZonedDateTime? = null

        val eventSegmentsRaw = extractSegments(rawText, config.eventPrefixes, terminatorSet)
        val reminderSegmentsRaw = extractSegments(rawText, config.reminderPrefixes, terminatorSet)

        val eventContents = eventSegmentsRaw.map { stripSegmentContent(it, config) }.filter { it.isNotEmpty() }
        val reminderContents = reminderSegmentsRaw.map { stripSegmentContent(it, config) }.filter { it.isNotEmpty() }

        val analysisSource = eventContents.joinToString(" ")
        var workingText = if (analysisSource.isNotEmpty()) analysisSource else trimmedInput

        fun applyDefaultTime(dateTime: ZonedDateTime): ZonedDateTime {
            return dateTime.withHour(config.defaultDueHour)
                .withMinute(config.defaultDueMinute)
                .withSecond(0)
                .withNano(0)
        }

        fun recordDue(candidate: ZonedDateTime?) {
            if (candidate != null) {
                dueDateTime = when {
                    dueDateTime == null -> candidate
                    candidate.isBefore(now) -> dueDateTime
                    else -> candidate
                }
            }
        }

        // 1. Explicit date with year
        dateWithYear.find(workingText)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val date = LocalDate.of(year, month, day)
            recordDue(applyDefaultTime(date.atStartOfDay(zone)))
            workingText = workingText.replace(match.value, " ")
        }

        if (dueDateTime == null) {
            // Month/Day
            dateMonthDay.find(workingText)?.let { match ->
                val month = match.groupValues[1].toInt()
                val day = match.groupValues[2].toInt()
                var year = now.year
                var date = LocalDate.of(year, month, day)
                if (date.isBefore(now.toLocalDate())) {
                    year += 1
                    date = LocalDate.of(year, month, day)
                }
                recordDue(applyDefaultTime(date.atStartOfDay(zone)))
                workingText = workingText.replace(match.value, " ")
            }
        }

        if (dueDateTime == null) {
            dateSlash.find(workingText)?.let { match ->
                val month = match.groupValues[1].toInt()
                val day = match.groupValues[2].toInt()
                var year = now.year
                var date = LocalDate.of(year, month, day)
                if (date.isBefore(now.toLocalDate())) {
                    year += 1
                    date = LocalDate.of(year, month, day)
                }
                recordDue(applyDefaultTime(date.atStartOfDay(zone)))
                workingText = workingText.replace(match.value, " ")
            }
        }

        // Relative days
        for ((word, offset) in config.relativeDayMappings) {
            if (workingText.contains(word)) {
                recordDue(applyDefaultTime(now.plusDays(offset)))
                workingText = workingText.replace(word, " ")
                break
            }
        }

        // X days later
        daysLater.find(workingText)?.let { match ->
            val offset = match.groupValues[1].toLong()
            recordDue(applyDefaultTime(now.plusDays(offset)))
            workingText = workingText.replace(match.value, " ")
        }

        // Week keywords
        weekKeywords.forEach { (keyword, dayOfWeek) ->
            if (workingText.contains(keyword)) {
                val base = now.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                recordDue(applyDefaultTime(base))
                workingText = workingText.replace(keyword, " ")
            }
        }

        // Weekday expressions
        weekdayRegex.find(workingText)?.let { match ->
            val prefix = match.groupValues[1]
            val dayLabel = match.groupValues[2]
            val targetDow = when (dayLabel) {
                "月" -> DayOfWeek.MONDAY
                "火" -> DayOfWeek.TUESDAY
                "水" -> DayOfWeek.WEDNESDAY
                "木" -> DayOfWeek.THURSDAY
                "金" -> DayOfWeek.FRIDAY
                "土" -> DayOfWeek.SATURDAY
                "日" -> DayOfWeek.SUNDAY
                else -> null
            }
            if (targetDow != null) {
                val base = when (prefix) {
                    "今週" -> now.with(TemporalAdjusters.nextOrSame(targetDow))
                    "来週" -> now.plusWeeks(1).with(TemporalAdjusters.nextOrSame(targetDow))
                    "再来週" -> now.plusWeeks(2).with(TemporalAdjusters.nextOrSame(targetDow))
                    else -> now.with(TemporalAdjusters.nextOrSame(targetDow))
                }
                recordDue(applyDefaultTime(base))
                workingText = workingText.replace(match.value, " ")
            }
        }

        // Time detection
        var timeCandidate: LocalTime? = null
        val noonMatch = noonRegex.find(workingText)
        if (noonMatch != null) {
            timeCandidate = LocalTime.NOON
            workingText = workingText.replace(noonMatch.value, " ")
        }

        if (timeCandidate == null) {
            hmRegex.find(workingText)?.let { match ->
                val ampm = match.groupValues[1]
                val hour = match.groupValues[2].toInt()
                val minute = match.groupValues[3].toInt()
                timeCandidate = interpretTime(ampm, hour, minute, false)
                workingText = workingText.replace(match.value, " ")
            }
        }

        if (timeCandidate == null) {
            timeRegex.find(workingText)?.let { match ->
                val ampm = match.groupValues[1]
                val hour = match.groupValues[2].toInt()
                val minuteGroup = match.groupValues[3]
                val half = match.groupValues[4].isNotEmpty()
                var minute = if (minuteGroup.isNotEmpty()) minuteGroup.toInt() else 0
                if (half) minute = 30
                timeCandidate = interpretTime(ampm, hour, minute, half)
                workingText = workingText.replace(match.value, " ")
            }
        }

        if (timeCandidate == null) {
            hoursLater.find(workingText)?.let { match ->
                val hours = match.groupValues[1].toLong()
                val candidate = now.plusHours(hours)
                recordDue(candidate)
                workingText = workingText.replace(match.value, " ")
            }
        }

        if (timeCandidate != null) {
            val targetDateTime = when {
                dueDateTime != null -> dueDateTime!!.withHour(timeCandidate!!.hour).withMinute(timeCandidate!!.minute)
                else -> {
                    var candidate = now.withHour(timeCandidate!!.hour).withMinute(timeCandidate!!.minute)
                    if (candidate.isBefore(now)) {
                        candidate = candidate.plusDays(1)
                    }
                    candidate
                }
            }
            recordDue(targetDateTime)
        }

        var reminderInstant = dueDateTime
            ?.minusMinutes(config.defaultReminderLeadMinutes)
            ?.toInstant()

        val reminderOverride = parseReminderSegments(reminderContents, dueDateTime, now, config)
        if (reminderOverride != null) {
            reminderInstant = reminderOverride
        }

        val cleanedSource = removeSegments(rawText, eventSegmentsRaw + reminderSegmentsRaw)

        val cleanedTitle = cleanedSource
            .trim { ch -> ch.isWhitespace() || terminatorSet.contains(ch) }
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        val eventSentence = eventSegmentsRaw.lastOrNull()
            ?.let { stripSegmentContent(it, config) }
            ?.takeIf { it.isNotEmpty() }

        val reminderSentence = reminderSegmentsRaw.lastOrNull()
            ?.let { stripSegmentContent(it, config) }
            ?.takeIf { it.isNotEmpty() }

        return ParsedTaskMetadata(
            cleanedTitle = if (cleanedTitle.isNotEmpty()) cleanedTitle else trimmedInput,
            dueDate = dueDateTime?.toLocalDate(),
            reminderTime = reminderInstant,
            eventSentence = eventSentence,
            reminderSentence = reminderSentence
        )
    }

    private fun interpretTime(ampm: String?, hour: Int, minute: Int, isHalf: Boolean): LocalTime {
        var adjustedHour = hour
        if (ampm == "午後" && hour < 12) {
            adjustedHour += 12
        }
        if (ampm == "午前" && hour == 12) {
            adjustedHour = 0
        }
        if (isHalf && minute == 0) {
            return LocalTime.of(adjustedHour, 30)
        }
        return LocalTime.of(adjustedHour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun extractSegments(
        text: String,
        prefixes: List<Char>,
        terminators: Set<Char>
    ): List<String> {
        if (prefixes.isEmpty()) return emptyList()
        val segments = mutableListOf<String>()
        var index = 0
        val length = text.length
        while (index < length) {
            val ch = text[index]
            if (prefixes.contains(ch)) {
                val start = index
                index++
                while (index < length && !terminators.contains(text[index])) {
                    index++
                }
                val segment = text.substring(start, index)
                if (segment.length > 1) {
                    segments += segment
                }
            } else {
                index++
            }
        }
        return segments
    }

    private fun stripSegmentContent(
        segment: String,
        config: NaturalLanguageConfig.ParserConfig
    ): String {
        if (segment.isEmpty()) return segment
        return segment.drop(1)
            .trim()
            .trimEnd(*config.segmentTerminators.toCharArray())
            .trim()
    }

    private fun removeSegments(text: String, segments: List<String>): String {
        var result = text
        segments.forEach { segment ->
            result = result.replace(segment, " ")
        }
        return result
    }

    private fun parseReminderSegments(
        contents: List<String>,
        dueDateTime: ZonedDateTime?,
        now: ZonedDateTime,
        config: NaturalLanguageConfig.ParserConfig
    ): Instant? {
        if (contents.isEmpty()) return null
        val baseForBefore = dueDateTime ?: now

        for (content in contents.asReversed()) {
            val text = content.trim()
            if (text.isEmpty()) continue

            hoursBefore.find(text)?.let { match ->
                val hours = match.groupValues[1].toLong()
                return baseForBefore.minusHours(hours).toInstant()
            }

            minutesBefore.find(text)?.let { match ->
                val minutes = match.groupValues[1].toLong()
                return baseForBefore.minusMinutes(minutes).toInstant()
            }

            daysBefore.find(text)?.let { match ->
                val days = match.groupValues[1].toLong()
                return baseForBefore.minusDays(days).toInstant()
            }

            hoursLater.find(text)?.let { match ->
                val hours = match.groupValues[1].toLong()
                return now.plusHours(hours).toInstant()
            }

            daysLater.find(text)?.let { match ->
                val days = match.groupValues[1].toLong()
                return now.plusDays(days).toInstant()
            }

            minutesLater.find(text)?.let { match ->
                val minutes = match.groupValues[1].toLong()
                return now.plusMinutes(minutes).toInstant()
            }

            noonRegex.find(text)?.let {
                val anchor = dueDateTime ?: now
                var candidate = anchor.with(LocalTime.NOON)
                if (dueDateTime == null && candidate.isBefore(now)) {
                    candidate = candidate.plusDays(1)
                }
                return candidate.toInstant()
            }

            hmRegex.find(text)?.let { match ->
                val ampm = match.groupValues[1]
                val hour = match.groupValues[2].toInt()
                val minute = match.groupValues[3].toInt()
                val time = interpretTime(ampm, hour, minute, false)
                val anchor = dueDateTime ?: now
                var candidate = anchor.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
                if (dueDateTime == null && candidate.isBefore(now)) {
                    candidate = candidate.plusDays(1)
                }
                return candidate.toInstant()
            }

            timeRegex.find(text)?.let { match ->
                val ampm = match.groupValues[1]
                val hour = match.groupValues[2].toInt()
                val minuteGroup = match.groupValues[3]
                val half = match.groupValues[4].isNotEmpty()
                var minute = if (minuteGroup.isNotEmpty()) minuteGroup.toInt() else 0
                if (half) minute = 30
                val time = interpretTime(ampm, hour, minute, half)
                val anchor = dueDateTime ?: now
                var candidate = anchor.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
                if (dueDateTime == null && candidate.isBefore(now)) {
                    candidate = candidate.plusDays(1)
                }
                return candidate.toInstant()
            }
        }

        return null
    }
}
