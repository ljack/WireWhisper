package com.wirewhisper.packet

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class TcpHeaderTest {

    private fun buf(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(values.map { it.toByte() }.toByteArray())

    /**
     * Build a 20-byte TCP header.
     * @param flags raw flags value (lower 6 bits of byte 13)
     */
    private fun tcpHeader(
        srcPort: Int = 12345,
        dstPort: Int = 443,
        seqNum: Long = 1L,
        ackNum: Long = 0L,
        dataOffset: Int = 5, // 20 bytes
        flags: Int = 0x02,   // SYN
        windowSize: Int = 65535,
    ): ByteBuffer {
        val dataOffsetFlags = (dataOffset shl 12) or (flags and 0x3F)
        return buf(
            srcPort shr 8, srcPort and 0xFF,
            dstPort shr 8, dstPort and 0xFF,
            (seqNum shr 24).toInt() and 0xFF, (seqNum shr 16).toInt() and 0xFF,
            (seqNum shr 8).toInt() and 0xFF, seqNum.toInt() and 0xFF,
            (ackNum shr 24).toInt() and 0xFF, (ackNum shr 16).toInt() and 0xFF,
            (ackNum shr 8).toInt() and 0xFF, ackNum.toInt() and 0xFF,
            dataOffsetFlags shr 8, dataOffsetFlags and 0xFF,
            windowSize shr 8, windowSize and 0xFF,
            0x00, 0x00, // Checksum
            0x00, 0x00, // Urgent Pointer
        )
    }

    @Test
    fun `parse standard 20-byte header`() {
        val header = TcpHeader.parse(tcpHeader(), 0)
        assertNotNull(header)
        header!!
        assertEquals(12345, header.srcPort)
        assertEquals(443, header.dstPort)
        assertEquals(1L, header.sequenceNumber)
        assertEquals(0L, header.acknowledgmentNumber)
        assertEquals(20, header.dataOffset)
        assertEquals(65535, header.windowSize)
    }

    @Test
    fun `parse with data offset greater than 5`() {
        // Data offset = 8 → 32 bytes header (with options)
        val data = IntArray(32)
        val srcPort = 80
        val dstPort = 8080
        data[0] = srcPort shr 8; data[1] = srcPort and 0xFF
        data[2] = dstPort shr 8; data[3] = dstPort and 0xFF
        val doFlags = (8 shl 12) or 0x10 // dataOffset=8, ACK
        data[12] = doFlags shr 8; data[13] = doFlags and 0xFF

        val header = TcpHeader.parse(buf(*data), 0)
        assertNotNull(header)
        header!!
        assertEquals(32, header.dataOffset)
        assertEquals(80, header.srcPort)
        assertEquals(8080, header.dstPort)
    }

    @Test
    fun `verify SYN flag extraction`() {
        val header = TcpHeader.parse(tcpHeader(flags = 0x02), 0)!!
        assertEquals(0x02, header.flags)
        assertTrue(header.flags and 0x02 != 0) // SYN set
        assertTrue(header.flags and 0x10 == 0) // ACK not set
    }

    @Test
    fun `verify SYN-ACK flag extraction`() {
        val header = TcpHeader.parse(tcpHeader(flags = 0x12), 0)!!
        assertEquals(0x12, header.flags)
        assertTrue(header.flags and 0x02 != 0) // SYN
        assertTrue(header.flags and 0x10 != 0) // ACK
    }

    @Test
    fun `verify FIN flag`() {
        val header = TcpHeader.parse(tcpHeader(flags = 0x01), 0)!!
        assertTrue(header.flags and 0x01 != 0)
    }

    @Test
    fun `verify RST flag`() {
        val header = TcpHeader.parse(tcpHeader(flags = 0x04), 0)!!
        assertTrue(header.flags and 0x04 != 0)
    }

    @Test
    fun `verify ACK flag`() {
        val header = TcpHeader.parse(tcpHeader(flags = 0x10), 0)!!
        assertTrue(header.flags and 0x10 != 0)
    }

    @Test
    fun `verify PSH-ACK flags`() {
        val header = TcpHeader.parse(tcpHeader(flags = 0x18), 0)!!
        assertTrue(header.flags and 0x08 != 0) // PSH
        assertTrue(header.flags and 0x10 != 0) // ACK
    }

    @Test
    fun `return null for buffer too small`() {
        assertNull(TcpHeader.parse(buf(0x00, 0x50), 0))
    }

    @Test
    fun `return null when offset leaves insufficient data`() {
        val buffer = tcpHeader()
        // Offset of 5 means only 15 bytes left — insufficient
        assertNull(TcpHeader.parse(buffer, 5))
    }

    @Test
    fun `high port numbers - unsigned 16-bit`() {
        val header = TcpHeader.parse(tcpHeader(srcPort = 65535, dstPort = 49152), 0)!!
        assertEquals(65535, header.srcPort)
        assertEquals(49152, header.dstPort)
    }

    @Test
    fun `parse at non-zero offset`() {
        // 20 bytes of IP header padding + TCP header
        val ip = IntArray(20)
        val tcp = tcpHeader(srcPort = 1234, dstPort = 5678)
        val combined = ByteArray(20 + tcp.remaining())
        tcp.get(combined, 20, tcp.remaining())

        val header = TcpHeader.parse(ByteBuffer.wrap(combined), 20)
        assertNotNull(header)
        header!!
        assertEquals(1234, header.srcPort)
        assertEquals(5678, header.dstPort)
    }

    @Test
    fun `sequence number large unsigned value`() {
        val header = TcpHeader.parse(tcpHeader(seqNum = 0xFFFFFFFFL), 0)!!
        assertEquals(0xFFFFFFFFL, header.sequenceNumber)
    }
}
