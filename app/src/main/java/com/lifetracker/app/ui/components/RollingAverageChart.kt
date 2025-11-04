package com.lifetracker.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifetracker.core.analytics.RollingAveragePoint
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

@Composable
fun RollingAverageChart(
    points: List<RollingAveragePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF26A69A),
    areaColor: Color = Color(0x2626A69A),
    pointColor: Color = Color(0xFF00897B),
    axisColor: Color = Color.LightGray,
    descriptionZoneId: ZoneId = ZoneId.systemDefault()
) {
    var selectedPoint by remember(points) { mutableStateOf<ChartEntry?>(null) }
    var chartEntries by remember(points) { mutableStateOf(emptyList<ChartEntry>()) }
    val formatter = remember { DateTimeFormatter.ofPattern("M/d").withZone(descriptionZoneId) }
    val semanticsDescription = remember(points) { buildRollingAverageContentDescription(points, descriptionZoneId) }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .semantics { contentDescription = semanticsDescription }
            .pointerInput(points, chartEntries) {
                if (points.isEmpty()) return@pointerInput
                detectTapGestures { offset ->
                    selectedPoint = nearestEntry(offset, chartEntries)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (points.isEmpty()) {
                drawEmptyState(axisColor)
                return@Canvas
            }

            val horizontalPadding = 48.dp.toPx()
            val verticalPadding = 32.dp.toPx()
            val chartWidth = size.width - horizontalPadding * 2
            val chartHeight = size.height - verticalPadding * 2
            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            val minTime = points.minOf { it.windowStart.epochSecond }
            val maxTime = points.maxOf { it.windowEnd.epochSecond }
            val timeSpan = max(1L, maxTime - minTime)

            val minValue = points.minOf { it.average }
            val maxValue = points.maxOf { it.average }
            val valueRange = if ((maxValue - minValue).absoluteValue < 1e-6) 1.0 else maxValue - minValue

            val yScale = chartHeight / valueRange.toFloat()
            val yOffset = verticalPadding + chartHeight

            // axes
            drawLine(
                color = axisColor,
                start = Offset(horizontalPadding, verticalPadding),
                end = Offset(horizontalPadding, yOffset)
            )
            drawLine(
                color = axisColor,
                start = Offset(horizontalPadding, yOffset),
                end = Offset(horizontalPadding + chartWidth, yOffset)
            )

            val entriesList = points.map { point ->
                val centerTime = point.windowEnd.epochSecond
                val relativeTime = (centerTime - minTime).toFloat() / timeSpan.toFloat()
                val x = horizontalPadding + relativeTime * chartWidth
                val y = yOffset - ((point.average - minValue) * yScale).toFloat()
                ChartEntry(point = point, offset = Offset(x, y))
            }
            chartEntries = entriesList

            // area under line
            val areaPath = Path().apply {
                entriesList.firstOrNull()?.let { first ->
                    moveTo(first.offset.x, yOffset)
                    lineTo(first.offset.x, first.offset.y)
                    entriesList.drop(1).forEach { entry ->
                        lineTo(entry.offset.x, entry.offset.y)
                    }
                    entriesList.lastOrNull()?.let { last ->
                        lineTo(last.offset.x, yOffset)
                    }
                    close()
                }
            }
            drawPath(path = areaPath, color = areaColor)

            // line
            val linePath = Path().apply {
                entriesList.firstOrNull()?.let { first ->
                    moveTo(first.offset.x, first.offset.y)
                    entriesList.drop(1).forEach { entry ->
                        lineTo(entry.offset.x, entry.offset.y)
                    }
                }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // points
            entriesList.forEach { entry ->
                drawCircle(
                    color = pointColor,
                    radius = 5.dp.toPx(),
                    center = entry.offset
                )
            }

            selectedPoint?.let { chosen ->
                drawLine(
                    color = lineColor.copy(alpha = 0.35f),
                    start = Offset(chosen.offset.x, verticalPadding),
                    end = Offset(chosen.offset.x, yOffset),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = chosen.offset
                )
                drawCircle(
                    color = pointColor,
                    radius = 5.dp.toPx(),
                    center = chosen.offset
                )
            }

            // x ticks
            val tickCount = min(5, entriesList.size).coerceAtLeast(1)
            val step = max(1, entriesList.size / tickCount)
            entriesList.chunked(step).forEach { chunk ->
                val entry = chunk.first()
                val label = formatter.format(entry.point.windowEnd)
                drawText(label, Offset(entry.offset.x, yOffset + 20.dp.toPx()), axisColor)
            }
        }

        selectedPoint?.let { entry ->
            Tooltip(entry = entry)
        }
    }
}

private fun nearestEntry(target: Offset, entries: List<ChartEntry>): ChartEntry? {
    if (entries.isEmpty()) return null
    return entries.minByOrNull { (it.offset.x - target.x).absoluteValue }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Tooltip(entry: ChartEntry) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .align(Alignment.TopStart),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())
                    .format(entry.point.windowEnd),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "平均値: ${String.format(Locale.JAPAN, "%.2f", entry.point.average)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyState(color: Color) {
    drawText("データがありません", center, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawText(
    text: String,
    position: Offset,
    color: Color
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = 12.dp.toPx()
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
}

private data class ChartEntry(
    val point: RollingAveragePoint,
    val offset: Offset
)

internal fun buildRollingAverageContentDescription(
    points: List<RollingAveragePoint>,
    zoneId: ZoneId
): String {
    if (points.isEmpty()) {
        return "Rolling average chart has no data."
    }
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(zoneId)
    return buildString {
        append("Rolling average chart with ")
        append(points.size)
        append(" points.")
        points.forEachIndexed { index, point ->
            append(" Point ")
            append(index + 1)
            append(':')
            append(' ')
            append(formatter.format(point.windowStart))
            append(" to ")
            append(formatter.format(point.windowEnd))
            append(", value ")
            append(String.format(Locale.US, "%.2f", point.average))
            append('.')
        }
    }
}
