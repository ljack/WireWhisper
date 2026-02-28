package com.wirewhisper.packet

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * IPv4 header parser. Layout:
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Version|  IHL  |    DSCP/ECN   |         Total Length          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Identification        |Flags|      Fragment Offset    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Time to Live |    Protocol   |         Header Checksum       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       Source Address                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Destination Address                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 */
data class Ip4Header(
    val headerLength: Int,   // in bytes (IHL * 4)
    val totalLength: Int,
    val protocol: Int,       // 6=TCP, 17=UDP, 1=ICMP
    val srcAddress: InetAddress,
    val dstAddress: InetAddress,
) {
    companion object {
        const val MIN_HEADER_LENGTH = 20

        fun parse(buffer: ByteBuffer): Ip4Header? {
            if (buffer.remaining() < MIN_HEADER_LENGTH) return null

            val start = buffer.position()
            val versionIhl = buffer.get(start).toInt() and 0xFF
            val ihl = versionIhl and 0x0F
            val headerLength = ihl * 4
            if (buffer.remaining() < headerLength) return null

            val totalLength = buffer.getShort(start + 2).toInt() and 0xFFFF
            val protocol = buffer.get(start + 9).toInt() and 0xFF

            val srcBytes = ByteArray(4)
            val dstBytes = ByteArray(4)
            buffer.position(start + 12)
            buffer.get(srcBytes)
            buffer.get(dstBytes)
            buffer.position(start) // reset

            return Ip4Header(
                headerLength = headerLength,
                totalLength = totalLength,
                protocol = protocol,
                srcAddress = InetAddress.getByAddress(srcBytes),
                dstAddress = InetAddress.getByAddress(dstBytes),
            )
        }
    }
}
