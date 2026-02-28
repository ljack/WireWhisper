package com.wirewhisper.packet

import java.nio.ByteBuffer

/**
 * UDP header parser.
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Source Port          |       Destination Port        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            Length             |           Checksum            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 */
data class UdpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val length: Int,
) {
    companion object {
        const val HEADER_LENGTH = 8

        fun parse(buffer: ByteBuffer, offset: Int): UdpHeader? {
            if (buffer.limit() - offset < HEADER_LENGTH) return null

            val srcPort = buffer.getShort(offset).toInt() and 0xFFFF
            val dstPort = buffer.getShort(offset + 2).toInt() and 0xFFFF
            val length = buffer.getShort(offset + 4).toInt() and 0xFFFF

            return UdpHeader(
                srcPort = srcPort,
                dstPort = dstPort,
                length = length,
            )
        }
    }
}
