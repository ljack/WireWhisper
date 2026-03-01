package com.wirewhisper.flow

import android.util.Log
import android.util.LruCache
import com.wirewhisper.core.model.FlowKey
import com.wirewhisper.packet.DnsParser
import com.wirewhisper.packet.TlsParser
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Maintains an IP→hostname cache populated from DNS responses and TLS SNI.
 * Retroactively enriches active flows when a new mapping is discovered.
 */
class HostnameResolver(
    private val flowTracker: FlowTracker,
    cacheSize: Int = 4000,
) {
    companion object {
        private const val TAG = "HostnameResolver"
    }

    private data class CacheEntry(
        val hostname: String,
        val expiresAt: Long,
    )

    private val cache = LruCache<InetAddress, CacheEntry>(cacheSize)

    fun onDnsResponse(payload: ByteBuffer) {
        val response = DnsParser.parseResponse(payload) ?: return
        val now = System.currentTimeMillis()

        for (answer in response.answers) {
            val addr = answer.address ?: continue
            val hostname = if (response.queryName.isNotEmpty()) {
                response.queryName.lowercase()
            } else {
                answer.name.lowercase()
            }

            val ttlMs = (answer.ttl * 1000).coerceAtLeast(60_000)
            cache.put(addr, CacheEntry(hostname, now + ttlMs))
            Log.d(TAG, "DNS: ${addr.hostAddress} → $hostname (TTL ${answer.ttl}s)")

            // Retroactively enrich any active flows to this IP
            flowTracker.enrichFlowsByAddress(addr, hostname)
        }
    }

    fun onTlsClientHello(flowKey: FlowKey, payload: ByteBuffer) {
        val sni = TlsParser.extractSni(payload) ?: return
        val hostname = sni.hostname.lowercase()
        Log.d(TAG, "SNI: ${flowKey.dstAddress.hostAddress} → $hostname")

        val now = System.currentTimeMillis()
        // Cache with a long TTL since SNI is authoritative for this connection
        cache.put(flowKey.dstAddress, CacheEntry(hostname, now + 3600_000))

        flowTracker.enrichFlowGeo(flowKey, country = null, hostname = hostname)
    }

    fun lookupHostname(address: InetAddress): String? {
        val entry = cache.get(address) ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(address)
            return null
        }
        return entry.hostname
    }
}
