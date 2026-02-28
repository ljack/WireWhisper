package com.wirewhisper.flow

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-app per-second byte counts in a circular buffer for sparkline rendering.
 * Each UID gets a [TrafficRingBuffer] with [WINDOW_SECONDS] slots.
 */
class TrafficSampler {

    companion object {
        const val WINDOW_SECONDS = 30
    }

    private val buffers = ConcurrentHashMap<Int, TrafficRingBuffer>()

    fun recordTraffic(uid: Int, bytes: Int) {
        if (bytes <= 0) return
        val buffer = buffers.getOrPut(uid) { TrafficRingBuffer(WINDOW_SECONDS) }
        buffer.add(bytes.toLong())
    }

    fun getAppSamples(uid: Int): List<Long> {
        return buffers[uid]?.snapshot() ?: List(WINDOW_SECONDS) { 0L }
    }
}

/**
 * Fixed-size ring buffer that accumulates byte counts per second.
 * Values landing in the same second are summed. Old slots are zeroed on advance.
 */
class TrafficRingBuffer(private val size: Int) {
    private val data = LongArray(size)
    private var headSecond = 0L
    private var headIndex = 0

    @Synchronized
    fun add(bytes: Long) {
        val nowSecond = System.currentTimeMillis() / 1000
        if (headSecond == 0L) {
            headSecond = nowSecond
            headIndex = 0
        }
        advance(nowSecond)
        data[headIndex] += bytes
    }

    @Synchronized
    fun snapshot(): List<Long> {
        val nowSecond = System.currentTimeMillis() / 1000
        if (headSecond == 0L) return List(size) { 0L }
        advance(nowSecond)

        val result = LongArray(size)
        for (i in 0 until size) {
            result[i] = data[(headIndex - size + 1 + i + size * 2) % size]
        }
        return result.toList()
    }

    private fun advance(nowSecond: Long) {
        val gap = nowSecond - headSecond
        if (gap <= 0) return
        val steps = gap.coerceAtMost(size.toLong()).toInt()
        for (i in 1..steps) {
            headIndex = (headIndex + 1) % size
            data[headIndex] = 0
        }
        headSecond = nowSecond
    }
}
