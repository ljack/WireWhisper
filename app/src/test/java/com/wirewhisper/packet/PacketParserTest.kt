package com.wirewhisper.packet

import com.wirewhisper.core.model.Protocol
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class PacketParserTest {

    private val parser = PacketParser()

    private fun buf(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(values.map { it.toByte() }.toByteArray())

    /** Build complete IPv4 + TCP packet (20 + 20 = 40 bytes min). */
    private fun ipv4TcpPacket(
        srcIp: List<Int> = listOf(192, 168, 1, 100),
        dstIp: List<Int> = listOf(93, 184, 216, 34),
        srcPort: Int = 54321,
        dstPort: Int = 443,
        tcpFlags: Int = 0x02,
        payloadSize: Int = 10,
    ): ByteBuffer {
        val totalLength = 20 + 20 + payloadSize
        val doFlags = (5 shl 12) or (tcpFlags and 0x3F)
        val data = mutableListOf(
            // IPv4 header (20 bytes)
            0x45, 0x00,
            totalLength shr 8, totalLength and 0xFF,
            0x12, 0x34, 0x40, 0x00, 0x40,
            6, // TCP
            0x00, 0x00,
            srcIp[0], srcIp[1], srcIp[2], srcIp[3],
            dstIp[0], dstIp[1], dstIp[2], dstIp[3],
            // TCP header (20 bytes)
            srcPort shr 8, srcPort and 0xFF,
            dstPort shr 8, dstPort and 0xFF,
            0x00, 0x00, 0x00, 0x01, // Seq
            0x00, 0x00, 0x00, 0x00, // Ack
            doFlags shr 8, doFlags and 0xFF,
            0xFF, 0xFF, // Window
            0x00, 0x00, 0x00, 0x00, // Checksum + Urgent
        )
        // Add payload bytes
        repeat(payloadSize) { data.add(0xAA) }
        return buf(*data.toIntArray())
    }

    /** Build complete IPv4 + UDP packet (20 + 8 = 28 bytes min). */
    private fun ipv4UdpPacket(
        srcPort: Int = 12345,
        dstPort: Int = 53,
        payloadSize: Int = 20,
    ): ByteBuffer {
        val udpLength = 8 + payloadSize
        val totalLength = 20 + udpLength
        val data = mutableListOf(
            // IPv4 header (20 bytes)
            0x45, 0x00,
            totalLength shr 8, totalLength and 0xFF,
            0x12, 0x34, 0x40, 0x00, 0x40,
            17, // UDP
            0x00, 0x00,
            10, 0, 0, 1,
            8, 8, 8, 8,
            // UDP header (8 bytes)
            srcPort shr 8, srcPort and 0xFF,
            dstPort shr 8, dstPort and 0xFF,
            udpLength shr 8, udpLength and 0xFF,
            0x00, 0x00, // Checksum
        )
        repeat(payloadSize) { data.add(0xBB) }
        return buf(*data.toIntArray())
    }

    /** Build IPv6 + TCP packet (40 + 20 = 60 bytes min). */
    private fun ipv6TcpPacket(payloadSize: Int = 10): ByteBuffer {
        val tcpLen = 20 + payloadSize
        val data = IntArray(40 + tcpLen)
        data[0] = 0x60
        data[4] = tcpLen shr 8; data[5] = tcpLen and 0xFF
        data[6] = 6 // TCP
        data[7] = 64
        data[23] = 1 // src ::1
        data[39] = 2 // dst ::2
        // TCP header at offset 40
        data[40] = 0x00; data[41] = 0x50 // srcPort 80
        data[42] = 0x01; data[43] = 0xBB // dstPort 443
        data[44] = 0; data[45] = 0; data[46] = 0; data[47] = 1 // seq
        data[48] = 0; data[49] = 0; data[50] = 0; data[51] = 0 // ack
        val doFlags = (5 shl 12) or 0x02 // SYN
        data[52] = doFlags shr 8; data[53] = doFlags and 0xFF
        data[54] = 0xFF; data[55] = 0xFF // window
        return buf(*data)
    }

    /** Build IPv6 + UDP packet. */
    private fun ipv6UdpPacket(payloadSize: Int = 10): ByteBuffer {
        val udpLen = 8 + payloadSize
        val data = IntArray(40 + udpLen)
        data[0] = 0x60
        data[4] = udpLen shr 8; data[5] = udpLen and 0xFF
        data[6] = 17 // UDP
        data[7] = 64
        data[23] = 1; data[39] = 2
        // UDP header at offset 40
        data[40] = 0x30; data[41] = 0x39 // srcPort 12345
        data[42] = 0x00; data[43] = 0x35 // dstPort 53
        data[44] = udpLen shr 8; data[45] = udpLen and 0xFF
        return buf(*data)
    }

    @Test
    fun `parse IPv4 TCP packet`() {
        val info = parser.parse(ipv4TcpPacket())
        assertNotNull(info)
        info!!
        assertEquals(Protocol.TCP, info.protocol)
        assertEquals(4, info.ipVersion)
        assertEquals(54321, info.srcPort)
        assertEquals(443, info.dstPort)
        assertEquals("192.168.1.100", info.srcAddress.hostAddress)
        assertEquals("93.184.216.34", info.dstAddress.hostAddress)
    }

    @Test
    fun `parse IPv4 UDP packet`() {
        val info = parser.parse(ipv4UdpPacket())
        assertNotNull(info)
        info!!
        assertEquals(Protocol.UDP, info.protocol)
        assertEquals(4, info.ipVersion)
        assertEquals(12345, info.srcPort)
        assertEquals(53, info.dstPort)
    }

    @Test
    fun `parse IPv6 TCP packet`() {
        val info = parser.parse(ipv6TcpPacket())
        assertNotNull(info)
        info!!
        assertEquals(Protocol.TCP, info.protocol)
        assertEquals(6, info.ipVersion)
        assertEquals(80, info.srcPort)
        assertEquals(443, info.dstPort)
    }

    @Test
    fun `parse IPv6 UDP packet`() {
        val info = parser.parse(ipv6UdpPacket())
        assertNotNull(info)
        info!!
        assertEquals(Protocol.UDP, info.protocol)
        assertEquals(6, info.ipVersion)
        assertEquals(12345, info.srcPort)
        assertEquals(53, info.dstPort)
    }

    @Test
    fun `return null for empty buffer`() {
        assertNull(parser.parse(ByteBuffer.allocate(0)))
    }

    @Test
    fun `return null for unknown IP version`() {
        // Version = 3 (invalid)
        assertNull(parser.parse(buf(0x30, 0x00)))
    }

    @Test
    fun `payload length calculated correctly for TCP`() {
        val info = parser.parse(ipv4TcpPacket(payloadSize = 100))!!
        assertEquals(100, info.payloadLength)
        assertEquals(20, info.ipHeaderLength)
        assertEquals(20, info.transportHeaderLength)
        assertEquals(140, info.totalLength) // 20 + 20 + 100
    }

    @Test
    fun `payload length calculated correctly for UDP`() {
        val info = parser.parse(ipv4UdpPacket(payloadSize = 50))!!
        assertEquals(50, info.payloadLength)
        assertEquals(8, info.transportHeaderLength)
    }

    @Test
    fun `flowKey and reverseFlowKey are inverses`() {
        val info = parser.parse(ipv4TcpPacket())!!
        val fwd = info.flowKey
        val rev = info.reverseFlowKey
        assertEquals(fwd.srcAddress, rev.dstAddress)
        assertEquals(fwd.dstAddress, rev.srcAddress)
        assertEquals(fwd.srcPort, rev.dstPort)
        assertEquals(fwd.dstPort, rev.srcPort)
        assertEquals(fwd.protocol, rev.protocol)
    }

    @Test
    fun `ICMP packet parsed with zero ports`() {
        val totalLength = 28 // 20 IP + 8 ICMP
        val data = mutableListOf(
            0x45, 0x00,
            totalLength shr 8, totalLength and 0xFF,
            0x12, 0x34, 0x00, 0x00, 0x40,
            1, // ICMP
            0x00, 0x00,
            10, 0, 0, 1, 10, 0, 0, 2,
        )
        repeat(8) { data.add(0x00) } // ICMP data
        val info = parser.parse(buf(*data.toIntArray()))
        assertNotNull(info)
        info!!
        assertEquals(Protocol.ICMP, info.protocol)
        assertEquals(0, info.srcPort)
        assertEquals(0, info.dstPort)
    }

    @Test
    fun `TCP flags preserved in PacketInfo`() {
        val info = parser.parse(ipv4TcpPacket(tcpFlags = 0x12))!! // SYN-ACK
        assertTrue(info.isSynAck)
        assertFalse(info.isSyn) // isSyn requires SYN without ACK
    }
}
