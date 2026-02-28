import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/**
 * Gradle task to download DB-IP Lite CSV and convert to compact binary format.
 *
 * Usage: ./gradlew downloadGeoDb
 *
 * Downloads the current month's DB-IP Country Lite CSV (free, CC BY 4.0),
 * filters to IPv4 only, merges adjacent same-country ranges, and writes
 * the binary database to app/src/main/assets/geoip-country.bin.
 */

data class GeoRange(val start: Long, val end: Long, val country: String)

fun ipv4ToLong(ip: String): Long {
    val parts = ip.split(".")
    if (parts.size != 4) throw IllegalArgumentException("Invalid IPv4: $ip")
    return (parts[0].toLong() shl 24) or
            (parts[1].toLong() shl 16) or
            (parts[2].toLong() shl 8) or
            parts[3].toLong()
}

tasks.register("downloadGeoDb") {
    group = "geoip"
    description = "Download DB-IP Lite CSV and convert to binary geoip-country.bin"

    doLast {
        val now = LocalDate.now()
        val yearMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val versionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
        val csvUrl = "https://download.db-ip.com/free/dbip-country-lite-$yearMonth.csv.gz"
        val outputFile = file("src/main/assets/geoip-country.bin")

        println("Downloading $csvUrl ...")
        val csvBytes = URL(csvUrl).openStream().use { raw ->
            GZIPInputStream(raw).use { it.readBytes() }
        }
        println("Downloaded ${csvBytes.size} bytes of CSV")

        // Parse CSV, skip IPv6
        val ranges = mutableListOf<GeoRange>()
        csvBytes.decodeToString().lines().forEach { line ->
            if (line.isBlank() || line.contains(":")) return@forEach // skip empty & IPv6
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
        println("Parsed ${ranges.size} IPv4 ranges")

        // Sort by start IP
        ranges.sortBy { it.start }

        // Merge adjacent same-country ranges
        val merged = mutableListOf<GeoRange>()
        for (r in ranges) {
            val last = merged.lastOrNull()
            if (last != null && last.country == r.country && r.start <= last.end + 1) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, r.end))
            } else {
                merged.add(r)
            }
        }
        println("Merged to ${merged.size} ranges")

        // Write binary
        outputFile.parentFile.mkdirs()
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            // Header (16 bytes)
            out.writeInt(0x47454F31) // Magic "GEO1"
            out.writeInt(versionDate) // Version YYYYMMDD
            out.writeInt(merged.size) // Entry count
            out.writeInt(0) // Reserved

            // Entries (6 bytes each)
            for (r in merged) {
                out.writeInt(r.start.toInt()) // ip_start as uint32 BE
                val cc = r.country.padEnd(2).take(2)
                out.writeByte(cc[0].code)
                out.writeByte(cc[1].code)
            }
        }

        outputFile.writeBytes(baos.toByteArray())
        val sizeMB = outputFile.length() / (1024.0 * 1024.0)
        println("Wrote ${outputFile.absolutePath} (${merged.size} entries, ${"%.2f".format(sizeMB)} MB)")
    }
}
