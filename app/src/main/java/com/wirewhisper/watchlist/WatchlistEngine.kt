package com.wirewhisper.watchlist

import com.wirewhisper.data.db.WatchlistDao
import com.wirewhisper.data.db.WatchlistEntryEntity
import com.wirewhisper.ui.util.isIpv4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class WatchlistEntry(
    val id: Long,
    val type: String,
    val value: String,
    val label: String?,
    val createdAt: Long,
)

/**
 * In-memory watchlist engine backed by Room persistence.
 * Follows the same pattern as [com.wirewhisper.firewall.BlockingEngine].
 *
 * Hot-path [isWatched] is O(1) via concurrent set lookups.
 */
class WatchlistEngine(
    private val dao: WatchlistDao,
    private val scope: CoroutineScope,
) {
    private val watchedHostnames = ConcurrentHashMap.newKeySet<String>()
    private val watchedIps = ConcurrentHashMap.newKeySet<String>()

    private val _entries = MutableStateFlow<List<WatchlistEntry>>(emptyList())
    val entries: StateFlow<List<WatchlistEntry>> = _entries.asStateFlow()

    suspend fun loadEntries() {
        val all = dao.getAllOnce()
        watchedHostnames.clear()
        watchedIps.clear()
        for (entry in all) {
            when (entry.type) {
                "hostname" -> watchedHostnames.add(entry.value)
                "ip" -> watchedIps.add(entry.value)
            }
        }
        emitEntries(all)
    }

    /** Values are stored normalized (lowercased), so callers should pass lowercased input for matching. */
    fun isWatched(hostname: String?, ip: String?): Boolean {
        if (hostname != null && hostname in watchedHostnames) return true
        if (ip != null && ip in watchedIps) return true
        return false
    }

    /** Add a watchlist entry. Value is automatically normalized (lowercased/trimmed). Type is inferred from the value. */
    fun addEntry(value: String, label: String? = null) {
        val normalized = value.lowercase().trim()
        val type = if (isIpv4Address(normalized)) "ip" else "hostname"
        addEntryInternal(type, normalized, label)
    }

    private fun addEntryInternal(type: String, value: String, label: String?) {
        scope.launch {
            val id = dao.insert(
                WatchlistEntryEntity(type = type, value = value, label = label)
            )
            if (id != -1L) {
                addToSets(type, value)
                refreshEntries()
            }
        }
    }

    /** Batch insert with a single refresh at the end. Values are normalized internally. */
    fun addEntriesBatch(values: List<String>) {
        scope.launch {
            for (raw in values) {
                val normalized = raw.lowercase().trim()
                val type = if (isIpv4Address(normalized)) "ip" else "hostname"
                val id = dao.insert(
                    WatchlistEntryEntity(type = type, value = normalized)
                )
                if (id != -1L) {
                    addToSets(type, normalized)
                }
            }
            refreshEntries()
        }
    }

    fun removeEntry(id: Long) {
        scope.launch {
            val entry = dao.getById(id) ?: return@launch
            dao.deleteById(id)
            removeFromSets(entry.type, entry.value)
            refreshEntries()
        }
    }

    fun removeByValue(value: String) {
        scope.launch {
            dao.deleteByValue(value)
            watchedHostnames.remove(value)
            watchedIps.remove(value)
            refreshEntries()
        }
    }

    fun getWatchedHostnames(): Set<String> = watchedHostnames.toSet()
    fun getWatchedIps(): Set<String> = watchedIps.toSet()

    private fun addToSets(type: String, value: String) {
        when (type) {
            "hostname" -> watchedHostnames.add(value)
            "ip" -> watchedIps.add(value)
        }
    }

    private fun removeFromSets(type: String, value: String) {
        when (type) {
            "hostname" -> watchedHostnames.remove(value)
            "ip" -> watchedIps.remove(value)
        }
    }

    private suspend fun refreshEntries() {
        emitEntries(dao.getAllOnce())
    }

    private fun emitEntries(entities: List<WatchlistEntryEntity>) {
        _entries.value = entities.map {
            WatchlistEntry(it.id, it.type, it.value, it.label, it.createdAt)
        }
    }
}
