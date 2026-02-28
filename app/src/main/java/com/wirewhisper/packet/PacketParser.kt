package com.wirewhisper.packet

import com.wirewhisper.core.model.PacketInfo
import com.wirewhisper.core.model.Protocol
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Parses raw IP packets from the TUN interface into [PacketInfo] metadata.
 * Handles both IPv4 and IPv6 with TCP/UDP transport headers.
 *
 * This is a zero-allocation-optimized parser: it reads directly from the
 * ByteBuffer without creating intermediate objects beyond the result.
 */
class PacketParser {

    fun parse(buffer: ByteBuffer): PacketInfo? {
        if (buffer.remaining() < 1) return null

        val firstByte = buffer.get(buffer.position()).toInt() and 0xFF
        val version = firstByte shr 4

        return when (version) {
            4 -> parseIp4(buffer)
            6 -> parseIp6(buffer)
            else -> null // Unknown IP version
        }
    }

    private fun parseIp4(buffer: ByteBuffer): PacketInfo? {
        val ip = Ip4Header.parse(buffer) ?: return null
        val protocol = Protocol.fromNumber(ip.protocol)

        return parseTransport(
            buffer = buffer,
            transportOffset = buffer.position() + ip.headerLength,
            protocol = protocol,
            ipVersion = 4,
            ipHeaderLength = ip.headerLength,
            totalLength = ip.totalLength,
            srcAddress = ip.srcAddress,
            dstAddress = ip.dstAddress,
        )
    }

    private fun parseIp6(buffer: ByteBuffer): PacketInfo? {
        val ip = Ip6Header.parse(buffer) ?: return null
        val protocol = Protocol.fromNumber(ip.nextHeader)

        return parseTransport(
            buffer = buffer,
            transportOffset = buffer.position() + ip.headerLength,
            protocol = protocol,
            ipVersion = 6,
            ipHeaderLength = ip.headerLength,
            totalLength = Ip6Header.FIXED_HEADER_LENGTH + ip.payloadLength,
            srcAddress = ip.srcAddress,
            dstAddress = ip.dstAddress,
        )
    }

    private fun parseTransport(
        buffer: ByteBuffer,
        transportOffset: Int,
        protocol: Protocol,
        ipVersion: Int,
        ipHeaderLength: Int,
        totalLength: Int,
        srcAddress: InetAddress,
        dstAddress: InetAddress,
    ): PacketInfo? {
        val srcPort: Int
        val dstPort: Int
        val transportHeaderLength: Int
        val tcpFlags: Int

        when (protocol) {
            Protocol.TCP -> {
                val tcp = TcpHeader.parse(buffer, transportOffset) ?: return null
                srcPort = tcp.srcPort
                dstPort = tcp.dstPort
                transportHeaderLength = tcp.dataOffset
                tcpFlags = tcp.flags
            }
            Protocol.UDP -> {
                val udp = UdpHeader.parse(buffer, transportOffset) ?: return null
                srcPort = udp.srcPort
                dstPort = udp.dstPort
                transportHeaderLength = UdpHeader.HEADER_LENGTH
                tcpFlags = 0
            }
            else -> {
                // ICMP or other: no port info
                srcPort = 0
                dstPort = 0
                transportHeaderLength = 0
                tcpFlags = 0
            }
        }

        val payloadLength = totalLength - ipHeaderLength - transportHeaderLength

        return PacketInfo(
            srcAddress = srcAddress,
            srcPort = srcPort,
            dstAddress = dstAddress,
            dstPort = dstPort,
            protocol = protocol,
            ipVersion = ipVersion,
            ipHeaderLength = ipHeaderLength,
            transportHeaderLength = transportHeaderLength,
            payloadLength = maxOf(0, payloadLength),
            totalLength = totalLength,
            tcpFlags = tcpFlags,
        )
    }
}
