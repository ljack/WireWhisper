package com.wirewhisper.packet

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class TlsParserTest {

    /**
     * Build a TLS ClientHello with SNI extension.
     * @param hostname the SNI hostname, or null for no SNI extension
     * @param extraExtensions list of (type, data) pairs to add before SNI
     */
    private fun buildClientHello(
        hostname: String? = "example.com",
        sessionIdLen: Int = 0,
        cipherSuiteCount: Int = 1,
        compressionMethodCount: Int = 1,
        extraExtensions: List<Pair<Int, ByteArray>> = emptyList(),
    ): ByteBuffer {
        val result = mutableListOf<Int>()

        // -- Build ClientHello body first to know lengths --
        val body = mutableListOf<Int>()
        // Client Version (TLS 1.2)
        body.add(0x03); body.add(0x03)
        // Random (32 bytes)
        repeat(32) { body.add(0x00) }
        // Session ID
        body.add(sessionIdLen)
        repeat(sessionIdLen) { body.add(0xAA) }
        // Cipher Suites
        val csLen = cipherSuiteCount * 2
        body.add(csLen shr 8); body.add(csLen and 0xFF)
        repeat(cipherSuiteCount) { body.add(0x00); body.add(0x2F) }
        // Compression Methods
        body.add(compressionMethodCount)
        repeat(compressionMethodCount) { body.add(0x00) }

        // Extensions
        val extensions = mutableListOf<Int>()
        for ((extType, extData) in extraExtensions) {
            extensions.add(extType shr 8); extensions.add(extType and 0xFF)
            extensions.add(extData.size shr 8); extensions.add(extData.size and 0xFF)
            for (b in extData) extensions.add(b.toInt() and 0xFF)
        }
        if (hostname != null) {
            val nameBytes = hostname.toByteArray(Charsets.US_ASCII)
            val sniListLen = 1 + 2 + nameBytes.size // type(1) + len(2) + name
            val extDataLen = 2 + sniListLen // list length(2) + list

            extensions.add(0x00); extensions.add(0x00) // SNI extension type
            extensions.add(extDataLen shr 8); extensions.add(extDataLen and 0xFF)
            extensions.add(sniListLen shr 8); extensions.add(sniListLen and 0xFF)
            extensions.add(0x00) // host_name type
            extensions.add(nameBytes.size shr 8); extensions.add(nameBytes.size and 0xFF)
            for (b in nameBytes) extensions.add(b.toInt() and 0xFF)
        }

        val extTotalLen = extensions.size
        body.add(extTotalLen shr 8); body.add(extTotalLen and 0xFF)
        body.addAll(extensions)

        val hsLen = body.size
        val recordLen = 4 + hsLen // handshake header (4) + body

        // TLS record header (5 bytes)
        result.add(0x16) // ContentType = Handshake
        result.add(0x03); result.add(0x01) // TLS 1.0 record version
        result.add(recordLen shr 8); result.add(recordLen and 0xFF)
        // Handshake header (4 bytes)
        result.add(0x01) // ClientHello
        result.add((hsLen shr 16) and 0xFF)
        result.add((hsLen shr 8) and 0xFF)
        result.add(hsLen and 0xFF)
        // Body
        result.addAll(body)

        return ByteBuffer.wrap(result.map { it.toByte() }.toByteArray())
    }

    @Test
    fun `extract SNI from minimal ClientHello`() {
        val sni = TlsParser.extractSni(buildClientHello("example.com"))
        assertNotNull(sni)
        assertEquals("example.com", sni!!.hostname)
    }

    @Test
    fun `extract SNI with session ID and multiple cipher suites`() {
        val sni = TlsParser.extractSni(
            buildClientHello(
                hostname = "api.github.com",
                sessionIdLen = 32,
                cipherSuiteCount = 10,
                compressionMethodCount = 1,
            )
        )
        assertNotNull(sni)
        assertEquals("api.github.com", sni!!.hostname)
    }

    @Test
    fun `return null for non-handshake content type`() {
        val data = byteArrayOf(
            0x17.toByte(), // Application Data, not Handshake
            0x03, 0x03, 0x00, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05,
        )
        assertNull(TlsParser.extractSni(ByteBuffer.wrap(data)))
    }

    @Test
    fun `return null for non-ClientHello handshake`() {
        val data = mutableListOf<Int>()
        data.add(0x16) // Handshake
        data.add(0x03); data.add(0x03) // Version
        data.add(0x00); data.add(0x04) // Record length = 4
        data.add(0x02) // ServerHello (not ClientHello)
        data.add(0x00); data.add(0x00); data.add(0x00) // Handshake length = 0
        assertNull(TlsParser.extractSni(ByteBuffer.wrap(data.map { it.toByte() }.toByteArray())))
    }

    @Test
    fun `return null for ClientHello with no SNI extension`() {
        assertNull(TlsParser.extractSni(buildClientHello(hostname = null)))
    }

    @Test
    fun `return null for truncated TLS record`() {
        val full = buildClientHello("test.com")
        // Truncate to half
        val truncated = ByteArray(full.remaining() / 2)
        full.get(truncated)
        assertNull(TlsParser.extractSni(ByteBuffer.wrap(truncated)))
    }

    @Test
    fun `return null for empty buffer`() {
        assertNull(TlsParser.extractSni(ByteBuffer.allocate(0)))
    }

    @Test
    fun `return null for buffer smaller than 5 bytes`() {
        assertNull(TlsParser.extractSni(ByteBuffer.wrap(byteArrayOf(0x16, 0x03, 0x01, 0x00))))
    }

    @Test
    fun `extract SNI for long domain name`() {
        val longDomain = "very-long-subdomain.another-part.deeply.nested.example.co.uk"
        val sni = TlsParser.extractSni(buildClientHello(longDomain))
        assertNotNull(sni)
        assertEquals(longDomain, sni!!.hostname)
    }

    @Test
    fun `extract SNI for short domain`() {
        val sni = TlsParser.extractSni(buildClientHello("a.io"))
        assertNotNull(sni)
        assertEquals("a.io", sni!!.hostname)
    }

    @Test
    fun `walk past extensions before SNI`() {
        // Add dummy extensions (type 0xFF01 and 0x000D) before SNI
        val sni = TlsParser.extractSni(
            buildClientHello(
                hostname = "after-ext.example.com",
                extraExtensions = listOf(
                    0xFF01 to ByteArray(16), // Renegotiation Info
                    0x000D to ByteArray(20), // Signature Algorithms
                    0x000A to ByteArray(8),  // Supported Groups
                ),
            )
        )
        assertNotNull(sni)
        assertEquals("after-ext.example.com", sni!!.hostname)
    }

    @Test
    fun `zero-length session ID and compression`() {
        val sni = TlsParser.extractSni(
            buildClientHello(
                hostname = "minimal.test",
                sessionIdLen = 0,
                cipherSuiteCount = 1,
                compressionMethodCount = 1,
            )
        )
        assertNotNull(sni)
        assertEquals("minimal.test", sni!!.hostname)
    }
}
