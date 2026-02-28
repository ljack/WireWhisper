package com.wirewhisper.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress

class OfflineGeoLookupTest {

    /**
     * Build a minimal binary GeoIP DB in memory.
     * Each entry is (ipStartLong, "CC").
     */
    private fun buildDb(
        entries: List<Pair<Long, String>>,
        version: Int = 20260301,
        magic: Int = 0x47454F31,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(magic)
            out.writeInt(version)
            out.writeInt(entries.size)
            out.writeInt(0)
            for ((ip, cc) in entries) {
                out.writeInt(ip.toInt())
                out.writeByte(cc[0].code)
                out.writeByte(cc[1].code)
            }
        }
        return baos.toByteArray()
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        return (parts[0].toLong() shl 24) or
                (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or
                parts[3].toLong()
    }

    private fun load(data: ByteArray): OfflineGeoLookup =
        OfflineGeoLookup.load(ByteArrayInputStream(data))

    // ── Basic functionality ──────────────────────────────────

    @Test
    fun `exact match on range start`() {
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
                ipToLong("2.0.0.0") to "DE",
            )
        )
        val lookup = load(db)
        assertEquals("AU", lookup.lookup(InetAddress.getByName("1.0.0.0")))
        assertEquals("DE", lookup.lookup(InetAddress.getByName("2.0.0.0")))
    }

    @Test
    fun `ip within range`() {
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
                ipToLong("2.0.0.0") to "DE",
                ipToLong("3.0.0.0") to "US",
            )
        )
        val lookup = load(db)
        // 1.128.0.0 falls in the AU range (between 1.0.0.0 and 2.0.0.0)
        assertEquals("AU", lookup.lookup(InetAddress.getByName("1.128.0.0")))
        // 2.255.255.255 falls in the DE range
        assertEquals("DE", lookup.lookup(InetAddress.getByName("2.255.255.255")))
    }

    @Test
    fun `last range covers remaining IPs`() {
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
                ipToLong("200.0.0.0") to "BR",
            )
        )
        val lookup = load(db)
        // 250.0.0.0 is past the last entry, should be in the BR range
        assertEquals("BR", lookup.lookup(InetAddress.getByName("250.0.0.0")))
    }

    @Test
    fun `ip before first range returns null`() {
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
                ipToLong("2.0.0.0") to "DE",
            )
        )
        val lookup = load(db)
        assertNull(lookup.lookup(InetAddress.getByName("0.0.0.1")))
    }

    // ── ZZ (unallocated) ─────────────────────────────────────

    @Test
    fun `ZZ country returns null`() {
        val db = buildDb(
            listOf(
                ipToLong("0.0.0.0") to "ZZ",
                ipToLong("1.0.0.0") to "AU",
            )
        )
        val lookup = load(db)
        assertNull(lookup.lookup(InetAddress.getByName("0.0.0.1")))
        assertEquals("AU", lookup.lookup(InetAddress.getByName("1.0.0.0")))
    }

    // ── IPv6 ─────────────────────────────────────────────────

    @Test
    fun `IPv6 returns null`() {
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
            )
        )
        val lookup = load(db)
        val ipv6 = InetAddress.getByName("2001:db8::1")
        assertNull(lookup.lookup(ipv6))
    }

    // ── Unsigned comparison (high-bit IPs) ───────────────────

    @Test
    fun `IPs above 128_0_0_0 use unsigned comparison`() {
        // IPs above 128.0.0.0 have the high bit set, which would be negative
        // if treated as signed int32. The lookup must handle this correctly.
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
                ipToLong("128.0.0.0") to "US",
                ipToLong("200.0.0.0") to "BR",
                ipToLong("224.0.0.0") to "ZZ", // multicast
            )
        )
        val lookup = load(db)

        assertEquals("AU", lookup.lookup(InetAddress.getByName("100.0.0.0")))
        assertEquals("US", lookup.lookup(InetAddress.getByName("128.0.0.1")))
        assertEquals("US", lookup.lookup(InetAddress.getByName("192.0.0.0")))
        assertEquals("BR", lookup.lookup(InetAddress.getByName("200.0.0.1")))
        assertEquals("BR", lookup.lookup(InetAddress.getByName("223.255.255.255")))
        assertNull(lookup.lookup(InetAddress.getByName("224.0.0.1"))) // ZZ
    }

    @Test
    fun `highest possible IPv4 address`() {
        val db = buildDb(
            listOf(
                ipToLong("0.0.0.0") to "ZZ",
                ipToLong("1.0.0.0") to "AU",
                ipToLong("240.0.0.0") to "US",
            )
        )
        val lookup = load(db)
        // 255.255.255.255 should fall in the US range
        assertEquals("US", lookup.lookup(InetAddress.getByName("255.255.255.255")))
    }

    // ── Header validation ────────────────────────────────────

    @Test(expected = IOException::class)
    fun `invalid magic number throws`() {
        val db = buildDb(listOf(ipToLong("1.0.0.0") to "AU"), magic = 0xDEADBEEF.toInt())
        load(db)
    }

    @Test(expected = IOException::class)
    fun `truncated file throws`() {
        val db = buildDb(listOf(ipToLong("1.0.0.0") to "AU"))
        // Chop off the last 3 bytes to truncate the entry
        load(db.copyOfRange(0, db.size - 3))
    }

    @Test(expected = IOException::class)
    fun `too small file throws`() {
        load(ByteArray(8))
    }

    // ── Metadata ─────────────────────────────────────────────

    @Test
    fun `version and entry count are read correctly`() {
        val db = buildDb(
            listOf(
                ipToLong("1.0.0.0") to "AU",
                ipToLong("2.0.0.0") to "DE",
            ),
            version = 20260301,
        )
        val lookup = load(db)
        assertEquals(20260301, lookup.version)
        assertEquals(2, lookup.entryCount)
    }

    // ── Empty DB ─────────────────────────────────────────────

    @Test
    fun `empty DB returns null for any IP`() {
        val db = buildDb(emptyList())
        val lookup = load(db)
        assertNull(lookup.lookup(InetAddress.getByName("8.8.8.8")))
    }

    // ── Single entry DB ──────────────────────────────────────

    @Test
    fun `single entry DB`() {
        val db = buildDb(listOf(ipToLong("0.0.0.0") to "US"))
        val lookup = load(db)
        assertEquals("US", lookup.lookup(InetAddress.getByName("0.0.0.0")))
        assertEquals("US", lookup.lookup(InetAddress.getByName("128.0.0.0")))
        assertEquals("US", lookup.lookup(InetAddress.getByName("255.255.255.255")))
    }
}
