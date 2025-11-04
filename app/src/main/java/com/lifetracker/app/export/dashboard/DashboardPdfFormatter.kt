package com.lifetracker.app.export.dashboard

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

class DashboardPdfFormatter {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    fun format(snapshot: DashboardSnapshot): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val titlePaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
        }
        val sectionTitlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
        }
        val valuePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 13f
        }
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
        }

        val contentWidth = pageInfo.pageWidth - (MARGIN * 2)
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var cursorY = MARGIN

        fun ensureSpace(required: Float) {
            if (cursorY + required > pageInfo.pageHeight - MARGIN) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                cursorY = MARGIN
            }
        }

        fun advance(amount: Float) {
            cursorY += amount
        }

        fun drawMultiline(text: String, paint: TextPaint, startX: Float): Float {
            if (text.isBlank()) return 0f
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (contentWidth - (startX - MARGIN)).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(startX, cursorY)
            layout.draw(canvas)
            canvas.restore()
            return layout.height.toFloat()
        }

        // Header
        canvas.drawText("ダッシュボードサマリー", MARGIN, cursorY, titlePaint)
        advance(HEADER_GAP)
        val timestamp = timestampFormatter.format(snapshot.generatedAt.atZone(snapshot.timezone))
        canvas.drawText("作成日時: $timestamp", MARGIN, cursorY, valuePaint)
        advance(LINE_GAP)
        canvas.drawText("タイムゾーン: ${snapshot.timezone.id}", MARGIN, cursorY, valuePaint)
        advance(LINE_GAP)
        canvas.drawText("ロケール: ${snapshot.locale.toLanguageTag()}", MARGIN, cursorY, valuePaint)
        advance(SECTION_GAP)

        snapshot.metrics.forEach { metric ->
            ensureSpace(SECTION_MIN_SPACE)
            canvas.drawText(metric.title, MARGIN, cursorY, sectionTitlePaint)
            advance(LINE_GAP)
            if (metric.value.isNotBlank()) {
                canvas.drawText("値: ${metric.value}", MARGIN + INDENT, cursorY, valuePaint)
                advance(LINE_GAP)
            }
            if (metric.description.isNotBlank()) {
                val used = drawMultiline(metric.description, bodyPaint, MARGIN + INDENT)
                advance(used + LINE_GAP)
            } else {
                advance(LINE_GAP)
            }
            advance(SECTION_GAP)
        }

        document.finishPage(page)

        val output = ByteArrayOutputStream()
        try {
            document.writeTo(output)
        } finally {
            document.close()
        }
        return output.toByteArray()
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val INDENT = 24f
        const val LINE_GAP = 16f
        const val SECTION_GAP = 12f
        const val SECTION_MIN_SPACE = 80f
        const val HEADER_GAP = 28f
    }
}
