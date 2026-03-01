package com.wirewhisper.ui.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val THUMB_HEIGHT = 28.dp

@Composable
fun FastScrollColumn(
    listState: LazyListState,
    itemCount: Int,
    timestampForIndex: (Int) -> Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var thumbOffsetPx by remember { mutableFloatStateOf(0f) }
    val thumbHeightPx = with(density) { THUMB_HEIGHT.toPx() }

    val isScrollable by remember {
        derivedStateOf { itemCount > 0 && (listState.canScrollForward || listState.canScrollBackward) }
    }

    var showThumb by remember { mutableStateOf(false) }

    // Helper to scroll to a given Y offset on the track
    fun scrollToOffset(yPx: Float) {
        val clamped = yPx.coerceIn(0f, trackHeightPx)
        thumbOffsetPx = clamped
        val fraction = clamped / trackHeightPx
        val targetIndex = timeProportionalIndex(fraction, itemCount, timestampForIndex)
        coroutineScope.launch { listState.scrollToItem(targetIndex) }
    }

    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            showThumb = true
        } else {
            delay(1500)
            if (!isDragging) showThumb = false
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            if (itemCount <= 1) 0f
            else listState.firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)
        }
    }

    LaunchedEffect(scrollFraction, isDragging) {
        if (!isDragging && trackHeightPx > 0f) {
            thumbOffsetPx = scrollFraction * trackHeightPx
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (isScrollable) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(48.dp)
                    .padding(end = 4.dp)
                    .onSizeChanged { trackHeightPx = (it.height - thumbHeightPx).coerceAtLeast(1f) }
                    // Tap-to-position: jump the thumb to where the user taps
                    .pointerInput(itemCount) {
                        detectTapGestures { offset ->
                            showThumb = true
                            scrollToOffset(offset.y - thumbHeightPx / 2)
                        }
                    }
                    // Drag to scrub
                    .pointerInput(itemCount) {
                        detectVerticalDragGestures(
                            onDragStart = { startOffset ->
                                isDragging = true
                                showThumb = true
                                // Jump thumb to tap position on drag start
                                scrollToOffset(startOffset.y - thumbHeightPx / 2)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                thumbOffsetPx = (thumbOffsetPx + dragAmount)
                                    .coerceIn(0f, trackHeightPx)
                                val fraction = thumbOffsetPx / trackHeightPx
                                val targetIndex = timeProportionalIndex(
                                    fraction, itemCount, timestampForIndex,
                                )
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            },
                            onDragEnd = { isDragging = false },
                        )
                    },
            ) {
                // Track line (always visible when scrollable)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .width(3.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(1.5.dp),
                        ),
                )

                // Thumb pill
                AnimatedVisibility(
                    visible = showThumb || isDragging,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                            .width(if (isDragging) 10.dp else 6.dp)
                            .height(THUMB_HEIGHT)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }

                // Date bubble (during drag only)
                AnimatedVisibility(
                    visible = isDragging && itemCount > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    val currentIndex = if (trackHeightPx > 0f) {
                        timeProportionalIndex(
                            thumbOffsetPx / trackHeightPx, itemCount, timestampForIndex,
                        )
                    } else 0
                    val timestamp = timestampForIndex(
                        currentIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                    )

                    // Clamp bubble Y so it doesn't overflow the track
                    val bubbleHeightPx = with(density) { 32.dp.toPx() }
                    val clampedBubbleY = thumbOffsetPx
                        .coerceAtMost(trackHeightPx + thumbHeightPx - bubbleHeightPx)
                        .coerceAtLeast(0f)

                    Card(
                        modifier = Modifier.offset {
                            IntOffset(
                                x = with(density) { (-56).dp.roundToPx() },
                                y = clampedBubbleY.roundToInt(),
                            )
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = formatScrubDate(timestamp),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .widthIn(min = 80.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun timeProportionalIndex(
    fraction: Float,
    itemCount: Int,
    timestampForIndex: (Int) -> Long,
): Int {
    if (itemCount <= 1) return 0
    val clampedFraction = fraction.coerceIn(0f, 1f)

    val newestTime = timestampForIndex(0)
    val oldestTime = timestampForIndex(itemCount - 1)
    if (newestTime == oldestTime) return (clampedFraction * (itemCount - 1)).roundToInt()

    val targetTime = newestTime - (clampedFraction * (newestTime - oldestTime)).toLong()

    // Binary search (items sorted descending by time)
    var low = 0
    var high = itemCount - 1
    while (low < high) {
        val mid = (low + high) / 2
        if (timestampForIndex(mid) > targetTime) low = mid + 1 else high = mid
    }

    val best = low.coerceIn(0, itemCount - 1)
    if (best > 0) {
        val prevDiff = abs(timestampForIndex(best - 1) - targetTime)
        val currDiff = abs(timestampForIndex(best) - targetTime)
        if (prevDiff < currDiff) return best - 1
    }
    return best
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
private val monthDayTimeFormat = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
private val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

private fun formatScrubDate(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        isToday(epochMs) -> "Today ${timeFormat.format(Date(epochMs))}"
        isYesterday(epochMs) -> "Yest. ${timeFormat.format(Date(epochMs))}"
        isSameYear(epochMs) -> monthDayTimeFormat.format(Date(epochMs))
        else -> fullDateFormat.format(Date(epochMs))
    }
}

private fun isToday(epochMs: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(epochMs: Long): Boolean {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    return yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun isSameYear(epochMs: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
}
