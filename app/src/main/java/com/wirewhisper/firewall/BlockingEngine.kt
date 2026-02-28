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

    private val _blockedAppsFlow = MutableStateFlow<Set<String>>(emptySet())
    val blockedAppsFlow: StateFlow<Set<String>> = _blockedAppsFlow.asStateFlow()

    private val _blockedHostnamesFlow = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val blockedHostnamesFlow: StateFlow<Map<String, Set<String>>> = _blockedHostnamesFlow.asStateFlow()

    suspend fun loadRules() {
        val rules = dao.getAllRulesOnce()
        blockedApps.clear()
        blockedHostnames.clear()

        for (rule in rules) {
            if (rule.hostname == null) {
                blockedApps.add(rule.packageName)
            } else {
                blockedHostnames.getOrPut(rule.packageName) {
                    ConcurrentHashMap.newKeySet()
                }.add(rule.hostname)
            }
        }
        emitFlows()
    }

    /**
     * Hot-path check for packet filtering. Must be fast.
     */
    fun isBlocked(packageName: String?, hostname: String?): Boolean {
        if (packageName == null) return false
        if (packageName in blockedApps) return true
        if (hostname != null) {
            val hostSet = blockedHostnames[packageName]
            if (hostSet != null && hostname in hostSet) return true
        }
        return false
    }

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

    private fun emitFlows() {
        _blockedAppsFlow.value = blockedApps.toSet()
        _blockedHostnamesFlow.value = blockedHostnames.mapValues { it.value.toSet() }
    }
}
