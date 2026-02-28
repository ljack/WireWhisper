package com.wirewhisper.core.model

import java.net.InetAddress

/**
 * Metadata extracted from a single parsed IP packet.
 * Created by [com.wirewhisper.packet.PacketParser] and consumed
 * by [com.wirewhisper.flow.FlowTracker].
 */
data class PacketInfo(
    val srcAddress: InetAddress,
    val srcPort: Int,
    val dstAddress: InetAddress,
    val dstPort: Int,
    val protocol: Protocol,
    val ipVersion: Int,             // 4 or 6
    val ipHeaderLength: Int,        // bytes
    val transportHeaderLength: Int, // bytes
    val payloadLength: Int,         // transport payload bytes
    val totalLength: Int,           // entire IP packet bytes
    val tcpFlags: Int = 0,          // raw TCP flags byte if TCP
) {
    val flowKey: FlowKey
        get() = FlowKey(srcAddress, srcPort, dstAddress, dstPort, protocol)

    /** Reverse flow key (for matching response packets). */
    val reverseFlowKey: FlowKey
        get() = FlowKey(dstAddress, dstPort, srcAddress, srcPort, protocol)

    val isTcp: Boolean get() = protocol == Protocol.TCP
    val isUdp: Boolean get() = protocol == Protocol.UDP
    val isSyn: Boolean get() = isTcp && (tcpFlags and TcpFlags.SYN != 0) && (tcpFlags and TcpFlags.ACK == 0)
    val isSynAck: Boolean get() = isTcp && (tcpFlags and TcpFlags.SYN != 0) && (tcpFlags and TcpFlags.ACK != 0)
    val isAck: Boolean get() = isTcp && (tcpFlags and TcpFlags.ACK != 0)
    val isFin: Boolean get() = isTcp && (tcpFlags and TcpFlags.FIN != 0)
    val isRst: Boolean get() = isTcp && (tcpFlags and TcpFlags.RST != 0)
}

object TcpFlags {
    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10
    const val URG = 0x20
}
