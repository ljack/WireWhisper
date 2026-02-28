package com.wirewhisper.packet

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class DnsParserTest {

    /** Encode a DNS domain name as wire-format labels. */
    private fun encodeName(name: String): ByteArray {
        val result = mutableListOf<Byte>()
        for (label in name.split(".")) {
            result.add(label.length.toByte())
            result.addAll(label.toByteArray(Charsets.US_ASCII).toList())
        }
        result.add(0) // terminator
        return result.toByteArray()
    }

    /** Build a DNS query packet for the given domain name and query type. */
    private fun buildQuery(
        name: String,
        queryType: Int = 1, // A
        txId: Int = 0x1234,
    ): ByteBuffer {
        val nameBytes = encodeName(name)
        val data = mutableListOf<Int>()
        // Header (12 bytes)
        data.add(txId shr 8); data.add(txId and 0xFF) // Transaction ID
        data.add(0x01); data.add(0x00) // Flags: QR=0, RD=1
        data.add(0x00); data.add(0x01) // QD count = 1
        data.add(0x00); data.add(0x00) // AN count = 0
        data.add(0x00); data.add(0x00) // NS count = 0
        data.add(0x00); data.add(0x00) // AR count = 0
        // Question
        for (b in nameBytes) data.add(b.toInt() and 0xFF)
        data.add(queryType shr 8); data.add(queryType and 0xFF) // QTYPE
        data.add(0x00); data.add(0x01) // QCLASS = IN
        return ByteBuffer.wrap(data.map { it.toByte() }.toByteArray())
    }

    /** Build a DNS response with A records. */
    private fun buildAResponse(
        name: String,
        addresses: List<String>,
        txId: Int = 0x1234,
        ttl: Long = 300,
    ): ByteBuffer {
        val nameBytes = encodeName(name)
        val nameOffset = 12 // start of question name in message
        val data = mutableListOf<Int>()
        // Header
        data.add(txId shr 8); data.add(txId and 0xFF)
        data.add(0x81); data.add(0x80) // QR=1, RD=1, RA=1
        data.add(0x00); data.add(0x01) // QD = 1
        data.add(0x00); data.add(addresses.size) // AN count
        data.add(0x00); data.add(0x00) // NS = 0
        data.add(0x00); data.add(0x00) // AR = 0
        // Question
        for (b in nameBytes) data.add(b.toInt() and 0xFF)
        data.add(0x00); data.add(0x01) // QTYPE = A
        data.add(0x00); data.add(0x01) // QCLASS = IN
        // Answers
        for (addr in addresses) {
            data.add(0xC0); data.add(nameOffset) // Name pointer
            data.add(0x00); data.add(0x01) // TYPE = A
            data.add(0x00); data.add(0x01) // CLASS = IN
            data.add((ttl shr 24).toInt() and 0xFF)
            data.add((ttl shr 16).toInt() and 0xFF)
            data.add((ttl shr 8).toInt() and 0xFF)
            data.add(ttl.toInt() and 0xFF)
            data.add(0x00); data.add(0x04) // RDLENGTH = 4
            val parts = addr.split(".").map { it.toInt() }
            for (p in parts) data.add(p)
        }
        return ByteBuffer.wrap(data.map { it.toByte() }.toByteArray())
    }

    // ---- Query Tests ----

    @Test
    fun `parse standard A query`() {
        val query = DnsParser.parseQuery(buildQuery("www.google.com"))
        assertNotNull(query)
        query!!
        assertEquals(0x1234, query.transactionId)
        assertEquals("www.google.com", query.queryName)
        assertEquals(1, query.queryType) // A
    }

    @Test
    fun `parse AAAA query`() {
        val query = DnsParser.parseQuery(buildQuery("www.google.com", queryType = 28))
        assertNotNull(query)
        query!!
        assertEquals(28, query.queryType)
    }

    @Test
    fun `return null for response packets in parseQuery`() {
        val response = buildAResponse("example.com", listOf("1.2.3.4"))
        assertNull(DnsParser.parseQuery(response))
    }

    @Test
    fun `return null for buffer smaller than 12 bytes in parseQuery`() {
        assertNull(DnsParser.parseQuery(ByteBuffer.wrap(ByteArray(11))))
    }

    @Test
    fun `verify transaction ID extraction`() {
        val query = DnsParser.parseQuery(buildQuery("test.com", txId = 0xABCD))!!
        assertEquals(0xABCD, query.transactionId)
    }

    @Test
    fun `multi-label domain name`() {
        val query = DnsParser.parseQuery(buildQuery("sub.domain.example.com"))!!
        assertEquals("sub.domain.example.com", query.queryName)
    }

    @Test
    fun `single label domain`() {
        val query = DnsParser.parseQuery(buildQuery("localhost"))!!
        assertEquals("localhost", query.queryName)
    }

    // ---- Response Tests ----

    @Test
    fun `parse A record response`() {
        val resp = DnsParser.parseResponse(buildAResponse("www.google.com", listOf("142.250.74.78")))
        assertNotNull(resp)
        resp!!
        assertEquals(0x1234, resp.transactionId)
        assertEquals("www.google.com", resp.queryName)
        assertEquals(1, resp.answers.size)
        assertEquals("142.250.74.78", resp.answers[0].address!!.hostAddress)
        assertEquals(1, resp.answers[0].type) // A
        assertEquals(300L, resp.answers[0].ttl)
    }

    @Test
    fun `parse response with multiple A records`() {
        val resp = DnsParser.parseResponse(
            buildAResponse("dns.google", listOf("8.8.8.8", "8.8.4.4"))
        )!!
        assertEquals(2, resp.answers.size)
        assertEquals("8.8.8.8", resp.answers[0].address!!.hostAddress)
        assertEquals("8.8.4.4", resp.answers[1].address!!.hostAddress)
    }

    @Test
    fun `parse AAAA record response`() {
        val nameBytes = encodeName("www.google.com")
        val nameOffset = 12
        val data = mutableListOf<Int>()
        // Header
        data.addAll(listOf(0x56, 0x78, 0x81, 0x80, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00))
        // Question
        for (b in nameBytes) data.add(b.toInt() and 0xFF)
        data.addAll(listOf(0x00, 0x1C, 0x00, 0x01)) // AAAA, IN
        // Answer
        data.addAll(listOf(0xC0, nameOffset)) // pointer
        data.addAll(listOf(0x00, 0x1C)) // AAAA
        data.addAll(listOf(0x00, 0x01)) // IN
        data.addAll(listOf(0x00, 0x00, 0x00, 0x3C)) // TTL = 60
        data.addAll(listOf(0x00, 0x10)) // RDLENGTH = 16
        // 2001:4860:4860::8844
        data.addAll(listOf(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0x00, 0x00))
        data.addAll(listOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x88, 0x44))

        val resp = DnsParser.parseResponse(ByteBuffer.wrap(data.map { it.toByte() }.toByteArray()))
        assertNotNull(resp)
        resp!!
        assertEquals(1, resp.answers.size)
        assertEquals(28, resp.answers[0].type)
        assertNotNull(resp.answers[0].address)
        assertEquals(60L, resp.answers[0].ttl)
    }

    @Test
    fun `parse CNAME plus A record chain`() {
        val nameBytes = encodeName("www.example.com")
        val nameOffset = 12
        val cnameTarget = encodeName("example.com")
        val data = mutableListOf<Int>()
        // Header
        data.addAll(listOf(0xAB, 0xCD, 0x81, 0x80, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00))
        // Question
        for (b in nameBytes) data.add(b.toInt() and 0xFF)
        data.addAll(listOf(0x00, 0x01, 0x00, 0x01))
        // Answer 1: CNAME
        data.addAll(listOf(0xC0, nameOffset))
        data.addAll(listOf(0x00, 0x05)) // CNAME
        data.addAll(listOf(0x00, 0x01)) // IN
        data.addAll(listOf(0x00, 0x00, 0x01, 0x2C)) // TTL = 300
        data.add(0x00); data.add(cnameTarget.size) // RDLENGTH
        for (b in cnameTarget) data.add(b.toInt() and 0xFF)
        // Answer 2: A for the CNAME target
        // Use inline name (no pointer) for simplicity
        for (b in cnameTarget) data.add(b.toInt() and 0xFF)
        data.addAll(listOf(0x00, 0x01)) // A
        data.addAll(listOf(0x00, 0x01)) // IN
        data.addAll(listOf(0x00, 0x00, 0x00, 0x3C)) // TTL = 60
        data.addAll(listOf(0x00, 0x04)) // RDLENGTH = 4
        data.addAll(listOf(93, 184, 216, 34)) // 93.184.216.34

        val resp = DnsParser.parseResponse(ByteBuffer.wrap(data.map { it.toByte() }.toByteArray()))
        assertNotNull(resp)
        resp!!
        assertEquals(2, resp.answers.size)
        assertEquals(5, resp.answers[0].type) // CNAME
        assertNotNull(resp.answers[0].cname)
        assertEquals(1, resp.answers[1].type) // A
        assertEquals("93.184.216.34", resp.answers[1].address!!.hostAddress)
    }

    @Test
    fun `return null for query packets in parseResponse`() {
        assertNull(DnsParser.parseResponse(buildQuery("example.com")))
    }

    @Test
    fun `return null for response with no answers`() {
        val data = mutableListOf<Int>()
        val nameBytes = encodeName("nx.example.com")
        data.addAll(listOf(0x12, 0x34, 0x81, 0x83, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        for (b in nameBytes) data.add(b.toInt() and 0xFF)
        data.addAll(listOf(0x00, 0x01, 0x00, 0x01))
        assertNull(DnsParser.parseResponse(ByteBuffer.wrap(data.map { it.toByte() }.toByteArray())))
    }

    @Test
    fun `return null for buffer smaller than 12 bytes in parseResponse`() {
        assertNull(DnsParser.parseResponse(ByteBuffer.wrap(ByteArray(11))))
    }

    @Test
    fun `verify TTL extraction`() {
        val resp = DnsParser.parseResponse(
            buildAResponse("test.com", listOf("1.2.3.4"), ttl = 86400)
        )!!
        assertEquals(86400L, resp.answers[0].ttl)
    }

    @Test
    fun `label compression with pointer`() {
        // The buildAResponse uses C0 0C pointer in answers — this tests compression
        val resp = DnsParser.parseResponse(buildAResponse("ptr.test.com", listOf("10.0.0.1")))
        assertNotNull(resp)
        resp!!
        assertEquals("ptr.test.com", resp.answers[0].name)
    }

    @Test
    fun `return null for pointer pointing out of bounds`() {
        val data = mutableListOf<Int>()
        data.addAll(listOf(0x12, 0x34, 0x81, 0x80, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00))
        val nameBytes = encodeName("test.com")
        for (b in nameBytes) data.add(b.toInt() and 0xFF)
        data.addAll(listOf(0x00, 0x01, 0x00, 0x01))
        // Answer with pointer to offset 0xFF (out of bounds)
        data.addAll(listOf(0xC0, 0xFF))
        data.addAll(listOf(0x00, 0x01, 0x00, 0x01))
        data.addAll(listOf(0x00, 0x00, 0x01, 0x2C))
        data.addAll(listOf(0x00, 0x04, 1, 2, 3, 4))
        val resp = DnsParser.parseResponse(ByteBuffer.wrap(data.map { it.toByte() }.toByteArray()))
        // Should return null because the answer name pointer is invalid
        assertNull(resp)
    }

    @Test
    fun `query name from response question section`() {
        val resp = DnsParser.parseResponse(
            buildAResponse("api.github.com", listOf("140.82.121.6"))
        )!!
        assertEquals("api.github.com", resp.queryName)
    }
}
