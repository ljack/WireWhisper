package com.wirewhisper.ui.now

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wirewhisper.WireWhisperApp
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.vpn.WireWhisperVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WireWhisperApp
    private val pm = application.packageManager

    val vpnRunning: StateFlow<Boolean> = app.vpnRunning

    private val expandedUids = MutableStateFlow<Set<Int>>(emptySet())
    private val tick = MutableStateFlow(0L)

    // Icon cache to avoid repeated PackageManager lookups
    private val iconCache = mutableMapOf<String, Drawable?>()
    private val nameCache = mutableMapOf<Int, Pair<String, String?>>() // uid -> (appName, packageName)

    val uiState: StateFlow<NowUiState> = combine(
        app.flowTracker.activeFlows,
        expandedUids,
        tick,
    ) { flows, expanded, _ ->
        buildUiState(flows, expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowUiState())

    init {
        // 1-second tick for sparkline refresh
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick.value = System.currentTimeMillis()
            }
        }
    }

    fun toggleAppExpansion(uid: Int) {
        expandedUids.value = expandedUids.value.let { current ->
            if (uid in current) current - uid else current + uid
        }
    }

    fun startVpn() {
        val context = getApplication<WireWhisperApp>()
        val intent = Intent(context, WireWhisperVpnService::class.java).apply {
            action = WireWhisperVpnService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopVpn() {
        val context = getApplication<WireWhisperApp>()
        val intent = Intent(context, WireWhisperVpnService::class.java).apply {
            action = WireWhisperVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun prepareVpn(): Intent? {
        return VpnService.prepare(getApplication())
    }

    private fun buildUiState(flows: List<FlowRecord>, expanded: Set<Int>): NowUiState {
        if (flows.isEmpty()) return NowUiState()

        // Group flows by UID
        val byUid = flows.groupBy { it.uid }

        val groups = byUid.map { (uid, uidFlows) ->
            val (appName, packageName) = resolveAppInfo(uid, uidFlows)

            // Group by hostname within this app
            val hostnameGroups = uidFlows
                .groupBy { it.dnsHostname ?: it.dstAddress.hostAddress ?: "unknown" }
                .map { (hostname, hFlows) ->
                    HostnameGroupUiModel(
                        hostname = hostname,
                        flowCount = hFlows.size,
                        totalBytes = hFlows.sumOf { it.bytesSent + it.bytesReceived },
                    )
                }
                .sortedByDescending { it.totalBytes }

            val totalBytes = uidFlows.sumOf { it.bytesSent + it.bytesReceived }
            val sparkline = app.trafficSampler.getAppSamples(uid)

            AppGroupUiModel(
                uid = uid,
                appName = appName,
                packageName = packageName,
                icon = packageName?.let { getAppIcon(it) },
                totalBytes = totalBytes,
                hostnames = hostnameGroups,
                sparklineSamples = sparkline,
                isExpanded = uid in expanded,
            )
        }.sortedByDescending { it.totalBytes }

        return NowUiState(
            appGroups = groups,
            totalActiveFlows = flows.size,
        )
    }

    private fun resolveAppInfo(uid: Int, flows: List<FlowRecord>): Pair<String, String?> {
        nameCache[uid]?.let { return it }

        val flowWithName = flows.firstOrNull { it.appName != null }
        if (flowWithName != null) {
            val result = (flowWithName.appName ?: "UID $uid") to flowWithName.packageName
            nameCache[uid] = result
            return result
        }

        val flowWithPkg = flows.firstOrNull { it.packageName != null }
        if (flowWithPkg?.packageName != null) {
            val label = try {
                val ai = pm.getApplicationInfo(flowWithPkg.packageName!!, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                flowWithPkg.packageName!!
            }
            val result = label to flowWithPkg.packageName
            nameCache[uid] = result
            return result
        }

        return "UID $uid" to null
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return iconCache.getOrPut(packageName) {
            try {
                pm.getApplicationIcon(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
