package com.wirewhisper.ui.now

import android.graphics.drawable.Drawable
import com.wirewhisper.flow.TrafficSample

/** Whether the Now screen shows full chrome (header, toggles, search) or just the list. */
enum class ScreenMode { NORMAL, FULLSCREEN }

enum class GroupMode { BY_APP, BY_COUNTRY }

enum class SortMode { RECENT_ACTIVITY, TOTAL_BYTES }

data class NowUiState(
    val groupMode: GroupMode = GroupMode.BY_APP,
    val sortMode: SortMode = SortMode.RECENT_ACTIVITY,
    val appGroups: List<AppGroupUiModel> = emptyList(),
    val countryGroups: List<CountryGroupUiModel> = emptyList(),
    val totalActiveFlows: Int = 0,
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
