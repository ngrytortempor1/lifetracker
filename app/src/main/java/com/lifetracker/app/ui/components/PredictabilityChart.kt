package com.lifetracker.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import com.lifetracker.app.analytics.EventAnalyticsCalculations
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun PredictabilityChart(
    buckets: List<EventAnalyticsCalculations.PredictabilityBucket>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF3F51B5),
    pointColor: Color = Color(0xFF1E88E5),
    insufficientColor: Color = Color(0xFF8E99A2),
    increaseColor: Color = Color(0xFF2E7D32),
    decreaseColor: Color = Color(0xFFC62828),
    descriptionZoneId: ZoneId = ZoneId.systemDefault()
) {
    val semanticsDescription = buildPredictabilityContentDescription(buckets, descriptionZoneId)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .semantics { contentDescription = semanticsDescription }
    ) {
        if (buckets.isEmpty()) {
            drawEmptyState(insufficientColor)
            return@Canvas
        }

        val validBuckets = buckets.filter { it.weightedProbability != null }
        val maxProbability = max(
            1.0,
            validBuckets.maxOfOrNull { it.weightedProbability ?: 0.0 } ?: 1.0
        )
        val minProbability = min(
            0.0,
            validBuckets.minOfOrNull { it.weightedProbability ?: 0.0 } ?: 0.0
        )

        val verticalPadding = 32.dp.toPx()
        val horizontalPadding = 48.dp.toPx()
        val chartWidth = size.width - horizontalPadding * 2
        val chartHeight = size.height - verticalPadding * 2

        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val yScale = chartHeight / (maxProbability - minProbability)
        val yOffset = verticalPadding + chartHeight

        val xSpacing = if (buckets.size <= 1) 0f else chartWidth / (buckets.size - 1)

        // Axes
        drawLine(
            color = Color.LightGray,
            start = Offset(horizontalPadding, verticalPadding),
            end = Offset(horizontalPadding, yOffset)
        )
        drawLine(
            color = Color.LightGray,
            start = Offset(horizontalPadding, yOffset),
            end = Offset(horizontalPadding + chartWidth, yOffset)
        )

        // Horizontal reference lines (0%, 50%, 100%)
        val referenceLevels = listOf(0.0, 0.5, 1.0)
        referenceLevels.forEach { level ->
            val yPosition = yOffset - ((level - minProbability) * yScale).toFloat()
            drawLine(
                color = Color.LightGray.copy(alpha = 0.4f),
                start = Offset(horizontalPadding, yPosition),
                end = Offset(horizontalPadding + chartWidth, yPosition)
            )
        }

        val maxSample = max(1L, buckets.maxOfOrNull { it.sampleSize } ?: 1L)
        val points = buckets.mapIndexed { index, bucket ->
            val x = horizontalPadding + xSpacing * index
            val probability = bucket.weightedProbability
            val y = if (probability != null) {
                yOffset - ((probability - minProbability) * yScale).toFloat()
            } else {
                null
            }
            ChartPoint(x = x, y = y, bucket = bucket)
        }

        // EMA line if available
        val emaPoints = points.mapNotNull { point ->
            val ema = point.bucket.emaProbability ?: return@mapNotNull null
            val y = yOffset - ((ema - minProbability) * yScale).toFloat()
            Offset(point.x, y)
        }
        if (emaPoints.size >= 2) {
            val path = Path().apply {
                moveTo(emaPoints.first().x, emaPoints.first().y)
                for (index in 1 until emaPoints.size) {
                    lineTo(emaPoints[index].x, emaPoints[index].y)
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw points
        val minRadius = 4.dp.toPx()
        val maxRadius = 12.dp.toPx()
        points.forEachIndexed { index, point ->
            val probability = point.bucket.weightedProbability
            val color = if (probability != null) {
                val trendDelta = point.bucket.trendDelta(index, buckets)
                when {
                    trendDelta > POSITIVE_TREND_THRESHOLD -> increaseColor
                    trendDelta < -POSITIVE_TREND_THRESHOLD -> decreaseColor
                    else -> pointColor
                }
            } else {
                insufficientColor
            }

            val radius = if (probability != null) {
                val normalized = (point.bucket.sampleSize.toFloat() / maxSample.toFloat()).coerceIn(0f, 1f)
                lerp(minRadius, maxRadius, normalized)
            } else {
                minRadius
            }

            drawCircle(
                color = color,
                radius = radius,
                center = Offset(point.x, point.y ?: (yOffset - ((0.0 - minProbability) * yScale).toFloat()))
            )
        }

        // Draw sample size markers below
        val textBaseline = yOffset + 16.dp.toPx()
        points.forEach { point ->
            val label = "${point.bucket.sampleSize}"
            drawCenteredText(label, Offset(point.x, textBaseline), insufficientColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyState(color: Color) {
    val message = "データがありません"
    drawCenteredText(message, center, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCenteredText(
    text: String,
    position: Offset,
    color: Color
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 12.dp.toPx()
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
}

private data class ChartPoint(
    val x: Float,
    val y: Float?,
    val bucket: EventAnalyticsCalculations.PredictabilityBucket
)

private const val POSITIVE_TREND_THRESHOLD = 0.05

private fun EventAnalyticsCalculations.PredictabilityBucket.trendDelta(
    index: Int,
    buckets: List<EventAnalyticsCalculations.PredictabilityBucket>
): Double {
    val probability = weightedProbability ?: return 0.0
    val emaValue = emaProbability
    if (emaValue != null) {
        return probability - emaValue
    }
    val previous = buckets.getOrNull(index - 1)?.weightedProbability ?: return 0.0
    return probability - previous
}

internal fun buildPredictabilityContentDescription(
    buckets: List<EventAnalyticsCalculations.PredictabilityBucket>,
    zoneId: ZoneId
): String {
    if (buckets.isEmpty()) {
        return "Predictability chart has no data."
    }
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(zoneId)
    val builder = StringBuilder()
    builder.append("Predictability chart with ${buckets.size} buckets.")
    buckets.forEachIndexed { index, bucket ->
        val bucketRange = "${formatter.format(bucket.bucketStart)} to ${formatter.format(bucket.bucketEnd)}"
        val probabilityText = bucket.weightedProbability?.let { "${(it * 100).roundToInt()} percent" }
            ?: "insufficient data"
        val sampleText = "${bucket.sampleSize} samples"
        val trendDelta = bucket.trendDelta(index, buckets)
        val trendText = when {
            bucket.weightedProbability == null -> ""
            trendDelta > POSITIVE_TREND_THRESHOLD -> "trend rising"
            trendDelta < -POSITIVE_TREND_THRESHOLD -> "trend falling"
            trendDelta.absoluteValue <= POSITIVE_TREND_THRESHOLD -> "trend stable"
            else -> ""
        }
        builder.append(" Bucket ${index + 1}: $bucketRange, $probabilityText, $sampleText")
        if (trendText.isNotEmpty()) {
            builder.append(", $trendText")
        }
        if (bucket.dataStatus == EventAnalyticsCalculations.PredictabilityDataStatus.INSUFFICIENT_SAMPLES) {
            builder.append(", low confidence")
        }
        builder.append('.')
    }
    return builder.toString()
}
