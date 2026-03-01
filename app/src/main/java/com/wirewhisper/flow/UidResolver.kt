package com.wirewhisper.flow

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.util.Log
import com.wirewhisper.core.model.AppInfo
import com.wirewhisper.core.model.PacketInfo
import com.wirewhisper.core.model.Protocol
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the UID (and package name) that owns a given network connection.
 *
 * ### Strategy (API 36+)
 *
 * **Primary method: `ConnectivityManager.getConnectionOwnerUid()`**
 * Available since API 29. Works when the caller is the active VPN app (which we are).
 * Requires the connection to be in the kernel's socket table at the time of the call.
 *
 * **Fallback: `/proc/net/tcp6` and `/proc/net/udp6`**
 * Parse the kernel's socket table to match local-address:port → UID.
 * This works on stock Android but has timing issues (the entry might disappear
 * before we read it, or might not yet be present for very short-lived connections).
 *
 * **Caching**:
 * UID → package name resolution is cached since `PackageManager.getPackagesForUid()`
 * is relatively expensive. UIDs don't change during a session.
 *
 * ### Known limitations
 * - Connections that start and finish between our read cycles may be missed
 * - Shared UIDs (android:sharedUserId) report all packages sharing the UID
 * - System UIDs (0, 1000) map to "android" / "system"
 * - QUIC/UDP-based protocols may have short-lived socket entries
 */
class UidResolver(private val context: Context) {

    companion object {
        private const val TAG = "UidResolver"
        private const val UID_SYSTEM = 1000
        private const val UID_ROOT = 0
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

    private val connectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }
    private val packageManager: PackageManager = context.packageManager

    // Cache: UID → AppInfo
    private val uidCache = ConcurrentHashMap<Int, AppInfo>()

    /**
     * Resolves UID and enriches the flow in the given [FlowTracker].
     */
    fun resolveAndEnrich(info: PacketInfo, flowTracker: FlowTracker) {
        if (info.protocol != Protocol.TCP && info.protocol != Protocol.UDP) return

        val uid = resolveUid(info)
        if (uid < 0) return

        val appInfo = resolveAppInfo(uid)
        flowTracker.enrichFlow(
            key = info.flowKey,
            uid = uid,
            packageName = appInfo.packageName,
            appName = appInfo.appName,
        )
    }

    private fun resolveUid(info: PacketInfo): Int {
        // Primary: ConnectivityManager.getConnectionOwnerUid()
        // This is the most reliable method when we are the active VPN
        return try {
            val protocol = when (info.protocol) {
                Protocol.TCP -> android.system.OsConstants.IPPROTO_TCP
                Protocol.UDP -> android.system.OsConstants.IPPROTO_UDP
                else -> return -1
            }
            connectivityManager.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(info.srcAddress, info.srcPort),
                InetSocketAddress(info.dstAddress, info.dstPort),
            )
        } catch (e: SecurityException) {
            Log.d(TAG, "getConnectionOwnerUid denied, falling back to /proc", e)
            resolveUidFromProc(info)
        } catch (e: Exception) {
            Log.d(TAG, "getConnectionOwnerUid failed", e)
            resolveUidFromProc(info)
        }
    }

    /**
     * Fallback: parse /proc/net/tcp6 or /proc/net/udp6 to find the UID.
     *
     * Format of each line:
     * ```
     *   sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
     *    0: 0000000000000000FFFF00000100007F:1F90 0000000000000000FFFF00000100007F:C8A4 01 00000000:00000000 00:00000000 00000000  1000        0 12345 ...
     * ```
     *
     * We match on local port (column 1, after the colon) and extract the UID (column 7).
     */
    private fun resolveUidFromProc(info: PacketInfo): Int {
        val procFile = when (info.protocol) {
            Protocol.TCP -> "/proc/net/tcp6"
            Protocol.UDP -> "/proc/net/udp6"
            else -> return -1
        }

        val localPortHex = String.format("%04X", info.srcPort)

        try {
            BufferedReader(FileReader(procFile)).use { reader ->
                reader.readLine() // skip header
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.trim().split(WHITESPACE_REGEX)
                    if (parts.size >= 8) {
                        val localAddr = parts[1]
                        val port = localAddr.substringAfterLast(':')
                        if (port == localPortHex) {
                            return parts[7].toIntOrNull() ?: -1
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to read $procFile", e)
        }
        return -1
    }

    fun resolveAppInfo(uid: Int): AppInfo {
        uidCache[uid]?.let { return it }

        val info = when (uid) {
            UID_ROOT -> AppInfo(uid, "root", "System (root)")
            UID_SYSTEM -> AppInfo(uid, "android", "Android System")
            else -> {
                val packages = packageManager.getPackagesForUid(uid)
                val packageName = packages?.firstOrNull() ?: "uid:$uid"
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
                AppInfo(uid, packageName, appName)
            }
        }

        uidCache[uid] = info
        return info
    }
}
