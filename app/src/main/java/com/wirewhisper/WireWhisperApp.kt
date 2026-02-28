package com.wirewhisper

import android.app.Application
import com.wirewhisper.data.db.AppDatabase
import com.wirewhisper.data.repository.FlowRepository
import com.wirewhisper.data.repository.RoomFlowRepository
import com.wirewhisper.flow.FlowTracker
import com.wirewhisper.flow.HostnameResolver
import com.wirewhisper.flow.TrafficSampler
import com.wirewhisper.flow.UidResolver
import com.wirewhisper.geo.InMemoryGeoResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Application-scoped singletons for the monitoring pipeline.
 *
 * This is intentionally simple manual DI rather than Hilt/Koin to keep
 * dependencies minimal. Refactor to a DI framework when the object graph
 * grows beyond what's comfortable to manage here.
 */
class WireWhisperApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Pipeline components (lazily initialized) ───────────────

    val trafficSampler: TrafficSampler by lazy { TrafficSampler() }

    val flowTracker: FlowTracker by lazy {
        FlowTracker().also {
            it.repository = flowRepository
            it.trafficSampler = trafficSampler
        }
    }

    val hostnameResolver: HostnameResolver by lazy {
        HostnameResolver(flowTracker)
    }

    val uidResolver: UidResolver by lazy { UidResolver(this) }

    val geoResolver: InMemoryGeoResolver by lazy { InMemoryGeoResolver() }

    val flowRepository: FlowRepository by lazy {
        val db = AppDatabase.getInstance(this)
        RoomFlowRepository(db.flowDao(), applicationScope)
    }

    // ── Observable VPN state ───────────────────────────────────

    val vpnRunning = MutableStateFlow(false)
}
