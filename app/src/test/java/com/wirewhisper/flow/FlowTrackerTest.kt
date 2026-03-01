package com.wirewhisper.flow

import com.wirewhisper.core.model.FlowKey
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.core.model.PacketInfo
import com.wirewhisper.core.model.Protocol
import com.wirewhisper.data.repository.InMemoryFlowRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class FlowTrackerTest {

    private lateinit var tracker: FlowTracker

    private val srcAddr = InetAddress.getByName("192.168.1.100")
    private val dstAddr = InetAddress.getByName("93.184.216.34")

    private fun makePacketInfo(
        srcAddress: InetAddress = srcAddr,
        srcPort: Int = 54321,
        dstAddress: InetAddress = dstAddr,
        dstPort: Int = 443,
        protocol: Protocol = Protocol.TCP,
        totalLength: Int = 100,
        tcpFlags: Int = 0x10, // ACK
    ) = PacketInfo(
        srcAddress = srcAddress,
        srcPort = srcPort,
        dstAddress = dstAddress,
        dstPort = dstPort,
        protocol = protocol,
        ipVersion = 4,
        ipHeaderLength = 20,
        transportHeaderLength = 20,
        payloadLength = totalLength - 40,
        totalLength = totalLength,
        tcpFlags = tcpFlags,
    )

    @Before
    fun setUp() {
        tracker = FlowTracker()
    }

    @Test
    fun `onPacket outgoing creates new record with bytesSent`() {
        tracker.onPacket(makePacketInfo(totalLength = 200), outgoing = true)
        val flows = tracker.activeFlows.value
        assertEquals(1, flows.size)
        assertEquals(200L, flows[0].bytesSent)
        assertEquals(0L, flows[0].bytesReceived)
        assertEquals(1, flows[0].packetsSent)
        assertEquals(0, flows[0].packetsReceived)
    }

    @Test
    fun `onPacket incoming creates record with bytesReceived`() {
        tracker.onPacket(makePacketInfo(totalLength = 150), outgoing = false)
        val flows = tracker.activeFlows.value
        assertEquals(1, flows.size)
        assertEquals(0L, flows[0].bytesSent)
        assertEquals(150L, flows[0].bytesReceived)
        assertEquals(0, flows[0].packetsSent)
        assertEquals(1, flows[0].packetsReceived)
    }

    @Test
    fun `onPacket same key accumulates bytes`() {
        val info = makePacketInfo(totalLength = 100)
        tracker.onPacket(info, outgoing = true)
        tracker.onPacket(info, outgoing = true)
        tracker.onPacket(info, outgoing = true)
        val flows = tracker.activeFlows.value
        assertEquals(1, flows.size)
        assertEquals(300L, flows[0].bytesSent)
        assertEquals(3, flows[0].packetsSent)
    }

    @Test
    fun `onPacket different keys create separate records`() {
        tracker.onPacket(makePacketInfo(dstPort = 443), outgoing = true)
        tracker.onPacket(makePacketInfo(dstPort = 80), outgoing = true)
        val flows = tracker.activeFlows.value
        assertEquals(2, flows.size)
    }

    @Test
    fun `onPacket incoming uses reverseFlowKey`() {
        // Outgoing: src=192.168.1.100:54321, dst=93.184.216.34:443
        tracker.onPacket(makePacketInfo(), outgoing = true)
        // Incoming response: src=93.184.216.34:443, dst=192.168.1.100:54321
        // reverseFlowKey will map this back to the same flow
        val responseInfo = makePacketInfo(
            srcAddress = dstAddr,
            srcPort = 443,
            dstAddress = srcAddr,
            dstPort = 54321,
            totalLength = 500,
        )
        tracker.onPacket(responseInfo, outgoing = false)
        val flows = tracker.activeFlows.value
        assertEquals(1, flows.size)
        assertEquals(100L, flows[0].bytesSent)
        assertEquals(500L, flows[0].bytesReceived)
    }

    @Test
    fun `enrichFlow updates uid and app info`() {
        tracker.onPacket(makePacketInfo(), outgoing = true)
        val key = tracker.activeFlows.value[0].key
        tracker.enrichFlow(key, uid = 10042, packageName = "com.example.app", appName = "Example")
        val flow = tracker.activeFlows.value.find { it.key == key }
        // enrichFlow uses computeIfPresent; check after next snapshot
        // Force snapshot via another packet
        tracker.onPacket(makePacketInfo(dstPort = 9999), outgoing = true)
        val enriched = tracker.activeFlows.value.find { it.key == key }!!
        assertEquals(10042, enriched.uid)
        assertEquals("com.example.app", enriched.packageName)
        assertEquals("Example", enriched.appName)
    }

    @Test
    fun `enrichFlow preserves byte counts`() {
        tracker.onPacket(makePacketInfo(totalLength = 500), outgoing = true)
        tracker.onPacket(makePacketInfo(totalLength = 200), outgoing = true)
        val key = tracker.activeFlows.value[0].key
        tracker.enrichFlow(key, uid = 100, packageName = "pkg", appName = "App")
        // Force snapshot
        tracker.onPacket(makePacketInfo(dstPort = 9999), outgoing = true)
        val enriched = tracker.activeFlows.value.find { it.key == key }!!
        assertEquals(700L, enriched.bytesSent)
        assertEquals(2, enriched.packetsSent)
    }

    @Test
    fun `enrichFlowGeo sets country and hostname`() {
        tracker.onPacket(makePacketInfo(), outgoing = true)
        val key = tracker.activeFlows.value[0].key
        tracker.enrichFlowGeo(key, country = "US", hostname = "example.com")
        // Force snapshot
        tracker.onPacket(makePacketInfo(dstPort = 9999), outgoing = true)
        val flow = tracker.activeFlows.value.find { it.key == key }!!
        assertEquals("US", flow.country)
        assertEquals("example.com", flow.dnsHostname)
    }

    @Test
    fun `enrichFlowsByAddress sets hostname on matching flows`() {
        tracker.onPacket(makePacketInfo(), outgoing = true)
        tracker.enrichFlowsByAddress(dstAddr, "example.com")
        // Force snapshot
        tracker.onPacket(makePacketInfo(dstPort = 9999), outgoing = true)
        val flow = tracker.activeFlows.value.find { it.key.dstAddress == dstAddr }!!
        assertEquals("example.com", flow.dnsHostname)
    }

    @Test
    fun `enrichFlowsByAddress skips flows with existing hostname`() {
        tracker.onPacket(makePacketInfo(), outgoing = true)
        val key = tracker.activeFlows.value[0].key
        tracker.enrichFlowGeo(key, country = null, hostname = "original.com")
        tracker.enrichFlowsByAddress(dstAddr, "new.com")
        // Force snapshot
        tracker.onPacket(makePacketInfo(dstPort = 9999), outgoing = true)
        val flow = tracker.activeFlows.value.find { it.key == key }!!
        assertEquals("original.com", flow.dnsHostname)
    }

    @Test
    fun `flushTimedOut keeps recent flows`() {
        val repo = InMemoryFlowRepository()
        tracker.repository = repo
        tracker.onPacket(makePacketInfo(), outgoing = true)
        tracker.flushTimedOut()
        // Recent flow should still be active
        assertEquals(1, tracker.activeFlows.value.size)
    }

    @Test
    fun `flushTimedOut removes old flows and persists to repo`() {
        val repo = InMemoryFlowRepository()
        tracker.repository = repo
        tracker.onPacket(makePacketInfo(), outgoing = true)
        // Artificially age the flow
        val flow = tracker.activeFlows.value[0]
        flow.lastSeen = System.currentTimeMillis() - FlowRecord.IDLE_TIMEOUT_MS - 1000
        tracker.flushTimedOut()
        assertEquals(0, tracker.activeFlows.value.size)
    }

    @Test
    fun `flushAll persists all flows and clears`() {
        val repo = InMemoryFlowRepository()
        tracker.repository = repo
        tracker.onPacket(makePacketInfo(dstPort = 443), outgoing = true)
        tracker.onPacket(makePacketInfo(dstPort = 80), outgoing = true)
        assertEquals(2, tracker.activeFlows.value.size)
        tracker.flushAll()
        assertEquals(0, tracker.activeFlows.value.size)
    }

    @Test
    fun `flushAll with no repository is no-op`() {
        tracker.repository = null
        tracker.onPacket(makePacketInfo(), outgoing = true)
        tracker.flushAll() // should not crash
        // Flows remain since no repo to flush to
    }

    @Test
    fun `recordTrafficSample records traffic via sampler`() {
        val sampler = TrafficSampler()
        tracker.trafficSampler = sampler
        val info = makePacketInfo(totalLength = 500)
        tracker.onPacket(info, outgoing = true)
        tracker.recordTrafficSample(info, outgoing = true)
        // Sampler records uid=-1 (UNKNOWN_UID) with 500 bytes
        val samples = sampler.getAppSamples(-1)
        assertEquals(TrafficSampler.WINDOW_SECONDS, samples.size)
        assertTrue(samples.last() >= 500L)
    }

    @Test
    fun `recordTrafficSample records blocked traffic`() {
        val sampler = TrafficSampler()
        tracker.trafficSampler = sampler
        val info = makePacketInfo(totalLength = 300)
        tracker.onPacket(info, outgoing = true)
        tracker.recordTrafficSample(info, outgoing = true, blocked = true)
        val samples = sampler.getAppDirectionalSamples(-1)
        assertEquals(TrafficSampler.WINDOW_SECONDS, samples.size)
        assertTrue(samples.last().blockedSent >= 300L)
        assertEquals(0L, samples.last().sent)
    }
}
