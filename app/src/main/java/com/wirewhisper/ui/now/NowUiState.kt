package com.wirewhisper.ui.now

import android.graphics.drawable.Drawable
import com.wirewhisper.flow.TrafficSample

enum class GroupMode { BY_APP, BY_COUNTRY }

enum class SortMode { RECENT_ACTIVITY, TOTAL_BYTES }

enum class TimeFilter(val label: String, val shortLabel: String, val durationMs: Long?) {
    ALL("Show All", "All", null),
    LAST_5_MIN("Last 5 min", "5m", 5 * 60_000L),
    LAST_HOUR("Last hour", "1h", 60 * 60_000L),
    LAST_24H("Last 24h", "24h", 24 * 60 * 60_000L),
    LAST_WEEK("Last week", "1w", 7 * 24 * 60 * 60_000L),
    FROM_NOW("From now", "Now", 0L),
}

data class NowUiState(
    val groupMode: GroupMode = GroupMode.BY_APP,
    val sortMode: SortMode = SortMode.RECENT_ACTIVITY,
    val appGroups: List<AppGroupUiModel> = emptyList(),
    val countryGroups: List<CountryGroupUiModel> = emptyList(),
    val totalActiveFlows: Int = 0,
    val blockedAppCount: Int = 0,
    val watchedAppCount: Int = 0,
    val appCount: Int = 0,
    val countryCount: Int = 0,
    val totalBytesSent: Long = 0,
    val totalBytesReceived: Long = 0,
    val lastActivityTime: Long = 0L,
)

data class AppGroupUiModel(
    val uid: Int,
    val appName: String,
    val packageName: String?,
    val icon: Drawable?,
    val totalBytes: Long,
    val hostnames: List<HostnameGroupUiModel>,
    val sparklineSamples: List<TrafficSample>,
    val isExpanded: Boolean,
    val isBlocked: Boolean = false,
    val lastActivityTime: Long = 0L,
    val blockedAttemptCount: Long = 0,
)

data class CountryGroupUiModel(
    val countryCode: String,
    val displayName: String,
    val totalBytes: Long,
    val apps: List<CountryAppUiModel>,
    val isExpanded: Boolean,
    val isBlocked: Boolean = false,
    val blockedAttemptCount: Long = 0,
)

data class CountryAppUiModel(
    val uid: Int,
    val appName: String,
    val packageName: String?,
    val icon: Drawable?,
    val totalBytes: Long,
    val hostnames: List<HostnameGroupUiModel>,
    val sparklineSamples: List<TrafficSample>,
    val isExpanded: Boolean,
    val isBlocked: Boolean = false,
    val lastActivityTime: Long = 0L,
)

data class HostnameGroupUiModel(
    val hostname: String,
    val flowCount: Int,
    val totalBytes: Long,
    val isBlocked: Boolean = false,
    val parentAppBlocked: Boolean = false,
    val blockedAttemptCount: Long = 0,
    val isWatched: Boolean = false,
)

data class TrafficDetailUiModel(
    val uid: Int,
    val appName: String,
    val samples: List<TrafficSample>,
    val totalSent: Long,
    val totalReceived: Long,
    val totalBlockedSent: Long = 0,
    val totalBlockedReceived: Long = 0,
)
