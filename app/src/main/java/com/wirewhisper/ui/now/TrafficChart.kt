package com.wirewhisper.ui.now

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wirewhisper.flow.TrafficSample

@Composable
fun TrafficChart(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
    sentColor: Color = Color(0xFFE91E63),    // pink/magenta
    receivedColor: Color = Color(0xFF00BCD4), // cyan
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        if (samples.isEmpty()) return@Canvas

        val leftPadding = 48f
        val rightPadding = 8f
        val topPadding = 8f
        val bottomPadding = 20f
        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding
        val centerY = topPadding + chartHeight / 2f

        val maxSent = samples.maxOf { it.sent }.coerceAtLeast(1)
        val maxRecv = samples.maxOf { it.received }.coerceAtLeast(1)
        val maxVal = maxOf(maxSent, maxRecv)

        val barWidth = chartWidth / samples.size
        val gap = 1f
        val halfHeight = chartHeight / 2f

        // Center line
        drawLine(
            color = dividerColor,
            start = Offset(leftPadding, centerY),
            end = Offset(size.width - rightPadding, centerY),
            strokeWidth = 1f,
        )

        // Bars
        for (i in samples.indices) {
            val sample = samples[i]
            val x = leftPadding + i * barWidth

            // Sent (above center)
            if (sample.sent > 0) {
                val ratio = sample.sent.toFloat() / maxVal
                val barH = (ratio * halfHeight).coerceAtLeast(2f)
                val alpha = (0.3f + 0.7f * ratio).coerceAtMost(1f)
                drawRect(
                    color = sentColor.copy(alpha = alpha),
                    topLeft = Offset(x + gap / 2, centerY - barH),
                    size = Size(barWidth - gap, barH),
                )
            }

            // Received (below center)
            if (sample.received > 0) {
                val ratio = sample.received.toFloat() / maxVal
                val barH = (ratio * halfHeight).coerceAtLeast(2f)
                val alpha = (0.3f + 0.7f * ratio).coerceAtMost(1f)
                drawRect(
                    color = receivedColor.copy(alpha = alpha),
                    topLeft = Offset(x + gap / 2, centerY),
                    size = Size(barWidth - gap, barH),
                )
            }
        }

        // Y-axis labels
        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
        drawAxisLabel(textMeasurer, formatBytesShort(maxVal), labelStyle, 2f, topPadding + 4f)
        drawAxisLabel(textMeasurer, formatBytesShort(maxVal), labelStyle, 2f, topPadding + chartHeight - 12f)

        // X-axis labels (10s intervals)
        val intervalSeconds = 10
        val totalSeconds = samples.size
        for (s in intervalSeconds..totalSeconds step intervalSeconds) {
            val x = leftPadding + (s.toFloat() / totalSeconds) * chartWidth
            val label = "${s}s"
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(x - measured.size.width / 2f, size.height - bottomPadding + 4f),
            )
        }
    }
}

private fun DrawScope.drawAxisLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
) {
    val measured = textMeasurer.measure(text, style)
    drawText(textLayoutResult = measured, topLeft = Offset(x, y))
}

internal fun formatBytesShort(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
