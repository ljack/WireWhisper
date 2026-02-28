package com.wirewhisper.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wirewhisper.WireWhisperApp
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.core.model.Protocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HistoryFilter(
    val app: String? = null,
    val country: String? = null,
    val protocol: Protocol? = null,
    val sinceMs: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WireWhisperApp).flowRepository

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    val flows: StateFlow<List<FlowRecord>> = _filter
        .flatMapLatest { f ->
            repository.getFlowsFiltered(
                packageName = f.app,
                country = f.country,
                protocolNumber = f.protocol?.number,
                sinceMs = f.sinceMs,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableApps: StateFlow<List<String>> = repository.getDistinctApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCountries: StateFlow<List<String>> = repository.getDistinctCountries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun clearFilters() {
        _filter.value = HistoryFilter()
    }
}
