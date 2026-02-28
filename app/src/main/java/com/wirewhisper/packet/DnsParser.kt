package com.wirewhisper.packet

import java.net.InetAddress
import java.nio.ByteBuffer

data class DnsQuery(
    val transactionId: Int,
    val queryName: String,
    val queryType: Int,
)

data class DnsAnswer(
    val name: String,
    val type: Int,
    val address: InetAddress?,
    val cname: String?,
    val ttl: Long,
)

data class DnsResponse(
    val transactionId: Int,
    val queryName: String,
    val answers: List<DnsAnswer>,
)

object DnsParser {

    private const val MAX_LABEL_DEPTH = 16
    private const val TYPE_A = 1
    private const val TYPE_CNAME = 5
    private const val TYPE_AAAA = 28
    private const val POINTER_MASK = 0xC0

    fun parseQuery(payload: ByteBuffer): DnsQuery? {
        if (payload.remaining() < 12) return null
        val start = payload.position()
        return try {
            val txId = payload.short.toInt() and 0xFFFF
            val flags = payload.short.toInt() and 0xFFFF
            // QR bit = 0 means query
            if (flags and 0x8000 != 0) return null
            val qdCount = payload.short.toInt() and 0xFFFF
            payload.short // anCount
            payload.short // nsCount
            payload.short // arCount
            if (qdCount < 1) return null
            val qname = readName(payload, start) ?: return null
            if (payload.remaining() < 4) return null
            val qtype = payload.short.toInt() and 0xFFFF
            payload.short // qclass
            DnsQuery(txId, qname, qtype)
        } catch (_: Exception) {
            null
        }
    }

    fun parseResponse(payload: ByteBuffer): DnsResponse? {
        if (payload.remaining() < 12) return null
        val start = payload.position()
        return try {
            val txId = payload.short.toInt() and 0xFFFF
            val flags = payload.short.toInt() and 0xFFFF
            // QR bit = 1 means response
            if (flags and 0x8000 == 0) return null
            val qdCount = payload.short.toInt() and 0xFFFF
            val anCount = payload.short.toInt() and 0xFFFF
            payload.short // nsCount
            payload.short // arCount

            // Skip question section
            var queryName = ""
            for (i in 0 until qdCount) {
                val name = readName(payload, start) ?: return null
                if (i == 0) queryName = name
                if (payload.remaining() < 4) return null
                payload.short // qtype
                payload.short // qclass
            }

            // Parse answer section
            val answers = mutableListOf<DnsAnswer>()
            for (i in 0 until anCount) {
                val answer = parseResourceRecord(payload, start) ?: break
                answers.add(answer)
            }

            if (answers.isEmpty()) return null
            DnsResponse(txId, queryName, answers)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseResourceRecord(buf: ByteBuffer, messageStart: Int): DnsAnswer? {
        val name = readName(buf, messageStart) ?: return null
        if (buf.remaining() < 10) return null
        val type = buf.short.toInt() and 0xFFFF
        buf.short // class
        val ttl = buf.int.toLong() and 0xFFFFFFFFL
        val rdLength = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < rdLength) return null

        return when (type) {
            TYPE_A -> {
                if (rdLength != 4) { buf.position(buf.position() + rdLength); return null }
                val addr = ByteArray(4)
                buf.get(addr)
                DnsAnswer(name, type, InetAddress.getByAddress(addr), null, ttl)
            }
            TYPE_AAAA -> {
                if (rdLength != 16) { buf.position(buf.position() + rdLength); return null }
                val addr = ByteArray(16)
                buf.get(addr)
                DnsAnswer(name, type, InetAddress.getByAddress(addr), null, ttl)
            }
            TYPE_CNAME -> {
                val posBeforeCname = buf.position()
                val cname = readName(buf, messageStart)
                // Ensure we advance past the full rdLength
                buf.position(posBeforeCname + rdLength)
                DnsAnswer(name, type, null, cname, ttl)
            }
            else -> {
                buf.position(buf.position() + rdLength)
                null
            }
        }
    }

    /**
     * Reads a DNS domain name with label compression support (RFC 1035 4.1.4).
     * Pointer labels (0xC0 prefix) jump to an earlier offset in the message.
     */
    private fun readName(buf: ByteBuffer, messageStart: Int, depth: Int = 0): String? {
        if (depth > MAX_LABEL_DEPTH) return null
        val parts = mutableListOf<String>()
        var jumped = false
        var savedPosition = -1

        while (buf.remaining() > 0) {
            val len = buf.get().toInt() and 0xFF
            when {
                len == 0 -> {
                    if (jumped) buf.position(savedPosition)
                    return parts.joinToString(".")
                }
                len and POINTER_MASK == POINTER_MASK -> {
                    if (buf.remaining() < 1) return null
                    val offset = ((len and 0x3F) shl 8) or (buf.get().toInt() and 0xFF)
                    if (!jumped) savedPosition = buf.position()
                    jumped = true
                    val absoluteOffset = messageStart + offset
                    if (absoluteOffset < messageStart || absoluteOffset >= buf.limit()) return null
                    buf.position(absoluteOffset)
                    // Recursive through the loop with depth guard
                    if (depth + 1 > MAX_LABEL_DEPTH) return null
                }
                len > 63 -> return null // invalid label length
                else -> {
                    if (buf.remaining() < len) return null
                    val label = ByteArray(len)
                    buf.get(label)
                    parts.add(String(label, Charsets.US_ASCII))
                }
            }
        }
        return null
    }
}
