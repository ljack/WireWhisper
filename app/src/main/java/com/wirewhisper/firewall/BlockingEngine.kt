package com.wirewhisper.firewall

import com.wirewhisper.data.db.BlockRuleDao
import com.wirewhisper.data.db.BlockRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory blocking engine backed by Room persistence.
 *
 * Hot-path [isBlocked] is O(1) via concurrent set lookups.
 * Mutations update both in-memory sets and Room, then emit StateFlows for UI.
 */
class BlockingEngine(
    private val dao: BlockRuleDao,
    private val scope: CoroutineScope,
) {
    private val blockedApps = ConcurrentHashMap.newKeySet<String>()
    private val blockedHostnames = ConcurrentHashMap<String, MutableSet<String>>() // packageName → hostnames
    private val blockedCountries = ConcurrentHashMap.newKeySet<String>()

    private val _blockedAppsFlow = MutableStateFlow<Set<String>>(emptySet())
    val blockedAppsFlow: StateFlow<Set<String>> = _blockedAppsFlow.asStateFlow()

    private val _blockedHostnamesFlow = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val blockedHostnamesFlow: StateFlow<Map<String, Set<String>>> = _blockedHostnamesFlow.asStateFlow()

    private val _blockedCountriesFlow = MutableStateFlow<Set<String>>(emptySet())
    val blockedCountriesFlow: StateFlow<Set<String>> = _blockedCountriesFlow.asStateFlow()

    // Blocked attempt counters for UI shake animation
    private val appBlockedCounts = ConcurrentHashMap<String, Long>()
    private val hostnameBlockedCounts = ConcurrentHashMap<String, Long>() // "pkg:host" → count
    private val countryBlockedCounts = ConcurrentHashMap<String, Long>()

    suspend fun loadRules() {
        val rules = dao.getAllRulesOnce()
        blockedApps.clear()
        blockedHostnames.clear()
        blockedCountries.clear()

        for (rule in rules) {
            when {
                rule.countryCode != null && rule.packageName == null -> {
                    blockedCountries.add(rule.countryCode)
                }
                rule.hostname == null && rule.packageName != null -> {
                    blockedApps.add(rule.packageName)
                }
                rule.hostname != null && rule.packageName != null -> {
                    blockedHostnames.getOrPut(rule.packageName) {
                        ConcurrentHashMap.newKeySet()
                    }.add(rule.hostname)
                }
            }
        }
        emitFlows()
    }

    /**
     * Hot-path check for packet filtering. Returns the block reason if blocked, null if allowed.
     * Single lookup serves both the blocking decision and the reason for persistence.
     */
    fun determineBlockReason(packageName: String?, hostname: String?, country: String? = null): String? {
        if (country != null && country in blockedCountries) return "country:$country"
        if (packageName == null) return null
        if (packageName in blockedApps) return "app"
        if (hostname != null) {
            val hostSet = blockedHostnames[packageName]
            if (hostSet != null && hostname in hostSet) return "hostname"
        }
        return null
    }

    fun isBlocked(packageName: String?, hostname: String?, country: String? = null): Boolean =
        determineBlockReason(packageName, hostname, country) != null

    fun isCountryBlocked(countryCode: String): Boolean = countryCode in blockedCountries

    fun toggleAppBlock(packageName: String) {
        scope.launch {
            if (packageName in blockedApps) {
                blockedApps.remove(packageName)
                dao.deleteAppRule(packageName)
            } else {
                blockedApps.add(packageName)
                dao.insert(BlockRuleEntity(packageName = packageName))
            }
            emitFlows()
        }
    }

    fun toggleHostnameBlock(packageName: String, hostname: String) {
        scope.launch {
            val hostSet = blockedHostnames.getOrPut(packageName) {
                ConcurrentHashMap.newKeySet()
            }
            if (hostname in hostSet) {
                hostSet.remove(hostname)
                if (hostSet.isEmpty()) blockedHostnames.remove(packageName)
                dao.deleteHostnameRule(packageName, hostname)
            } else {
                hostSet.add(hostname)
                dao.insert(BlockRuleEntity(packageName = packageName, hostname = hostname))
            }
            emitFlows()
        }
    }

    fun toggleCountryBlock(countryCode: String) {
        scope.launch {
            if (countryCode in blockedCountries) {
                blockedCountries.remove(countryCode)
                dao.deleteCountryRule(countryCode)
            } else {
                blockedCountries.add(countryCode)
                dao.insert(BlockRuleEntity(countryCode = countryCode))
            }
            emitFlows()
        }
    }

    /** Called by TunProcessor when a packet is actually dropped. */
    fun notifyBlocked(packageName: String?, hostname: String?, country: String? = null) {
        if (packageName != null) {
            appBlockedCounts.compute(packageName) { _, v -> (v ?: 0) + 1 }
        }
        if (packageName != null && hostname != null) {
            hostnameBlockedCounts.compute("$packageName:$hostname") { _, v -> (v ?: 0) + 1 }
        }
        if (country != null) {
            countryBlockedCounts.compute(country) { _, v -> (v ?: 0) + 1 }
        }
    }

    fun resetBlockedCounts() {
        appBlockedCounts.clear()
        hostnameBlockedCounts.clear()
        countryBlockedCounts.clear()
    }

    fun getAppBlockedCount(packageName: String): Long = appBlockedCounts[packageName] ?: 0

    fun getHostnameBlockedCount(packageName: String, hostname: String): Long =
        hostnameBlockedCounts["$packageName:$hostname"] ?: 0

    fun getCountryBlockedCount(countryCode: String): Long = countryBlockedCounts[countryCode] ?: 0

    private fun emitFlows() {
        _blockedAppsFlow.value = blockedApps.toSet()
        _blockedHostnamesFlow.value = blockedHostnames.mapValues { it.value.toSet() }
        _blockedCountriesFlow.value = blockedCountries.toSet()
    }
}
