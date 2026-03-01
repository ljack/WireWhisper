package com.wirewhisper.ui.util

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

fun formatBytesShort(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}

private val IPV4_PATTERN = Regex("""\d{1,3}(\.\d{1,3}){3}""")

fun isIpv4Address(value: String): Boolean = IPV4_PATTERN.matches(value)

fun formatRelativeTime(timestampMs: Long): String {
    val delta = System.currentTimeMillis() - timestampMs
    if (delta < 0) return "now"
    return when {
        delta < 10_000 -> "%.1fs".format(delta / 1000.0)
        delta < 60_000 -> "${delta / 1000}s"
        delta < 3_600_000 -> "${delta / 60_000}m"
        delta < 86_400_000 -> "${delta / 3_600_000}h"
        else -> "${delta / 86_400_000}d"
    }
}
