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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class NowViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WireWhisperApp
    private val pm = application.packageManager

    val vpnRunning: StateFlow<Boolean> = app.vpnRunning

    private val expandedUids = MutableStateFlow<Set<Int>>(emptySet())
    private val expandedCountries = MutableStateFlow<Set<String>>(emptySet())
    private val expandedCountryApps = MutableStateFlow<Set<String>>(emptySet()) // "countryCode:uid"
    private val tick = MutableStateFlow(0L)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _groupMode = MutableStateFlow(GroupMode.BY_APP)
    val groupMode: StateFlow<GroupMode> = _groupMode

    // Traffic detail bottom sheet
    private val _trafficDetail = MutableStateFlow<TrafficDetailUiModel?>(null)
    val trafficDetail: StateFlow<TrafficDetailUiModel?> = _trafficDetail

    // Sort mode
    private val _sortMode = MutableStateFlow(SortMode.RECENT_ACTIVITY)
    private val _sortingPaused = MutableStateFlow(false)
    private val _frozenOrder = MutableStateFlow<List<Int>?>(null)
    private var interactionResumeJob: Job? = null

    // Icon cache to avoid repeated PackageManager lookups
    private val iconCache = mutableMapOf<String, Drawable?>()
    private val nameCache = mutableMapOf<Int, Pair<String, String?>>() // uid -> (appName, packageName)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<NowUiState> = combine(
        app.flowTracker.activeFlows,
        expandedUids,
        expandedCountries,
        expandedCountryApps,
        tick,
        _searchQuery,
        _groupMode,
        _sortMode,
        _sortingPaused,
        _frozenOrder,
    ) { values ->
        val flows = values[0] as List<FlowRecord>
        val expanded = values[1] as Set<Int>
        val expandedC = values[2] as Set<String>
        val expandedCA = values[3] as Set<String>
        // values[4] = tick (unused)
        val query = values[5] as String
        val mode = values[6] as GroupMode
        val sortMode = values[7] as SortMode
        val paused = values[8] as Boolean
        val frozenOrder = values[9] as List<Int>?
        when (mode) {
            GroupMode.BY_APP -> buildAppUiState(flows, expanded, query, sortMode, paused, frozenOrder)
            GroupMode.BY_COUNTRY -> buildCountryUiState(flows, query, expandedC, expandedCA)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowUiState())

    init {
        // 1-second tick for sparkline refresh
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick.value = System.currentTimeMillis()
                // Refresh traffic detail if bottom sheet is visible
                _trafficDetail.value?.let { detail ->
                    refreshTrafficDetail(detail.uid, detail.appName)
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onGroupModeChanged(mode: GroupMode) {
        _groupMode.value = mode
    }

    fun toggleAppExpansion(uid: Int) {
        expandedUids.value = expandedUids.value.let { current ->
            if (uid in current) current - uid else current + uid
        }
    }

    fun toggleCountryExpansion(countryCode: String) {
        expandedCountries.value = expandedCountries.value.let { current ->
            if (countryCode in current) current - countryCode else current + countryCode
        }
    }

    fun toggleCountryAppExpansion(countryCode: String, uid: Int) {
        val key = "$countryCode:$uid"
        expandedCountryApps.value = expandedCountryApps.value.let { current ->
            if (key in current) current - key else current + key
        }
    }

    // ── Traffic detail bottom sheet ───────────────────────────────

    fun showTrafficDetail(uid: Int) {
        val appName = nameCache[uid]?.first ?: "UID $uid"
        refreshTrafficDetail(uid, appName)
    }

    fun dismissTrafficDetail() {
        _trafficDetail.value = null
    }

    private fun refreshTrafficDetail(uid: Int, appName: String) {
        val samples = app.trafficSampler.getAppDirectionalSamples(uid)
        _trafficDetail.value = TrafficDetailUiModel(
            uid = uid,
            appName = appName,
            samples = samples,
            totalSent = samples.sumOf { it.sent },
            totalReceived = samples.sumOf { it.received },
        )
    }

    // ── Sort mode ────────────────────────────────────────────────

    fun onSortModeChanged(mode: SortMode) {
        _sortMode.value = mode
        _frozenOrder.value = null
        _sortingPaused.value = false
    }

    fun onUserInteractionStarted() {
        interactionResumeJob?.cancel()
        if (!_sortingPaused.value) {
            // Freeze current order on first pause
            val currentGroups = uiState.value.appGroups
            if (currentGroups.isNotEmpty()) {
                _frozenOrder.value = currentGroups.map { it.uid }
            }
        }
        _sortingPaused.value = true
    }

    fun onUserInteractionEnded() {
        interactionResumeJob?.cancel()
        interactionResumeJob = viewModelScope.launch {
            delay(3000)
            _sortingPaused.value = false
            _frozenOrder.value = null
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

    private fun buildAppUiState(
        flows: List<FlowRecord>,
        expanded: Set<Int>,
        query: String,
        sortMode: SortMode = SortMode.RECENT_ACTIVITY,
        paused: Boolean = false,
        frozenOrder: List<Int>? = null,
    ): NowUiState {
        if (flows.isEmpty()) return NowUiState(groupMode = GroupMode.BY_APP, sortMode = sortMode)

        // Group flows by UID
        val byUid = flows.groupBy { it.uid }

        var groups = byUid.map { (uid, uidFlows) ->
            buildAppGroupModel(uid, uidFlows, uid in expanded)
        }

        // Apply sorting
        groups = when {
            paused && frozenOrder != null -> {
                val orderMap = frozenOrder.withIndex().associate { (i, uid) -> uid to i }
                groups.sortedBy { orderMap[it.uid] ?: Int.MAX_VALUE }
            }
            sortMode == SortMode.RECENT_ACTIVITY ->
                groups.sortedWith(
                    compareByDescending<AppGroupUiModel> { it.lastActivityTime }
                        .thenByDescending { it.totalBytes }
                )
            else -> groups.sortedByDescending { it.totalBytes }
        }

        if (query.isNotBlank()) {
            groups = groups.filter { group ->
                group.appName.contains(query, ignoreCase = true) ||
                    group.packageName?.contains(query, ignoreCase = true) == true ||
                    group.hostnames.any { it.hostname.contains(query, ignoreCase = true) }
            }
        }

        return NowUiState(
            groupMode = GroupMode.BY_APP,
            sortMode = sortMode,
            appGroups = groups,
            totalActiveFlows = flows.size,
        )
    }

    private fun buildCountryUiState(
        flows: List<FlowRecord>,
        query: String,
        expandedC: Set<String>,
        expandedCA: Set<String>,
    ): NowUiState {
        if (flows.isEmpty()) return NowUiState(groupMode = GroupMode.BY_COUNTRY)

        // Group flows by country
        val byCountry = flows.groupBy { it.country ?: "??" }

        var groups = byCountry.map { (country, countryFlows) ->
            val displayName = formatCountryDisplay(country)

            // Sub-group by UID
            val byUid = countryFlows.groupBy { it.uid }
            val appModels = byUid.map { (uid, uidFlows) ->
                val (appName, packageName) = resolveAppInfo(uid, uidFlows)
                val hostnameGroups = buildHostnameGroups(uidFlows)
                val totalBytes = uidFlows.sumOf { it.bytesSent + it.bytesReceived }
                val sparkline = app.trafficSampler.getAppSamples(uid)
                val key = "$country:$uid"

                CountryAppUiModel(
                    uid = uid,
                    appName = appName,
                    packageName = packageName,
                    icon = packageName?.let { getAppIcon(it) },
                    totalBytes = totalBytes,
                    hostnames = hostnameGroups,
                    sparklineSamples = sparkline,
                    isExpanded = key in expandedCA,
                )
            }.sortedByDescending { it.totalBytes }

            CountryGroupUiModel(
                countryCode = country,
                displayName = displayName,
                totalBytes = countryFlows.sumOf { it.bytesSent + it.bytesReceived },
                apps = appModels,
                isExpanded = country in expandedC,
            )
        }.sortedWith(compareByDescending<CountryGroupUiModel> { it.countryCode != "??" }.thenByDescending { it.totalBytes })

        if (query.isNotBlank()) {
            groups = groups.filter { group ->
                group.displayName.contains(query, ignoreCase = true) ||
                    group.countryCode.contains(query, ignoreCase = true) ||
                    group.apps.any { app ->
                        app.appName.contains(query, ignoreCase = true) ||
                            app.packageName?.contains(query, ignoreCase = true) == true ||
                            app.hostnames.any { it.hostname.contains(query, ignoreCase = true) }
                    }
            }
        }

        return NowUiState(
            groupMode = GroupMode.BY_COUNTRY,
            countryGroups = groups,
            totalActiveFlows = flows.size,
        )
    }

    private fun buildAppGroupModel(uid: Int, uidFlows: List<FlowRecord>, isExpanded: Boolean): AppGroupUiModel {
        val (appName, packageName) = resolveAppInfo(uid, uidFlows)
        val isBlocked = packageName != null && app.blockingEngine.isBlocked(packageName, null)
        val hostnameGroups = buildHostnameGroups(uidFlows, packageName, isBlocked)
        val totalBytes = uidFlows.sumOf { it.bytesSent + it.bytesReceived }
        val sparkline = app.trafficSampler.getAppSamples(uid)
        val lastActivity = uidFlows.maxOf { it.lastSeen }

        return AppGroupUiModel(
            uid = uid,
            appName = appName,
            packageName = packageName,
            icon = packageName?.let { getAppIcon(it) },
            totalBytes = totalBytes,
            hostnames = hostnameGroups,
            sparklineSamples = sparkline,
            isExpanded = isExpanded,
            isBlocked = isBlocked,
            lastActivityTime = lastActivity,
        )
    }

    private fun buildHostnameGroups(
        flows: List<FlowRecord>,
        packageName: String? = null,
        appBlocked: Boolean = false,
    ): List<HostnameGroupUiModel> {
        return flows
            .groupBy { it.dnsHostname ?: it.dstAddress.hostAddress ?: "unknown" }
            .map { (hostname, hFlows) ->
                HostnameGroupUiModel(
                    hostname = hostname,
                    flowCount = hFlows.size,
                    totalBytes = hFlows.sumOf { it.bytesSent + it.bytesReceived },
                    isBlocked = packageName != null && app.blockingEngine.isBlocked(packageName, hostname),
                    parentAppBlocked = appBlocked,
                )
            }
            .sortedByDescending { it.totalBytes }
    }

    // ── Blocking ────────────────────────────────────────────────

    fun toggleAppBlock(packageName: String) {
        app.blockingEngine.toggleAppBlock(packageName)
    }

    fun toggleHostnameBlock(packageName: String, hostname: String) {
        app.blockingEngine.toggleHostnameBlock(packageName, hostname)
    }

    private fun formatCountryDisplay(code: String): String {
        return when (code) {
            "LAN" -> "\uD83C\uDFE0 Local Network"
            "LO" -> "\uD83D\uDD01 Loopback"
            "??" -> "\u2753 Unknown"
            else -> {
                val flag = countryCodeToFlag(code)
                val name = Locale.of("", code).displayCountry
                "$flag $name"
            }
        }
    }

    private fun countryCodeToFlag(code: String): String {
        if (code.length != 2) return "\uD83C\uDF10"
        val first = 0x1F1E6 + (code[0].uppercaseChar() - 'A')
        val second = 0x1F1E6 + (code[1].uppercaseChar() - 'A')
        return String(intArrayOf(first, second), 0, 2)
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
