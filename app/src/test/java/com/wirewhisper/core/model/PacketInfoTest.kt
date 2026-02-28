package com.wirewhisper.core.model

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class PacketInfoTest {

    private fun makePacketInfo(
        protocol: Protocol = Protocol.TCP,
        srcPort: Int = 54321,
        dstPort: Int = 443,
        tcpFlags: Int = 0,
    ) = PacketInfo(
        srcAddress = InetAddress.getByName("192.168.1.100"),
        srcPort = srcPort,
        dstAddress = InetAddress.getByName("93.184.216.34"),
        dstPort = dstPort,
        protocol = protocol,
        ipVersion = 4,
        ipHeaderLength = 20,
        transportHeaderLength = 20,
        payloadLength = 100,
        totalLength = 140,
        tcpFlags = tcpFlags,
    )

    @Test
    fun `flowKey constructs correct FlowKey`() {
        val info = makePacketInfo()
        val key = info.flowKey
        assertEquals(InetAddress.getByName("192.168.1.100"), key.srcAddress)
        assertEquals(54321, key.srcPort)
        assertEquals(InetAddress.getByName("93.184.216.34"), key.dstAddress)
        assertEquals(443, key.dstPort)
        assertEquals(Protocol.TCP, key.protocol)
    }

    @Test
    fun `reverseFlowKey swaps src and dst`() {
        val info = makePacketInfo()
        val rev = info.reverseFlowKey
        assertEquals(InetAddress.getByName("93.184.216.34"), rev.srcAddress)
        assertEquals(443, rev.srcPort)
        assertEquals(InetAddress.getByName("192.168.1.100"), rev.dstAddress)
        assertEquals(54321, rev.dstPort)
        assertEquals(Protocol.TCP, rev.protocol)
    }

    @Test
    fun `flowKey and reverseFlowKey are inverses`() {
        val info = makePacketInfo()
        val fwd = info.flowKey
        val rev = info.reverseFlowKey
        assertEquals(fwd.srcAddress, rev.dstAddress)
        assertEquals(fwd.dstAddress, rev.srcAddress)
        assertEquals(fwd.srcPort, rev.dstPort)
        assertEquals(fwd.dstPort, rev.srcPort)
    }

    @Test
    fun `isSyn - SYN only without ACK`() {
        val info = makePacketInfo(tcpFlags = TcpFlags.SYN)
        assertTrue(info.isSyn)
        assertFalse(info.isSynAck)
    }

    @Test
    fun `isSynAck - SYN with ACK`() {
        val info = makePacketInfo(tcpFlags = TcpFlags.SYN or TcpFlags.ACK)
        assertTrue(info.isSynAck)
        assertFalse(info.isSyn) // isSyn requires no ACK
    }

    @Test
    fun `isFin`() {
        val info = makePacketInfo(tcpFlags = TcpFlags.FIN or TcpFlags.ACK)
        assertTrue(info.isFin)
        assertTrue(info.isAck)
    }

    @Test
    fun `isRst`() {
        val info = makePacketInfo(tcpFlags = TcpFlags.RST)
        assertTrue(info.isRst)
    }

    @Test
    fun `isAck`() {
        val info = makePacketInfo(tcpFlags = TcpFlags.ACK)
        assertTrue(info.isAck)
        assertFalse(info.isSyn)
        assertFalse(info.isSynAck)
    }

    @Test
    fun `isTcp true for TCP protocol`() {
        assertTrue(makePacketInfo(protocol = Protocol.TCP).isTcp)
    }

    @Test
    fun `isTcp false for UDP protocol`() {
        assertFalse(makePacketInfo(protocol = Protocol.UDP).isTcp)
    }

    @Test
    fun `isUdp true for UDP protocol`() {
        assertTrue(makePacketInfo(protocol = Protocol.UDP).isUdp)
    }

    @Test
    fun `isUdp false for TCP protocol`() {
        assertFalse(makePacketInfo(protocol = Protocol.TCP).isUdp)
    }

    @Test
    fun `flags false for UDP packets`() {
        val info = makePacketInfo(protocol = Protocol.UDP, tcpFlags = 0x02)
        // Even with tcpFlags set, protocol is UDP so isSyn should be false
        assertFalse(info.isSyn)
        assertFalse(info.isAck)
    }
}
