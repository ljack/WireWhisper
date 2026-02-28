package com.wirewhisper.geo

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact offline IP-to-country lookup using a binary database.
 *
 * Binary format (`geoip-country.bin`):
 * ```
 * HEADER (16 bytes):
 *   0-3:   Magic 0x47454F31 ("GEO1")
 *   4-7:   Version uint32 BE (YYYYMMDD)
 *   8-11:  Entry count uint32 BE
 *   12-15: Reserved (zeros)
 *
 * ENTRIES (6 bytes each, sorted by ip_start):
 *   0-3:   ip_start uint32 BE
 *   4-5:   country_code 2 ASCII bytes
 * ```
 *
 * Ranges are contiguous: end of range N = start of range N+1 minus 1.
 * "ZZ" entries represent unallocated/reserved space and return null.
 *
 * Thread-safe: immutable after construction.
 */
class OfflineGeoLookup private constructor(
    val version: Int,
    private val ipStarts: IntArray,
    private val countryCodes: ByteArray, // 2 bytes per entry, packed
) {
    val entryCount: Int get() = ipStarts.size

    /**
     * Look up the country code for an IP address.
     * Returns null for IPv6, "ZZ" entries, or IPs before the first range.
     */
    fun lookup(address: InetAddress): String? {
        if (address !is Inet4Address) return null
        if (ipStarts.isEmpty()) return null

        val ip = ipToUint32(address)
        val idx = binarySearchRange(ip)
        if (idx < 0) return null

        val cc = countryCodeAt(idx)
        return if (cc == "ZZ") null else cc
    }

    private fun ipToUint32(addr: Inet4Address): Long {
        val bytes = addr.address
        return ((bytes[0].toLong() and 0xFF) shl 24) or
                ((bytes[1].toLong() and 0xFF) shl 16) or
                ((bytes[2].toLong() and 0xFF) shl 8) or
                (bytes[3].toLong() and 0xFF)
    }

    /**
     * Binary search for the range containing [ip].
     * Returns the index of the entry whose ip_start <= ip and
     * whose next entry's ip_start > ip (or is the last entry).
     * Returns -1 if ip is before the first range.
     */
    private fun binarySearchRange(ip: Long): Int {
        var lo = 0
        var hi = ipStarts.size - 1

        // ip is before the first range
        if (ip < (ipStarts[0].toLong() and 0xFFFFFFFFL)) return -1

        while (lo <= hi) {
            val mid = lo + (hi - lo) / 2
            val midStart = ipStarts[mid].toLong() and 0xFFFFFFFFL

            when {
                midStart == ip -> return mid
                midStart < ip -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        // hi now points to the largest entry with ip_start <= ip
        return hi
    }

    private fun countryCodeAt(index: Int): String {
        val offset = index * 2
        val c1 = countryCodes[offset].toInt().toChar()
        val c2 = countryCodes[offset + 1].toInt().toChar()
        return "$c1$c2"
    }

    companion object {
        const val MAGIC = 0x47454F31 // "GEO1"
        const val HEADER_SIZE = 16
        const val ENTRY_SIZE = 6

        /**
         * Load from a raw InputStream. Reads the entire stream and closes it.
         * @throws IOException if the magic number is invalid or data is truncated
         */
        fun load(input: InputStream): OfflineGeoLookup {
            val data = input.use { it.readBytes() }
            if (data.size < HEADER_SIZE) {
                throw IOException("GeoIP DB too small: ${data.size} bytes")
            }

            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val magic = buf.int
            if (magic != MAGIC) {
                throw IOException(
                    "Invalid GeoIP magic: 0x${magic.toString(16)}, expected 0x${MAGIC.toString(16)}"
                )
            }

            val version = buf.int
            val entryCount = buf.int
            buf.int // reserved

            val expectedSize = HEADER_SIZE + entryCount.toLong() * ENTRY_SIZE
            if (data.size < expectedSize) {
                throw IOException(
                    "GeoIP DB truncated: expected $expectedSize bytes, got ${data.size}"
                )
            }

            val ipStarts = IntArray(entryCount)
            val countryCodes = ByteArray(entryCount * 2)

            for (i in 0 until entryCount) {
                ipStarts[i] = buf.int
                countryCodes[i * 2] = buf.get()
                countryCodes[i * 2 + 1] = buf.get()
            }

            return OfflineGeoLookup(version, ipStarts, countryCodes)
        }

        fun loadFromAssets(context: Context): OfflineGeoLookup {
            return load(context.assets.open("geoip-country.bin"))
        }

        fun loadFromFile(file: File): OfflineGeoLookup {
            return load(file.inputStream())
        }
    }
}
