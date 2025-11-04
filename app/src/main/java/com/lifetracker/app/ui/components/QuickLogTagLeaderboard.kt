package com.lifetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifetracker.core.analytics.TagAggregate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuickLogTagLeaderboard(
    items: List<TagAggregate>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val semanticsDescription = buildQuickLogLeaderboardDescription(items, zoneId)
    val maxCount = items.maxOfOrNull { it.occurrences }?.coerceAtLeast(1) ?: 1
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(zoneId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (items.isEmpty()) {
            Text(
                text = "タグの記録が見つかりません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${index + 1}. ${item.tag}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Medium
                        )
                        Text(
                            text = "${item.occurrences} 回",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(inactiveColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (item.occurrences.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                    Text(
                        text = "最終記録: ${formatter.format(item.lastOccurredAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun buildQuickLogLeaderboardDescription(
    items: List<TagAggregate>,
    zoneId: ZoneId
): String {
    if (items.isEmpty()) {
        return "Quick log leaderboard has no data."
    }
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(zoneId)
    return buildString {
        append("Quick log leaderboard top ")
        append(items.size)
        append(" tags. ")
        items.forEachIndexed { index, item ->
            append("Rank ")
            append(index + 1)
            append(':')
            append(' ')
            append(item.tag)
            append(" with ")
            append(item.occurrences)
            append(" entries, last seen ")
            append(formatter.format(item.lastOccurredAt))
            append('.')
        }
    }
}
