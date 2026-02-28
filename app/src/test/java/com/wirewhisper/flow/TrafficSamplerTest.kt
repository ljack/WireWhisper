package com.wirewhisper.flow

import org.junit.Assert.*
import org.junit.Test

class TrafficSamplerTest {

    @Test
    fun `recordTraffic then getAppSamples returns non-zero in latest slot`() {
        val sampler = TrafficSampler()
        sampler.recordTraffic(uid = 10042, bytes = 1500)
        val samples = sampler.getAppSamples(10042)
        assertEquals(TrafficSampler.WINDOW_SECONDS, samples.size)
        assertTrue("Latest slot should have recorded bytes", samples.last() >= 1500L)
    }

    @Test
    fun `recordTraffic with zero bytes is no-op`() {
        val sampler = TrafficSampler()
        sampler.recordTraffic(uid = 1, bytes = 0)
        val samples = sampler.getAppSamples(1)
        assertEquals(TrafficSampler.WINDOW_SECONDS, samples.size)
        assertTrue("All should be zero", samples.all { it == 0L })
    }

    @Test
    fun `recordTraffic with negative bytes is no-op`() {
        val sampler = TrafficSampler()
        sampler.recordTraffic(uid = 1, bytes = -100)
        val samples = sampler.getAppSamples(1)
        assertTrue(samples.all { it == 0L })
    }

    @Test
    fun `getAppSamples for unknown UID returns 30 zeros`() {
        val sampler = TrafficSampler()
        val samples = sampler.getAppSamples(99999)
        assertEquals(TrafficSampler.WINDOW_SECONDS, samples.size)
        assertTrue(samples.all { it == 0L })
    }

    @Test
    fun `multiple UIDs tracked independently`() {
        val sampler = TrafficSampler()
        sampler.recordTraffic(uid = 1, bytes = 100)
        sampler.recordTraffic(uid = 2, bytes = 200)
        sampler.recordTraffic(uid = 3, bytes = 300)

        assertTrue(sampler.getAppSamples(1).last() >= 100L)
        assertTrue(sampler.getAppSamples(2).last() >= 200L)
        assertTrue(sampler.getAppSamples(3).last() >= 300L)
    }

    @Test
    fun `getAppSamples returns exactly WINDOW_SECONDS elements`() {
        val sampler = TrafficSampler()
        sampler.recordTraffic(uid = 1, bytes = 50)
        assertEquals(30, sampler.getAppSamples(1).size)
    }

    @Test
    fun `multiple recordTraffic calls accumulate in same second`() {
        val sampler = TrafficSampler()
        sampler.recordTraffic(uid = 1, bytes = 100)
        sampler.recordTraffic(uid = 1, bytes = 200)
        sampler.recordTraffic(uid = 1, bytes = 300)
        val samples = sampler.getAppSamples(1)
        assertTrue("Accumulated bytes should be >= 600", samples.last() >= 600L)
    }

    @Test
    fun `WINDOW_SECONDS is 30`() {
        assertEquals(30, TrafficSampler.WINDOW_SECONDS)
    }
}
