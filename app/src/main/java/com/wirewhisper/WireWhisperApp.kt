package com.wirewhisper

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wirewhisper.data.db.AppDatabase
import com.wirewhisper.data.repository.FlowRepository
import com.wirewhisper.data.repository.RoomFlowRepository
import com.wirewhisper.firewall.BlockingEngine
import com.wirewhisper.flow.FlowTracker
import com.wirewhisper.flow.HostnameResolver
import com.wirewhisper.flow.TrafficSampler
import com.wirewhisper.flow.UidResolver
import com.wirewhisper.geo.GeoDbRefreshWorker
import com.wirewhisper.geo.InMemoryGeoResolver
import com.wirewhisper.geo.OfflineGeoLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

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

    val geoResolver: InMemoryGeoResolver by lazy {
        val db = AppDatabase.getInstance(this)
        InMemoryGeoResolver(
            geoCacheDao = db.geoCacheDao(),
            offlineLookupProvider = {
                val refreshed = File(filesDir, GeoDbRefreshWorker.DB_FILENAME)
                if (refreshed.exists()) OfflineGeoLookup.loadFromFile(refreshed)
                else OfflineGeoLookup.loadFromAssets(this)
            },
        )
    }

    val flowRepository: FlowRepository by lazy {
        val db = AppDatabase.getInstance(this)
        RoomFlowRepository(db.flowDao(), applicationScope)
    }

    val blockingEngine: BlockingEngine by lazy {
        val db = AppDatabase.getInstance(this)
        BlockingEngine(db.blockRuleDao(), applicationScope)
    }

    suspend fun resetHistory() {
        flowTracker.clearAll()
        trafficSampler.clearAll()
        blockingEngine.resetBlockedCounts()
        flowRepository.deleteAll()
    }

    // ── Observable VPN state ───────────────────────────────────

    val vpnRunning = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        scheduleGeoDbRefresh()
        applicationScope.launch {
            blockingEngine.loadRules()
        }
    }

    private fun scheduleGeoDbRefresh() {
        val request = PeriodicWorkRequestBuilder<GeoDbRefreshWorker>(30, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "geo_db_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
