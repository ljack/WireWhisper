package com.wirewhisper.watchlist

import com.wirewhisper.data.db.WatchlistDao
import com.wirewhisper.data.db.WatchlistEntryEntity
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

    fun isWatched(hostname: String?, ip: String?): Boolean {
        if (hostname != null && hostname.lowercase() in watchedHostnames) return true
        if (ip != null && ip in watchedIps) return true
        return false
    }

    /** Callers must normalize (lowercase/trim) the value before calling. */
    fun addEntry(type: String, value: String, label: String? = null) {
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

    /** Batch insert with a single refresh at the end. */
    fun addEntriesBatch(entries: List<Pair<String, String>>) {
        scope.launch {
            for ((type, value) in entries) {
                val id = dao.insert(
                    WatchlistEntryEntity(type = type, value = value)
                )
                if (id != -1L) {
                    addToSets(type, value)
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
