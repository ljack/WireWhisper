package com.wirewhisper.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wirewhisper.WireWhisperApp
import com.wirewhisper.core.model.FlowRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlowDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WireWhisperApp
    private val _flow = MutableStateFlow<FlowRecord?>(null)
    val flow: StateFlow<FlowRecord?> = _flow.asStateFlow()

    /**
     * Look up a flow by its key hashCode.
     * First checks active flows, then falls back to repository (future).
     */
    fun loadFlow(flowId: Long) {
        val active = app.flowTracker.activeFlows.value.find {
            it.key.hashCode().toLong() == flowId
        }
        _flow.value = active
    }
}
