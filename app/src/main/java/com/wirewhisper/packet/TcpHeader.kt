package com.wirewhisper.packet

import java.nio.ByteBuffer

/**
 * TCP header parser.
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Source Port          |       Destination Port        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Sequence Number                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Acknowledgment Number                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Data |       |C|E|U|A|P|R|S|F|                               |
 * | Offset| Rsrvd |W|C|R|C|S|S|Y|I|            Window             |
 * |       |       |R|E|G|K|H|T|N|N|                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Checksum            |         Urgent Pointer        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 */
data class TcpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val dataOffset: Int,   // header length in bytes
    val flags: Int,        // raw flags byte
    val windowSize: Int,
) {
    companion object {
        const val MIN_HEADER_LENGTH = 20

        fun parse(buffer: ByteBuffer, offset: Int): TcpHeader? {
            if (buffer.limit() - offset < MIN_HEADER_LENGTH) return null

            val srcPort = buffer.getShort(offset).toInt() and 0xFFFF
            val dstPort = buffer.getShort(offset + 2).toInt() and 0xFFFF
            val seqNum = buffer.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            val ackNum = buffer.getInt(offset + 8).toLong() and 0xFFFFFFFFL
            val dataOffsetAndFlags = buffer.getShort(offset + 12).toInt() and 0xFFFF
            val dataOffset = ((dataOffsetAndFlags shr 12) and 0x0F) * 4
            val flags = dataOffsetAndFlags and 0x3F
            val windowSize = buffer.getShort(offset + 14).toInt() and 0xFFFF

            return TcpHeader(
                srcPort = srcPort,
                dstPort = dstPort,
                sequenceNumber = seqNum,
                acknowledgmentNumber = ackNum,
                dataOffset = dataOffset,
                flags = flags,
                windowSize = windowSize,
            )
        }
    }
}
