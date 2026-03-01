package com.wirewhisper.flow

import java.util.concurrent.ConcurrentHashMap

data class TrafficSample(
    val sent: Long,
    val received: Long,
    val blockedSent: Long = 0,
    val blockedReceived: Long = 0,
) {
    val total: Long get() = sent + received + blockedSent + blockedReceived
    val allowedTotal: Long get() = sent + received
    val blockedTotal: Long get() = blockedSent + blockedReceived
}

/**
 * Tracks per-app per-second byte counts in a circular buffer for sparkline rendering.
 * Each UID gets a [TrafficRingBuffer] with [WINDOW_SECONDS] slots.
 * Supports bidirectional traffic split (sent vs received).
 */
class TrafficSampler {

    companion object {
        const val WINDOW_SECONDS = 60
    }

    private val buffers = ConcurrentHashMap<Int, TrafficRingBuffer>()

    fun recordTraffic(uid: Int, bytes: Int, outgoing: Boolean, blocked: Boolean = false) {
        if (bytes <= 0) return
        val buffer = buffers.getOrPut(uid) { TrafficRingBuffer(WINDOW_SECONDS) }
        buffer.add(bytes.toLong(), outgoing, blocked)
    }

    fun clearAll() {
        buffers.clear()
    }

    fun getAppSamples(uid: Int): List<Long> {
        return buffers[uid]?.totalSnapshot() ?: List(WINDOW_SECONDS) { 0L }
    }

    fun getAppDirectionalSamples(uid: Int): List<TrafficSample> {
        return buffers[uid]?.snapshot() ?: List(WINDOW_SECONDS) { TrafficSample(0L, 0L) }
    }
}

/**
 * Fixed-size ring buffer that accumulates byte counts per second.
 * Values landing in the same second are summed. Old slots are zeroed on advance.
 * Tracks sent and received bytes separately.
 */
class TrafficRingBuffer(private val size: Int) {
    private val sentData = LongArray(size)
    private val recvData = LongArray(size)
    private val blockedSentData = LongArray(size)
    private val blockedRecvData = LongArray(size)
    private var headSecond = 0L
    private var headIndex = 0

    @Synchronized
    fun add(bytes: Long, outgoing: Boolean, blocked: Boolean = false) {
        val nowSecond = System.currentTimeMillis() / 1000
        if (headSecond == 0L) {
            headSecond = nowSecond
            headIndex = 0
        }
        advance(nowSecond)
        if (blocked) {
            if (outgoing) blockedSentData[headIndex] += bytes
            else blockedRecvData[headIndex] += bytes
        } else {
            if (outgoing) sentData[headIndex] += bytes
            else recvData[headIndex] += bytes
        }
    }

    @Synchronized
    fun snapshot(): List<TrafficSample> {
        val nowSecond = System.currentTimeMillis() / 1000
        if (headSecond == 0L) return List(size) { TrafficSample(0L, 0L) }
        advance(nowSecond)

        val result = ArrayList<TrafficSample>(size)
        for (i in 0 until size) {
            val idx = (headIndex - size + 1 + i + size * 2) % size
            result.add(TrafficSample(sentData[idx], recvData[idx], blockedSentData[idx], blockedRecvData[idx]))
        }
        return result
    }

    fun totalSnapshot(): List<Long> {
        return snapshot().map { it.total }
    }

    private fun advance(nowSecond: Long) {
        val gap = nowSecond - headSecond
        if (gap <= 0) return
        val steps = gap.coerceAtMost(size.toLong()).toInt()
        for (i in 1..steps) {
            headIndex = (headIndex + 1) % size
            sentData[headIndex] = 0
            recvData[headIndex] = 0
            blockedSentData[headIndex] = 0
            blockedRecvData[headIndex] = 0
        }
        headSecond = nowSecond
    }
}
