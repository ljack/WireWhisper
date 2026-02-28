package com.wirewhisper.geo

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirewhisper.WireWhisperApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/**
 * Monthly WorkManager worker that downloads the latest DB-IP Lite CSV,
 * converts it to our compact binary format, and hot-swaps it into
 * [InMemoryGeoResolver].
 */
class GeoDbRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "GeoDbRefresh"
        const val DB_FILENAME = "geoip-country.bin"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = LocalDate.now()
            val yearMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val versionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
            val csvUrl = "https://download.db-ip.com/free/dbip-country-lite-$yearMonth.csv.gz"

            Log.i(TAG, "Downloading $csvUrl")
            val csvBytes = URL(csvUrl).openStream().use { raw ->
                GZIPInputStream(raw).use { it.readBytes() }
            }
            Log.i(TAG, "Downloaded ${csvBytes.size} bytes of CSV")

            val ranges = parseCsv(csvBytes.decodeToString())
            Log.i(TAG, "Parsed ${ranges.size} IPv4 ranges")

            val merged = mergeRanges(ranges)
            Log.i(TAG, "Merged to ${merged.size} ranges")

            val binary = writeBinary(merged, versionDate)

            // Write to temp file, then atomically rename
            val tempFile = File(applicationContext.cacheDir, "$DB_FILENAME.tmp")
            val targetFile = File(applicationContext.filesDir, DB_FILENAME)
            tempFile.writeBytes(binary)
            tempFile.renameTo(targetFile)
            Log.i(TAG, "Wrote ${targetFile.absolutePath} (${merged.size} entries)")

            // Hot-swap into the resolver
            val app = applicationContext as? WireWhisperApp
            app?.geoResolver?.reloadOfflineDb()

            Log.i(TAG, "Refresh complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
            Result.retry()
        }
    }

    private data class GeoRange(val start: Long, val end: Long, val country: String)

    private fun ipv4ToLong(ip: String): Long {
        val parts = ip.split(".")
        return (parts[0].toLong() shl 24) or
                (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or
                parts[3].toLong()
    }

    private fun parseCsv(csv: String): List<GeoRange> {
        val ranges = mutableListOf<GeoRange>()
        csv.lines().forEach { line ->
            if (line.isBlank() || line.contains(":")) return@forEach
            val parts = line.split(",")
            if (parts.size < 3) return@forEach
            val ipStart = parts[0].trim().removeSurrounding("\"")
            val ipEnd = parts[1].trim().removeSurrounding("\"")
            val country = parts[2].trim().removeSurrounding("\"").uppercase()
            if (ipStart.contains(":") || ipEnd.contains(":")) return@forEach
            try {
                ranges.add(GeoRange(ipv4ToLong(ipStart), ipv4ToLong(ipEnd), country))
            } catch (_: Exception) {
                // skip malformed lines
            }
        }
        ranges.sortBy { it.start }
        return ranges
    }

    private fun mergeRanges(ranges: List<GeoRange>): List<GeoRange> {
        val merged = mutableListOf<GeoRange>()
        for (r in ranges) {
            val last = merged.lastOrNull()
            if (last != null && last.country == r.country && r.start <= last.end + 1) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, r.end))
            } else {
                merged.add(r)
            }
        }
        return merged
    }

    private fun writeBinary(ranges: List<GeoRange>, versionDate: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(0x47454F31) // Magic
            out.writeInt(versionDate)
            out.writeInt(ranges.size)
            out.writeInt(0) // Reserved

            for (r in ranges) {
                out.writeInt(r.start.toInt())
                val cc = r.country.padEnd(2).take(2)
                out.writeByte(cc[0].code)
                out.writeByte(cc[1].code)
            }
        }
        return baos.toByteArray()
    }
}
