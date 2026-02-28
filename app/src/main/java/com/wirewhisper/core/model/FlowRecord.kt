package com.wirewhisper.core.model

import java.net.InetAddress

/**
 * Aggregated flow record representing one logical connection.
 * Updated on every packet belonging to this flow. Flushed to Room
 * when the flow times out or the VPN session ends.
 */
data class FlowRecord(
    val key: FlowKey,
    val uid: Int = UNKNOWN_UID,
    val packageName: String? = null,
    val appName: String? = null,
    var bytesSent: Long = 0L,
    var bytesReceived: Long = 0L,
    var packetsSent: Int = 0,
    var packetsReceived: Int = 0,
    val firstSeen: Long = System.currentTimeMillis(),
    var lastSeen: Long = firstSeen,
    var country: String? = null,
    var dnsHostname: String? = null,
) {
    val dstAddress: InetAddress get() = key.dstAddress
    val dstPort: Int get() = key.dstPort
    val protocol: Protocol get() = key.protocol
    val isActive: Boolean
        get() = (System.currentTimeMillis() - lastSeen) < IDLE_TIMEOUT_MS

    companion object {
        const val UNKNOWN_UID = -1
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }
}
