package com.wirewhisper.packet

import java.nio.ByteBuffer

data class TlsSni(val hostname: String)

object TlsParser {

    private const val TLS_CONTENT_HANDSHAKE = 0x16
    private const val HANDSHAKE_CLIENT_HELLO = 0x01
    private const val SNI_EXTENSION_TYPE = 0x0000
    private const val SNI_HOST_NAME_TYPE = 0x00

    /**
     * Extracts the SNI hostname from a TLS ClientHello message.
     * Returns null if the payload is not a ClientHello or has no SNI extension.
     */
    fun extractSni(payload: ByteBuffer): TlsSni? {
        if (payload.remaining() < 5) return null
        return try {
            val contentType = payload.get().toInt() and 0xFF
            if (contentType != TLS_CONTENT_HANDSHAKE) return null

            // TLS version (2 bytes) — we accept any
            payload.short
            // Record length
            val recordLength = payload.short.toInt() and 0xFFFF
            if (payload.remaining() < recordLength) return null

            // Handshake header
            if (payload.remaining() < 4) return null
            val handshakeType = payload.get().toInt() and 0xFF
            if (handshakeType != HANDSHAKE_CLIENT_HELLO) return null
            // Handshake length (3 bytes)
            val hsLen = ((payload.get().toInt() and 0xFF) shl 16) or
                    ((payload.get().toInt() and 0xFF) shl 8) or
                    (payload.get().toInt() and 0xFF)
            if (payload.remaining() < hsLen) return null

            val hsEnd = payload.position() + hsLen

            // Client version (2 bytes)
            if (payload.remaining() < 2) return null
            payload.short

            // Random (32 bytes)
            if (payload.remaining() < 32) return null
            payload.position(payload.position() + 32)

            // Session ID
            if (payload.remaining() < 1) return null
            val sessionIdLen = payload.get().toInt() and 0xFF
            if (payload.remaining() < sessionIdLen) return null
            payload.position(payload.position() + sessionIdLen)

            // Cipher Suites
            if (payload.remaining() < 2) return null
            val cipherSuitesLen = payload.short.toInt() and 0xFFFF
            if (payload.remaining() < cipherSuitesLen) return null
            payload.position(payload.position() + cipherSuitesLen)

            // Compression Methods
            if (payload.remaining() < 1) return null
            val compLen = payload.get().toInt() and 0xFF
            if (payload.remaining() < compLen) return null
            payload.position(payload.position() + compLen)

            // Extensions
            if (payload.position() >= hsEnd) return null
            if (payload.remaining() < 2) return null
            val extensionsLen = payload.short.toInt() and 0xFFFF
            if (payload.remaining() < extensionsLen) return null

            val extensionsEnd = payload.position() + extensionsLen

            while (payload.position() + 4 <= extensionsEnd) {
                val extType = payload.short.toInt() and 0xFFFF
                val extLen = payload.short.toInt() and 0xFFFF
                if (payload.remaining() < extLen) return null

                if (extType == SNI_EXTENSION_TYPE) {
                    return parseSniExtension(payload, extLen)
                }
                payload.position(payload.position() + extLen)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSniExtension(buf: ByteBuffer, extLen: Int): TlsSni? {
        if (extLen < 2) return null
        val sniListLen = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < sniListLen) return null
        val listEnd = buf.position() + sniListLen

        while (buf.position() + 3 <= listEnd) {
            val nameType = buf.get().toInt() and 0xFF
            val nameLen = buf.short.toInt() and 0xFFFF
            if (buf.remaining() < nameLen) return null

            if (nameType == SNI_HOST_NAME_TYPE && nameLen > 0) {
                val nameBytes = ByteArray(nameLen)
                buf.get(nameBytes)
                return TlsSni(String(nameBytes, Charsets.US_ASCII))
            }
            buf.position(buf.position() + nameLen)
        }
        return null
    }
}
