package com.wirewhisper.geo

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/**
 * Resolves an IP address to geographic information (at minimum: country code).
 *
 * Implementations must be safe to call from any thread. Results should be
 * cached aggressively since the same IPs appear in many flows.
 */
interface GeoResolver {
    suspend fun resolve(address: InetAddress): GeoResult?
}

data class GeoResult(
    val countryCode: String,    // ISO 3166-1 alpha-2 (e.g., "US", "DE")
    val countryName: String?,
    val city: String? = null,
    val asn: String? = null,
    val org: String? = null,
)

/**
 * In-memory GeoResolver with optional online lookup via ip-api.com.
 *
 * ### Privacy note
 * When [onlineLookupEnabled] is true, destination IPs are sent to ip-api.com.
 * This is gated behind a user toggle in Settings. When disabled, only the
 * built-in heuristics are used (private ranges, loopback, etc.).
 *
 * ### Production upgrade path
 * Replace this with an embedded MaxMind GeoLite2 database (~5 MB for country-level).
 * This eliminates all network requests and keeps everything on-device.
 * Use `com.maxmind.geoip2:geoip2` with the `.mmdb` file in assets.
 */
class InMemoryGeoResolver(
    @Volatile var onlineLookupEnabled: Boolean = false,
) : GeoResolver {

    companion object {
        private const val TAG = "GeoResolver"
        private const val CACHE_SIZE = 2000
        private const val API_URL = "http://ip-api.com/json/"
        private const val API_TIMEOUT_MS = 3000
    }

    private val cache = LruCache<InetAddress, GeoResult>(CACHE_SIZE)

    override suspend fun resolve(address: InetAddress): GeoResult? {
        // Check cache first
        cache.get(address)?.let { return it }

        // Handle well-known address types
        val builtIn = resolveBuiltIn(address)
        if (builtIn != null) {
            cache.put(address, builtIn)
            return builtIn
        }

        // Online lookup if enabled
        if (onlineLookupEnabled) {
            val result = lookupOnline(address)
            if (result != null) {
                cache.put(address, result)
                return result
            }
        }

        return null
    }

    private fun resolveBuiltIn(address: InetAddress): GeoResult? {
        if (address.isLoopbackAddress) {
            return GeoResult("LO", "Loopback")
        }
        if (address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return GeoResult("LAN", "Local Network")
        }
        if (address is Inet4Address) {
            val bytes = address.address
            // 10.0.0.0/8
            if (bytes[0].toInt() and 0xFF == 10) return GeoResult("LAN", "Local Network")
            // 172.16.0.0/12
            if (bytes[0].toInt() and 0xFF == 172 && bytes[1].toInt() and 0xF0 == 16)
                return GeoResult("LAN", "Local Network")
            // 192.168.0.0/16
            if (bytes[0].toInt() and 0xFF == 192 && bytes[1].toInt() and 0xFF == 168)
                return GeoResult("LAN", "Local Network")
        }
        if (address is Inet6Address) {
            val bytes = address.address
            // ULA: fd00::/8
            if (bytes[0].toInt() and 0xFF == 0xFD) return GeoResult("LAN", "Local Network")
            // Link-local: fe80::/10
            if (bytes[0].toInt() and 0xFF == 0xFE && bytes[1].toInt() and 0xC0 == 0x80)
                return GeoResult("LAN", "Local Network")
        }
        return null
    }

    private suspend fun lookupOnline(address: InetAddress): GeoResult? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_URL${address.hostAddress}?fields=countryCode,country,city,as,org")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = API_TIMEOUT_MS
                conn.readTimeout = API_TIMEOUT_MS
                conn.requestMethod = "GET"

                if (conn.responseCode != 200) return@withContext null

                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                if (json.optString("status") == "fail") return@withContext null

                GeoResult(
                    countryCode = json.optString("countryCode", "??"),
                    countryName = json.optString("country"),
                    city = json.optString("city").takeIf { it.isNotEmpty() },
                    asn = json.optString("as").takeIf { it.isNotEmpty() },
                    org = json.optString("org").takeIf { it.isNotEmpty() },
                )
            } catch (e: Exception) {
                Log.d(TAG, "Online geo lookup failed for ${address.hostAddress}", e)
                null
            }
        }
}
