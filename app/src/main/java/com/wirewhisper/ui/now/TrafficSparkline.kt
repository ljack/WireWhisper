package com.wirewhisper.ui.now

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wirewhisper.flow.TrafficSample

@Composable
fun TrafficSparkline(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    blockedColor: Color = MaterialTheme.colorScheme.error,
) {
    Canvas(
        modifier = modifier
            .width(90.dp)
            .height(24.dp)
    ) {
        if (samples.isEmpty()) return@Canvas
        val maxVal = samples.maxOf { it.total }.coerceAtLeast(1)
        val barWidth = size.width / samples.size
        val gap = 1f

        for (i in samples.indices) {
            val sample = samples[i]
            val allowed = sample.allowedTotal
            val blocked = sample.blockedTotal
            val total = allowed + blocked
            if (total == 0L) {
                // Draw faint background bar
                drawRect(
                    color = barColor.copy(alpha = 0.1f),
                    topLeft = Offset(i * barWidth + gap / 2, size.height),
                    size = Size(barWidth - gap, 0f),
                )
                continue
            }

            val totalRatio = total.toFloat() / maxVal
            val totalHeight = (totalRatio * size.height).coerceAtLeast(2f)
            val alpha = (0.3f + 0.7f * totalRatio).coerceAtMost(1f)

            if (blocked > 0 && allowed > 0) {
                // Stacked: allowed at bottom, blocked on top
                val allowedRatio = allowed.toFloat() / total
                val allowedH = totalHeight * allowedRatio
                val blockedH = totalHeight - allowedH

                // Allowed portion (bottom)
                drawRect(
                    color = barColor.copy(alpha = alpha),
                    topLeft = Offset(i * barWidth + gap / 2, size.height - allowedH),
                    size = Size(barWidth - gap, allowedH),
                )
                // Blocked portion (top)
                drawRect(
                    color = blockedColor.copy(alpha = alpha),
                    topLeft = Offset(i * barWidth + gap / 2, size.height - totalHeight),
                    size = Size(barWidth - gap, blockedH),
                )
            } else if (blocked > 0) {
                // All blocked
                drawRect(
                    color = blockedColor.copy(alpha = alpha),
                    topLeft = Offset(i * barWidth + gap / 2, size.height - totalHeight),
                    size = Size(barWidth - gap, totalHeight),
                )
            } else {
                // All allowed
                drawRect(
                    color = barColor.copy(alpha = alpha),
                    topLeft = Offset(i * barWidth + gap / 2, size.height - totalHeight),
                    size = Size(barWidth - gap, totalHeight),
                )
            }
        }
    }
}
