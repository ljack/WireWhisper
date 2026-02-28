package com.wirewhisper.packet

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class Ip6HeaderTest {

    private fun buf(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(values.map { it.toByte() }.toByteArray())

    /** Build a minimal 40-byte IPv6 header. */
    private fun minimalIp6(
        payloadLength: Int = 20,
        nextHeader: Int = 6, // TCP
    ): ByteBuffer {
        val data = IntArray(40)
        data[0] = 0x60 // Version=6
        data[4] = payloadLength shr 8
        data[5] = payloadLength and 0xFF
        data[6] = nextHeader
        data[7] = 64 // Hop Limit
        // Src: ::1 (bytes 8-23)
        data[23] = 1
        // Dst: ::2 (bytes 24-39)
        data[39] = 2
        return buf(*data)
    }

    @Test
    fun `parse fixed 40-byte IPv6 header`() {
        val header = Ip6Header.parse(minimalIp6())
        assertNotNull(header)
        header!!
        assertEquals(20, header.payloadLength)
        assertEquals(6, header.nextHeader) // TCP
        assertEquals(40, header.headerLength)
    }

    @Test
    fun `verify 128-bit addresses`() {
        val header = Ip6Header.parse(minimalIp6())!!
        val srcBytes = header.srcAddress.address
        assertEquals(16, srcBytes.size)
        assertEquals(1, srcBytes[15].toInt())
        val dstBytes = header.dstAddress.address
        assertEquals(2, dstBytes[15].toInt())
    }

    @Test
    fun `parse with hop-by-hop extension header`() {
        // 40 bytes fixed header + 8 bytes extension header
        val data = IntArray(48)
        data[0] = 0x60
        data[4] = 0; data[5] = 28 // payload = 8 (ext) + 20 (tcp)
        data[6] = 0 // Next Header = Hop-by-Hop (0)
        data[7] = 64
        data[23] = 1; data[39] = 2
        // Extension header at offset 40
        data[40] = 6  // Next Header = TCP
        data[41] = 0  // Length = 0 → (0+1)*8 = 8 bytes
        // Padding (42-47) = 0

        val header = Ip6Header.parse(buf(*data))
        assertNotNull(header)
        header!!
        assertEquals(6, header.nextHeader) // TCP after walking extension
        assertEquals(48, header.headerLength) // 40 + 8
    }

    @Test
    fun `parse with routing extension header`() {
        val data = IntArray(48)
        data[0] = 0x60
        data[4] = 0; data[5] = 28
        data[6] = 43 // Next Header = Routing (43)
        data[7] = 64
        data[23] = 1; data[39] = 2
        data[40] = 17 // Next Header = UDP
        data[41] = 0  // Length = (0+1)*8 = 8

        val header = Ip6Header.parse(buf(*data))
        assertNotNull(header)
        header!!
        assertEquals(17, header.nextHeader)
        assertEquals(48, header.headerLength)
    }

    @Test
    fun `parse with two extension headers`() {
        // Hop-by-Hop → Routing → TCP
        val data = IntArray(56) // 40 + 8 + 8
        data[0] = 0x60
        data[4] = 0; data[5] = 36 // payload = 8 + 8 + 20
        data[6] = 0  // Hop-by-Hop
        data[7] = 64
        data[23] = 1; data[39] = 2
        // Extension 1 (Hop-by-Hop) at 40
        data[40] = 43  // Next Header = Routing
        data[41] = 0   // Length = 8
        // Extension 2 (Routing) at 48
        data[48] = 6   // Next Header = TCP
        data[49] = 0   // Length = 8

        val header = Ip6Header.parse(buf(*data))
        assertNotNull(header)
        header!!
        assertEquals(6, header.nextHeader)
        assertEquals(56, header.headerLength)
    }

    @Test
    fun `return null for buffer smaller than 40 bytes`() {
        assertNull(Ip6Header.parse(buf(*IntArray(39))))
    }

    @Test
    fun `return null for empty buffer`() {
        assertNull(Ip6Header.parse(ByteBuffer.allocate(0)))
    }

    @Test
    fun `extract UDP next header`() {
        val header = Ip6Header.parse(minimalIp6(nextHeader = 17))!!
        assertEquals(17, header.nextHeader)
    }

    @Test
    fun `extract ICMPv6 next header`() {
        val header = Ip6Header.parse(minimalIp6(nextHeader = 58))!!
        assertEquals(58, header.nextHeader)
    }

    @Test
    fun `buffer position is reset after parsing`() {
        val buffer = minimalIp6()
        val posBefore = buffer.position()
        Ip6Header.parse(buffer)
        assertEquals(posBefore, buffer.position())
    }
}
