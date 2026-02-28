package com.wirewhisper.packet

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class Ip4HeaderTest {

    private fun buf(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(values.map { it.toByte() }.toByteArray())

    /** Minimal valid 20-byte IPv4 header with TCP protocol. */
    private fun minimalIp4(
        protocol: Int = 6,
        totalLength: Int = 40,
        srcIp: List<Int> = listOf(192, 168, 1, 1),
        dstIp: List<Int> = listOf(8, 8, 8, 8),
    ): ByteBuffer = buf(
        0x45,                         // Version=4, IHL=5
        0x00,                         // DSCP/ECN
        totalLength shr 8, totalLength and 0xFF,
        0x12, 0x34,                   // Identification
        0x00, 0x00,                   // Flags + Fragment Offset
        0x40,                         // TTL=64
        protocol,                     // Protocol
        0x00, 0x00,                   // Checksum
        srcIp[0], srcIp[1], srcIp[2], srcIp[3],
        dstIp[0], dstIp[1], dstIp[2], dstIp[3],
    )

    @Test
    fun `parse minimal 20-byte header`() {
        val header = Ip4Header.parse(minimalIp4())
        assertNotNull(header)
        header!!
        assertEquals(20, header.headerLength)
        assertEquals(40, header.totalLength)
        assertEquals(6, header.protocol)
        assertEquals("192.168.1.1", header.srcAddress.hostAddress)
        assertEquals("8.8.8.8", header.dstAddress.hostAddress)
    }

    @Test
    fun `parse header with options - IHL 6`() {
        val buffer = buf(
            0x46,                         // Version=4, IHL=6 (24 bytes)
            0x00,
            0x00, 0x2C,                   // Total Length = 44
            0x12, 0x34, 0x00, 0x00, 0x40,
            0x11,                         // Protocol = UDP (17)
            0x00, 0x00,
            10, 0, 0, 1,                  // 10.0.0.1
            10, 0, 0, 2,                  // 10.0.0.2
            0x00, 0x00, 0x00, 0x00,       // Options padding
        )
        val header = Ip4Header.parse(buffer)
        assertNotNull(header)
        header!!
        assertEquals(24, header.headerLength)
        assertEquals(44, header.totalLength)
        assertEquals(17, header.protocol)
    }

    @Test
    fun `return null for buffer smaller than 20 bytes`() {
        assertNull(Ip4Header.parse(buf(0x45, 0x00, 0x00)))
    }

    @Test
    fun `return null for empty buffer`() {
        assertNull(Ip4Header.parse(ByteBuffer.allocate(0)))
    }

    @Test
    fun `return null when buffer smaller than IHL indicates`() {
        // IHL=6 means 24 bytes needed, but only 20 bytes provided
        val data = IntArray(20) { 0 }
        data[0] = 0x46 // IHL=6
        assertNull(Ip4Header.parse(buf(*data)))
    }

    @Test
    fun `extract TCP protocol number 6`() {
        val header = Ip4Header.parse(minimalIp4(protocol = 6))!!
        assertEquals(6, header.protocol)
    }

    @Test
    fun `extract UDP protocol number 17`() {
        val header = Ip4Header.parse(minimalIp4(protocol = 17))!!
        assertEquals(17, header.protocol)
    }

    @Test
    fun `extract ICMP protocol number 1`() {
        val header = Ip4Header.parse(minimalIp4(protocol = 1))!!
        assertEquals(1, header.protocol)
    }

    @Test
    fun `verify source and destination address bytes`() {
        val header = Ip4Header.parse(
            minimalIp4(srcIp = listOf(172, 16, 0, 1), dstIp = listOf(93, 184, 216, 34))
        )!!
        assertEquals("172.16.0.1", header.srcAddress.hostAddress)
        assertEquals("93.184.216.34", header.dstAddress.hostAddress)
    }

    @Test
    fun `buffer position is reset after parsing`() {
        val buffer = minimalIp4()
        val posBefore = buffer.position()
        Ip4Header.parse(buffer)
        assertEquals(posBefore, buffer.position())
    }
}
