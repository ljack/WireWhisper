package com.wirewhisper.ui.watchlist

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wirewhisper.WireWhisperApp
import com.wirewhisper.core.model.FlowRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class WatchlistDetailUiState(
    val entryValue: String = "",
    val entryType: String = "",
    val liveFlows: List<FlowRecord> = emptyList(),
)

data class AppFlowGroup(
    val appName: String,
    val packageName: String?,
    val icon: Drawable?,
    val flows: List<FlowRecord>,
    val totalBytes: Long,
)

class WatchlistDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WireWhisperApp
    private val pm = application.packageManager
    private val iconCache = mutableMapOf<String, Drawable?>()

    private val _entryValue = MutableStateFlow("")
    private val _entryType = MutableStateFlow("")

    fun init(entryValue: String, entryType: String) {
        _entryValue.value = entryValue
        _entryType.value = entryType
    }

    val uiState: StateFlow<WatchlistDetailUiState> = combine(
        _entryValue,
        _entryType,
        app.flowTracker.activeFlows,
    ) { value, type, activeFlows ->
        if (value.isBlank()) return@combine WatchlistDetailUiState()

        val hostname = if (type == "hostname") value else null
        val ip = if (type == "ip") value else null

        val liveMatches = activeFlows.filter { flow ->
            (hostname != null && flow.dnsHostname?.lowercase() == hostname) ||
            (ip != null && flow.dstAddress.hostAddress == ip)
        }

        WatchlistDetailUiState(
            entryValue = value,
            entryType = type,
            liveFlows = liveMatches,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchlistDetailUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyFlows: StateFlow<List<FlowRecord>> = combine(
        _entryValue,
        _entryType,
    ) { value, type -> value to type }
        .flatMapLatest { (value, type) ->
            val hostname = if (type == "hostname") value else null
            val ip = if (type == "ip") value else null
            app.flowRepository.getFlowsForDestination(hostname, ip)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun groupByApp(flows: List<FlowRecord>): List<AppFlowGroup> {
        return flows.groupBy { it.uid }.map { (_, appFlows) ->
            val first = appFlows.first()
            val appName = first.appName ?: first.packageName ?: "UID ${first.uid}"
            val packageName = first.packageName
            AppFlowGroup(
                appName = appName,
                packageName = packageName,
                icon = packageName?.let { getAppIcon(it) },
                flows = appFlows.sortedByDescending { it.lastSeen },
                totalBytes = appFlows.sumOf { it.bytesSent + it.bytesReceived },
            )
        }.sortedByDescending { it.totalBytes }
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return iconCache.getOrPut(packageName) {
            try { pm.getApplicationIcon(packageName) }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
}
