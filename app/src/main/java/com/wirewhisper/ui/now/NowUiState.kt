package com.wirewhisper.ui.now

import android.graphics.drawable.Drawable

data class NowUiState(
    val appGroups: List<AppGroupUiModel> = emptyList(),
    val totalActiveFlows: Int = 0,
)

data class AppGroupUiModel(
    val uid: Int,
    val appName: String,
    val packageName: String?,
    val icon: Drawable?,
    val totalBytes: Long,
    val hostnames: List<HostnameGroupUiModel>,
    val sparklineSamples: List<Long>,
    val isExpanded: Boolean,
)

data class HostnameGroupUiModel(
    val hostname: String,
    val flowCount: Int,
    val totalBytes: Long,
)
