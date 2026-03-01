package com.wirewhisper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wirewhisper.core.model.FlowKey
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.core.model.Protocol
import java.net.InetAddress

/**
 * Room entity representing a persisted flow record.
 *
 * Indexes on [packageName], [country], and [lastSeen] support the
 * filter-by-app, filter-by-country, and time-range queries used
 * in the History screen.
 */
@Entity(
    tableName = "flows",
    indices = [
        Index("packageName"),
        Index("country"),
        Index("lastSeen"),
        Index("protocol"),
        Index("blocked"),
    ]
)
data class FlowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val srcAddress: String,
    val srcPort: Int,
    val dstAddress: String,
    val dstPort: Int,
    val protocol: Int,          // Protocol.number
    val uid: Int,
    val packageName: String?,
    val appName: String?,
    val bytesSent: Long,
    val bytesReceived: Long,
    val packetsSent: Int,
    val packetsReceived: Int,
    val firstSeen: Long,        // epoch millis
    val lastSeen: Long,
    val country: String?,       // ISO country code
    val dnsHostname: String?,
    val blocked: Boolean = false,
    val blockReason: String? = null,
) {
    companion object {
        fun fromFlowRecord(record: FlowRecord): FlowEntity = FlowEntity(
            srcAddress = record.key.srcAddress.hostAddress ?: "",
            srcPort = record.key.srcPort,
            dstAddress = record.key.dstAddress.hostAddress ?: "",
            dstPort = record.key.dstPort,
            protocol = record.key.protocol.number,
            uid = record.uid,
            packageName = record.packageName,
            appName = record.appName,
            bytesSent = record.bytesSent,
            bytesReceived = record.bytesReceived,
            packetsSent = record.packetsSent,
            packetsReceived = record.packetsReceived,
            firstSeen = record.firstSeen,
            lastSeen = record.lastSeen,
            country = record.country,
            dnsHostname = record.dnsHostname,
            blocked = record.blocked,
            blockReason = record.blockReason,
        )
    }

    fun toFlowRecord(): FlowRecord {
        val key = FlowKey(
            srcAddress = InetAddress.getByName(srcAddress),
            srcPort = srcPort,
            dstAddress = InetAddress.getByName(dstAddress),
            dstPort = dstPort,
            protocol = Protocol.fromNumber(protocol),
        )
        return FlowRecord(
            key = key,
            uid = uid,
            packageName = packageName,
            appName = appName,
            firstSeen = firstSeen,
        ).also {
            it.bytesSent = bytesSent
            it.bytesReceived = bytesReceived
            it.packetsSent = packetsSent
            it.packetsReceived = packetsReceived
            it.lastSeen = lastSeen
            it.country = country
            it.dnsHostname = dnsHostname
            it.blocked = blocked
            it.blockReason = blockReason
        }
    }
}
