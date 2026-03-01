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
import com.wirewhisper.ui.util.formatBytesShort

@Composable
fun TrafficChart(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
    sentColor: Color = Color(0xFFE91E63),       // pink/magenta
    receivedColor: Color = Color(0xFF00BCD4),    // cyan
    blockedColor: Color = Color(0xFFFF5722),     // deep orange for blocked
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

        // Max includes both allowed and blocked
        val maxSent = samples.maxOf { it.sent + it.blockedSent }.coerceAtLeast(1)
        val maxRecv = samples.maxOf { it.received + it.blockedReceived }.coerceAtLeast(1)
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

            // === Sent (above center) ===
            val totalSent = sample.sent + sample.blockedSent
            if (totalSent > 0) {
                val totalRatio = totalSent.toFloat() / maxVal
                val totalH = (totalRatio * halfHeight).coerceAtLeast(2f)
                val alpha = (0.3f + 0.7f * totalRatio).coerceAtMost(1f)

                if (sample.blockedSent > 0 && sample.sent > 0) {
                    // Stacked: allowed closer to center, blocked on top
                    val allowedRatio = sample.sent.toFloat() / totalSent
                    val allowedH = totalH * allowedRatio
                    val blockedH = totalH - allowedH
                    drawRect(
                        color = sentColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY - allowedH),
                        size = Size(barWidth - gap, allowedH),
                    )
                    drawRect(
                        color = blockedColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY - totalH),
                        size = Size(barWidth - gap, blockedH),
                    )
                } else if (sample.blockedSent > 0) {
                    drawRect(
                        color = blockedColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY - totalH),
                        size = Size(barWidth - gap, totalH),
                    )
                } else {
                    drawRect(
                        color = sentColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY - totalH),
                        size = Size(barWidth - gap, totalH),
                    )
                }
            }

            // === Received (below center) ===
            val totalRecv = sample.received + sample.blockedReceived
            if (totalRecv > 0) {
                val totalRatio = totalRecv.toFloat() / maxVal
                val totalH = (totalRatio * halfHeight).coerceAtLeast(2f)
                val alpha = (0.3f + 0.7f * totalRatio).coerceAtMost(1f)

                if (sample.blockedReceived > 0 && sample.received > 0) {
                    val allowedRatio = sample.received.toFloat() / totalRecv
                    val allowedH = totalH * allowedRatio
                    val blockedH = totalH - allowedH
                    drawRect(
                        color = receivedColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY),
                        size = Size(barWidth - gap, allowedH),
                    )
                    drawRect(
                        color = blockedColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY + allowedH),
                        size = Size(barWidth - gap, blockedH),
                    )
                } else if (sample.blockedReceived > 0) {
                    drawRect(
                        color = blockedColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY),
                        size = Size(barWidth - gap, totalH),
                    )
                } else {
                    drawRect(
                        color = receivedColor.copy(alpha = alpha),
                        topLeft = Offset(x + gap / 2, centerY),
                        size = Size(barWidth - gap, totalH),
                    )
                }
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
            val xLabel = leftPadding + (s.toFloat() / totalSeconds) * chartWidth
            val label = "${s}s"
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(xLabel - measured.size.width / 2f, size.height - bottomPadding + 4f),
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

