package com.lifetracker.app.export.dashboard

import java.time.format.DateTimeFormatter

class DashboardCsvFormatter {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun format(snapshot: DashboardSnapshot): ByteArray {
        val builder = StringBuilder()
        builder.appendLine("metric_key,title,value,description")
        snapshot.metrics.forEach { metric ->
            builder.appendLine(toCsvRow(metric.key, metric.title, metric.value, metric.description))
        }

        val timestamp = timestampFormatter.format(snapshot.generatedAt.atZone(snapshot.timezone))
        builder.appendLine(
            toCsvRow(
                "generated_at",
                "",
                timestamp,
                "timezone=${snapshot.timezone.id}"
            )
        )
        builder.appendLine(
            toCsvRow(
                "locale",
                "",
                snapshot.locale.toLanguageTag(),
                ""
            )
        )

        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun toCsvRow(vararg columns: String): String =
        columns.joinToString(separator = ",") { it.toCsvValue() }

    private fun String.toCsvValue(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
