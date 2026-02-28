package com.wirewhisper.flow

import org.junit.Assert.*
import org.junit.Test

class TrafficRingBufferTest {

    @Test
    fun `single add appears in snapshot`() {
        val buffer = TrafficRingBuffer(30)
        buffer.add(1500L)
        val snapshot = buffer.snapshot()
        assertEquals(30, snapshot.size)
        assertTrue("Should contain the added value", snapshot.any { it >= 1500L })
    }

    @Test
    fun `multiple adds in same second are summed`() {
        val buffer = TrafficRingBuffer(30)
        buffer.add(100L)
        buffer.add(200L)
        buffer.add(300L)
        val snapshot = buffer.snapshot()
        // All adds happen in the same millisecond, so same second
        assertTrue("Values should be summed", snapshot.last() >= 600L)
    }

    @Test
    fun `snapshot returns exactly size elements`() {
        val buffer = TrafficRingBuffer(10)
        assertEquals(10, buffer.snapshot().size)

        buffer.add(42L)
        assertEquals(10, buffer.snapshot().size)
    }

    @Test
    fun `initial state is all zeros`() {
        val buffer = TrafficRingBuffer(30)
        val snapshot = buffer.snapshot()
        assertEquals(30, snapshot.size)
        assertTrue(snapshot.all { it == 0L })
    }

    @Test
    fun `small buffer works correctly`() {
        val buffer = TrafficRingBuffer(3)
        buffer.add(42L)
        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertTrue(snapshot.any { it == 42L })
    }

    @Test
    fun `latest value appears at end of snapshot`() {
        val buffer = TrafficRingBuffer(5)
        buffer.add(999L)
        val snapshot = buffer.snapshot()
        assertEquals(999L, snapshot.last())
    }

    @Test
    fun `buffer size 1 works`() {
        val buffer = TrafficRingBuffer(1)
        buffer.add(100L)
        val snapshot = buffer.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(100L, snapshot[0])
    }

    @Test
    fun `large add value`() {
        val buffer = TrafficRingBuffer(5)
        buffer.add(Long.MAX_VALUE / 2)
        val snapshot = buffer.snapshot()
        assertEquals(Long.MAX_VALUE / 2, snapshot.last())
    }
}
