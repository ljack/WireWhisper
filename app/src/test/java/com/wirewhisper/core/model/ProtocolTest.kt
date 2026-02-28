package com.wirewhisper.core.model

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {

    @Test
    fun `fromNumber 6 returns TCP`() {
        assertEquals(Protocol.TCP, Protocol.fromNumber(6))
    }

    @Test
    fun `fromNumber 17 returns UDP`() {
        assertEquals(Protocol.UDP, Protocol.fromNumber(17))
    }

    @Test
    fun `fromNumber 1 returns ICMP`() {
        assertEquals(Protocol.ICMP, Protocol.fromNumber(1))
    }

    @Test
    fun `fromNumber 58 returns ICMPV6`() {
        assertEquals(Protocol.ICMPV6, Protocol.fromNumber(58))
    }

    @Test
    fun `fromNumber unknown returns OTHER`() {
        assertEquals(Protocol.OTHER, Protocol.fromNumber(999))
    }

    @Test
    fun `fromNumber 0 returns OTHER`() {
        assertEquals(Protocol.OTHER, Protocol.fromNumber(0))
    }

    @Test
    fun `TCP has number 6`() {
        assertEquals(6, Protocol.TCP.number)
    }

    @Test
    fun `UDP has number 17`() {
        assertEquals(17, Protocol.UDP.number)
    }

    @Test
    fun `ICMP has number 1`() {
        assertEquals(1, Protocol.ICMP.number)
    }

    @Test
    fun `ICMPV6 has number 58`() {
        assertEquals(58, Protocol.ICMPV6.number)
    }

    @Test
    fun `OTHER has number -1`() {
        assertEquals(-1, Protocol.OTHER.number)
    }
}
