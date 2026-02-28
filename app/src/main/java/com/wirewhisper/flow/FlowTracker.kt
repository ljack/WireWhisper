package com.wirewhisper.flow

import android.util.Log
import com.wirewhisper.core.model.FlowKey
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.core.model.PacketInfo
import com.wirewhisper.data.repository.FlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregates individual packets into connection-level flow records.
 *
 * Each unique 5-tuple (src, srcPort, dst, dstPort, protocol) maps to a single
 * [FlowRecord] that accumulates byte counts, packet counts, and timestamps.
 *
 * ### Lifecycle
 * - **Active flows** are held in memory and exposed via [activeFlows] for the "Now" screen.
 * - When a flow goes idle (no packets for [FlowRecord.IDLE_TIMEOUT_MS]),
 *   [flushTimedOut] moves it to the [FlowRepository] for persistent storage.
 * - When the VPN stops, [flushAll] persists everything.
 *
 * ### Thread safety
 * Uses [ConcurrentHashMap] for the flow table. Individual [FlowRecord] fields
 * are updated under the map's per-key lock via `compute`. The [StateFlow] is
 * updated by snapshot copies to avoid exposing mutable records to the UI.
 */
class FlowTracker {

    companion object {
        private const val TAG = "FlowTracker"
        private const val SNAPSHOT_DEBOUNCE_MS = 500L
    }

    private val flows = ConcurrentHashMap<FlowKey, FlowRecord>()
    private val _activeFlows = MutableStateFlow<List<FlowRecord>>(emptyList())
    val activeFlows: StateFlow<List<FlowRecord>> = _activeFlows.asStateFlow()

    var repository: FlowRepository? = null
    var trafficSampler: TrafficSampler? = null

    @Volatile
    private var lastSnapshotTime = 0L

    /**
     * Called by [com.wirewhisper.vpn.TunProcessor] for every parsed packet.
     * Updates the corresponding flow record or creates a new one.
     *
     * @param info Parsed packet metadata
     * @param outgoing true if the packet is from the device to the internet
     */
    fun onPacket(info: PacketInfo, outgoing: Boolean) {
        val key = if (outgoing) info.flowKey else info.reverseFlowKey
        val now = System.currentTimeMillis()

        flows.compute(key) { _, existing ->
            if (existing != null) {
                if (outgoing) {
                    existing.bytesSent += info.totalLength
                    existing.packetsSent++
                } else {
                    existing.bytesReceived += info.totalLength
                    existing.packetsReceived++
                }
                existing.lastSeen = now
                existing
            } else {
                FlowRecord(
                    key = key,
                    bytesSent = if (outgoing) info.totalLength.toLong() else 0L,
                    bytesReceived = if (outgoing) 0L else info.totalLength.toLong(),
                    packetsSent = if (outgoing) 1 else 0,
                    packetsReceived = if (outgoing) 0 else 1,
                    firstSeen = now,
                    lastSeen = now,
                )
            }
        }

        // Record traffic for sparkline
        val flowRecord = flows[key]
        if (flowRecord != null) {
            trafficSampler?.recordTraffic(flowRecord.uid, info.totalLength)
        }

        // Throttled snapshot for UI
        if (now - lastSnapshotTime > SNAPSHOT_DEBOUNCE_MS) {
            lastSnapshotTime = now
            _activeFlows.value = flows.values.toList()
        }
    }

    /**
     * Enriches a flow with app identity info. Called by [UidResolver]
     * once the UID is determined.
     */
    fun enrichFlow(key: FlowKey, uid: Int, packageName: String?, appName: String?) {
        flows.computeIfPresent(key) { _, record ->
            record.copy(
                uid = uid,
                packageName = packageName,
                appName = appName,
            ).also { updated ->
                updated.bytesSent = record.bytesSent
                updated.bytesReceived = record.bytesReceived
                updated.packetsSent = record.packetsSent
                updated.packetsReceived = record.packetsReceived
                updated.lastSeen = record.lastSeen
                updated.country = record.country
                updated.dnsHostname = record.dnsHostname
            }
        }
    }

    /** Enriches a flow with geo data. */
    fun enrichFlowGeo(key: FlowKey, country: String?, hostname: String?) {
        flows.computeIfPresent(key) { _, record ->
            record.country = country ?: record.country
            record.dnsHostname = hostname ?: record.dnsHostname
            record
        }
    }

    /**
     * Retroactively enriches all active flows whose destination matches [address]
     * with the given hostname. Called when a new DNS mapping is discovered.
     */
    fun enrichFlowsByAddress(address: java.net.InetAddress, hostname: String) {
        for ((key, record) in flows) {
            if (key.dstAddress == address && record.dnsHostname == null) {
                record.dnsHostname = hostname
            }
        }
    }

    /**
     * Flushes flows that have been idle longer than [FlowRecord.IDLE_TIMEOUT_MS]
     * to the repository for persistent storage.
     */
    fun flushTimedOut() {
        val repo = repository ?: return
        val now = System.currentTimeMillis()
        val toFlush = mutableListOf<FlowRecord>()

        val iter = flows.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value.lastSeen > FlowRecord.IDLE_TIMEOUT_MS) {
                toFlush.add(entry.value)
                iter.remove()
            }
        }

        if (toFlush.isNotEmpty()) {
            repo.insertBatch(toFlush)
            Log.d(TAG, "Flushed ${toFlush.size} timed-out flows to repository")
        }

        _activeFlows.value = flows.values.toList()
    }

    /** Flushes all flows to repository. Called when VPN stops. */
    fun flushAll() {
        val repo = repository ?: return
        val all = flows.values.toList()
        if (all.isNotEmpty()) {
            repo.insertBatch(all)
            Log.d(TAG, "Flushed all ${all.size} flows to repository")
        }
        flows.clear()
        _activeFlows.value = emptyList()
    }
}
