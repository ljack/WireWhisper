package com.wirewhisper.flow

import org.junit.Assert.*
import org.junit.Test

class TrafficRingBufferTest {

    @Test
    fun `single add appears in snapshot`() {
        val buffer = TrafficRingBuffer(60)
        buffer.add(1500L, outgoing = true)
        val snapshot = buffer.snapshot()
        assertEquals(60, snapshot.size)
        assertTrue("Should contain the added value", snapshot.any { it.sent >= 1500L })
    }

    @Test
    fun `multiple adds in same second are summed`() {
        val buffer = TrafficRingBuffer(60)
        buffer.add(100L, outgoing = true)
        buffer.add(200L, outgoing = true)
        buffer.add(300L, outgoing = true)
        val snapshot = buffer.snapshot()
        assertTrue("Values should be summed", snapshot.last().sent >= 600L)
    }

    @Test
    fun `snapshot returns exactly size elements`() {
        val buffer = TrafficRingBuffer(10)
        assertEquals(10, buffer.snapshot().size)

        buffer.add(42L, outgoing = true)
        assertEquals(10, buffer.snapshot().size)
    }

    @Test
    fun `initial state is all zeros`() {
        val buffer = TrafficRingBuffer(60)
        val snapshot = buffer.snapshot()
        assertEquals(60, snapshot.size)
        assertTrue(snapshot.all { it.sent == 0L && it.received == 0L })
    }

    @Test
    fun `small buffer works correctly`() {
        val buffer = TrafficRingBuffer(3)
        buffer.add(42L, outgoing = true)
        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertTrue(snapshot.any { it.sent == 42L })
    }

    @Test
    fun `latest value appears at end of snapshot`() {
        val buffer = TrafficRingBuffer(5)
        buffer.add(999L, outgoing = true)
        val snapshot = buffer.snapshot()
        assertEquals(999L, snapshot.last().sent)
    }

    @Test
    fun `buffer size 1 works`() {
        val buffer = TrafficRingBuffer(1)
        buffer.add(100L, outgoing = true)
        val snapshot = buffer.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(100L, snapshot[0].sent)
    }

    @Test
    fun `large add value`() {
        val buffer = TrafficRingBuffer(5)
        buffer.add(Long.MAX_VALUE / 2, outgoing = true)
        val snapshot = buffer.snapshot()
        assertEquals(Long.MAX_VALUE / 2, snapshot.last().sent)
    }

    @Test
    fun `bidirectional split tracks sent and received separately`() {
        val buffer = TrafficRingBuffer(5)
        buffer.add(100L, outgoing = true)
        buffer.add(200L, outgoing = false)
        val snapshot = buffer.snapshot()
        assertEquals(100L, snapshot.last().sent)
        assertEquals(200L, snapshot.last().received)
        assertEquals(300L, snapshot.last().total)
    }

    @Test
    fun `totalSnapshot returns sum of sent and received`() {
        val buffer = TrafficRingBuffer(5)
        buffer.add(100L, outgoing = true)
        buffer.add(200L, outgoing = false)
        val total = buffer.totalSnapshot()
        assertEquals(300L, total.last())
    }
}
