package com.wirewhisper.packet

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class UdpHeaderTest {

    private fun buf(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(values.map { it.toByte() }.toByteArray())

    private fun udpHeader(
        srcPort: Int = 12345,
        dstPort: Int = 53,
        length: Int = 30,
    ): ByteBuffer = buf(
        srcPort shr 8, srcPort and 0xFF,
        dstPort shr 8, dstPort and 0xFF,
        length shr 8, length and 0xFF,
        0x00, 0x00, // Checksum
    )

    @Test
    fun `parse 8-byte UDP header`() {
        val header = UdpHeader.parse(udpHeader(), 0)
        assertNotNull(header)
        header!!
        assertEquals(12345, header.srcPort)
        assertEquals(53, header.dstPort)
        assertEquals(30, header.length)
    }

    @Test
    fun `return null for buffer smaller than 8 bytes`() {
        assertNull(UdpHeader.parse(buf(0x00, 0x35, 0x00, 0x35), 0))
    }

    @Test
    fun `return null for empty buffer`() {
        assertNull(UdpHeader.parse(ByteBuffer.allocate(0), 0))
    }

    @Test
    fun `high port numbers - unsigned 16-bit`() {
        val header = UdpHeader.parse(udpHeader(srcPort = 65535, dstPort = 49152), 0)!!
        assertEquals(65535, header.srcPort)
        assertEquals(49152, header.dstPort)
    }

    @Test
    fun `DNS port 53`() {
        val header = UdpHeader.parse(udpHeader(srcPort = 53, dstPort = 1024), 0)!!
        assertEquals(53, header.srcPort)
    }

    @Test
    fun `parse at non-zero offset`() {
        val padding = IntArray(20)
        val udp = udpHeader(srcPort = 4444, dstPort = 5555)
        val combined = ByteArray(20 + udp.remaining())
        udp.get(combined, 20, udp.remaining())

        val header = UdpHeader.parse(ByteBuffer.wrap(combined), 20)
        assertNotNull(header)
        header!!
        assertEquals(4444, header.srcPort)
        assertEquals(5555, header.dstPort)
    }

    @Test
    fun `return null when offset leaves insufficient data`() {
        val buffer = udpHeader()
        assertNull(UdpHeader.parse(buffer, 5))
    }
}
