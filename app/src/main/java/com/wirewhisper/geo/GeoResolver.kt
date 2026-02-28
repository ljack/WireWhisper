package com.wirewhisper.geo

import android.util.Log
import android.util.LruCache
import com.wirewhisper.data.db.GeoCacheDao
import com.wirewhisper.data.db.GeoCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

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
 * GeoResolver with LRU in-memory cache backed by offline DB and Room cache.
 *
 * Lookup order:
 * 1. In-memory LRU cache (instant)
 * 2. Built-in heuristics for private/loopback ranges (instant)
 * 3. Offline binary DB — instant IPv4 country lookup (no network)
 * 4. Room database cache (fast, survives restarts)
 * 5. Online lookup via ip-api.com (slow, rate-limited)
 *
 * ### Privacy note
 * When [onlineLookupEnabled] is true, destination IPs are sent to ip-api.com.
 * This is gated behind a user toggle in Settings. When disabled, the offline
 * DB still provides country-level resolution for all public IPv4 addresses.
 */
class InMemoryGeoResolver(
    @Volatile var onlineLookupEnabled: Boolean = false,
    private val geoCacheDao: GeoCacheDao? = null,
    private val offlineLookupProvider: (() -> OfflineGeoLookup?)? = null,
) : GeoResolver {

    companion object {
        private const val TAG = "GeoResolver"
        private const val CACHE_SIZE = 2000
        private const val API_URL = "http://ip-api.com/json/"
        private const val API_TIMEOUT_MS = 3000
        private const val DB_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000  // 7 days
    }

    private val cache = LruCache<InetAddress, GeoResult>(CACHE_SIZE)
    private val offlineLookup = AtomicReference<OfflineGeoLookup?>(null)
    @Volatile private var offlineLoaded = false

    override suspend fun resolve(address: InetAddress): GeoResult? {
        // 1. In-memory cache
        cache.get(address)?.let { return it }

        // 2. Built-in heuristics (private ranges, loopback)
        val builtIn = resolveBuiltIn(address)
        if (builtIn != null) {
            cache.put(address, builtIn)
            return builtIn
        }

        // 3. Offline binary DB
        val offlineResult = resolveOffline(address)
        if (offlineResult != null) {
            cache.put(address, offlineResult)
            return offlineResult
        }

        // 4. Room persistent cache
        val ipStr = address.hostAddress ?: return null
        val minTimestamp = System.currentTimeMillis() - DB_CACHE_TTL_MS
        val cached = geoCacheDao?.get(ipStr, minTimestamp)
        if (cached != null) {
            val result = GeoResult(
                countryCode = cached.countryCode,
                countryName = cached.countryName,
                city = cached.city,
                asn = cached.asn,
                org = cached.org,
            )
            cache.put(address, result)
            return result
        }

        // 5. Online lookup if enabled
        if (onlineLookupEnabled) {
            val result = lookupOnline(address)
            if (result != null) {
                cache.put(address, result)
                persistResult(ipStr, result)
                return result
            }
        }

        return null
    }

    private fun ensureOfflineLoaded() {
        if (offlineLoaded) return
        synchronized(this) {
            if (offlineLoaded) return
            try {
                val lookup = offlineLookupProvider?.invoke()
                offlineLookup.set(lookup)
                if (lookup != null) {
                    Log.i(TAG, "Loaded offline GeoIP DB: ${lookup.entryCount} entries, v${lookup.version}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load offline GeoIP DB", e)
            }
            offlineLoaded = true
        }
    }

    private fun resolveOffline(address: InetAddress): GeoResult? {
        ensureOfflineLoaded()
        val lookup = offlineLookup.get() ?: return null
        val cc = lookup.lookup(address) ?: return null
        return GeoResult(
            countryCode = cc,
            countryName = null, // country-level only from offline DB
        )
    }

    /**
     * Reload the offline DB (e.g. after a WorkManager refresh downloads a new version).
     * Safe to call from any thread.
     */
    fun reloadOfflineDb() {
        try {
            val lookup = offlineLookupProvider?.invoke()
            offlineLookup.set(lookup)
            offlineLoaded = true
            // Clear LRU cache so new lookups pick up updated country data
            cache.evictAll()
            if (lookup != null) {
                Log.i(TAG, "Reloaded offline GeoIP DB: ${lookup.entryCount} entries, v${lookup.version}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reload offline GeoIP DB", e)
        }
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

    private suspend fun persistResult(ip: String, result: GeoResult) {
        try {
            geoCacheDao?.upsert(
                GeoCacheEntity(
                    ip = ip,
                    countryCode = result.countryCode,
                    countryName = result.countryName,
                    city = result.city,
                    asn = result.asn,
                    org = result.org,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to persist geo result", e)
        }
    }
}
