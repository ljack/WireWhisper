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

@Composable
fun TrafficSparkline(
    samples: List<Long>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(
        modifier = modifier
            .width(90.dp)
            .height(24.dp)
    ) {
        if (samples.isEmpty()) return@Canvas
        val maxVal = samples.max().coerceAtLeast(1)
        val barWidth = size.width / samples.size
        val gap = 1f

        for (i in samples.indices) {
            val value = samples[i]
            val ratio = value.toFloat() / maxVal
            val barHeight = (ratio * size.height).coerceAtLeast(if (value > 0) 2f else 0f)
            val alpha = if (value == 0L) 0.1f else (0.3f + 0.7f * ratio).coerceAtMost(1f)

            drawRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(i * barWidth + gap / 2, size.height - barHeight),
                size = Size(barWidth - gap, barHeight),
            )
        }
    }
}
