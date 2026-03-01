package com.wirewhisper.ui.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wirewhisper.WireWhisperApp
import com.wirewhisper.watchlist.WatchlistEntry
import kotlinx.coroutines.flow.StateFlow

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WireWhisperApp
    private val engine = app.watchlistEngine

    val entries: StateFlow<List<WatchlistEntry>> = engine.entries

    fun addEntry(value: String, label: String? = null) {
        engine.addEntry(value, label)
    }

    fun addEntries(lines: String) {
        val values = lines.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { extractHostname(it) }
            .filter { it.isNotBlank() }
        engine.addEntriesBatch(values)
    }

    fun removeEntry(id: Long) {
        engine.removeEntry(id)
    }

    companion object {
        fun extractHostname(input: String): String {
            var cleaned = input.trim().trimEnd('.')
            val parenIdx = cleaned.indexOf('(')
            if (parenIdx > 0) {
                cleaned = cleaned.substring(0, parenIdx).trim().trimEnd('.')
            }
            return cleaned.lowercase()
        }
    }
}
