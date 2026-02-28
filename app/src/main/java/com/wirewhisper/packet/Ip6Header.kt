package com.wirewhisper.packet

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * IPv6 header parser. Fixed 40-byte header, followed by optional extension headers.
 * Layout:
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Version| Traffic Class |           Flow Label                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Payload Length        |  Next Header  |   Hop Limit   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         Source Address                        |
 * |                         (128 bits)                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Destination Address                      |
 * |                         (128 bits)                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 */
data class Ip6Header(
    val payloadLength: Int,
    val nextHeader: Int,         // transport protocol (after skipping extensions)
    val headerLength: Int,       // 40 + extension headers length
    val srcAddress: InetAddress,
    val dstAddress: InetAddress,
) {
    companion object {
        const val FIXED_HEADER_LENGTH = 40

        // Extension header "Next Header" values that are NOT transport protocols
        private val EXTENSION_HEADERS = setOf(
            0,   // Hop-by-Hop Options
            43,  // Routing
            44,  // Fragment
            50,  // ESP (skip but don't parse payload)
            51,  // AH
            60,  // Destination Options
            135, // Mobility
            139, // HIP
            140, // Shim6
        )

        fun parse(buffer: ByteBuffer): Ip6Header? {
            if (buffer.remaining() < FIXED_HEADER_LENGTH) return null

            val start = buffer.position()
            val payloadLength = buffer.getShort(start + 4).toInt() and 0xFFFF
            var nextHeader = buffer.get(start + 6).toInt() and 0xFF

            val srcBytes = ByteArray(16)
            val dstBytes = ByteArray(16)
            buffer.position(start + 8)
            buffer.get(srcBytes)
            buffer.get(dstBytes)

            // Walk extension headers to find the transport protocol
            var offset = FIXED_HEADER_LENGTH
            while (nextHeader in EXTENSION_HEADERS) {
                if (start + offset + 2 > buffer.limit()) break
                val extNextHeader = buffer.get(start + offset).toInt() and 0xFF
                val extLength = ((buffer.get(start + offset + 1).toInt() and 0xFF) + 1) * 8
                offset += extLength
                nextHeader = extNextHeader
            }

            buffer.position(start) // reset

            return Ip6Header(
                payloadLength = payloadLength,
                nextHeader = nextHeader,
                headerLength = offset,
                srcAddress = InetAddress.getByAddress(srcBytes),
                dstAddress = InetAddress.getByAddress(dstBytes),
            )
        }
    }
}
