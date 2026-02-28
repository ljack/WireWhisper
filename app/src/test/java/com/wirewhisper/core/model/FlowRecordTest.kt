package com.wirewhisper.core.model

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class FlowRecordTest {

    private fun makeKey(
        dstAddress: String = "93.184.216.34",
        dstPort: Int = 443,
        protocol: Protocol = Protocol.TCP,
    ) = FlowKey(
        srcAddress = InetAddress.getByName("192.168.1.100"),
        srcPort = 54321,
        dstAddress = InetAddress.getByName(dstAddress),
        dstPort = dstPort,
        protocol = protocol,
    )

    @Test
    fun `default UID is UNKNOWN_UID`() {
        val record = FlowRecord(key = makeKey())
        assertEquals(FlowRecord.UNKNOWN_UID, record.uid)
        assertEquals(-1, record.uid)
    }

    @Test
    fun `dstAddress delegates to key`() {
        val record = FlowRecord(key = makeKey(dstAddress = "10.0.0.1"))
        assertEquals(InetAddress.getByName("10.0.0.1"), record.dstAddress)
    }

    @Test
    fun `dstPort delegates to key`() {
        val record = FlowRecord(key = makeKey(dstPort = 8080))
        assertEquals(8080, record.dstPort)
    }

    @Test
    fun `protocol delegates to key`() {
        val record = FlowRecord(key = makeKey(protocol = Protocol.UDP))
        assertEquals(Protocol.UDP, record.protocol)
    }

    @Test
    fun `isActive returns true for recently created record`() {
        val record = FlowRecord(key = makeKey())
        assertTrue(record.isActive)
    }

    @Test
    fun `isActive returns false when lastSeen is old`() {
        val record = FlowRecord(
            key = makeKey(),
            lastSeen = System.currentTimeMillis() - FlowRecord.IDLE_TIMEOUT_MS - 1000,
        )
        assertFalse(record.isActive)
    }

    @Test
    fun `isActive boundary - exactly at timeout`() {
        val record = FlowRecord(
            key = makeKey(),
            lastSeen = System.currentTimeMillis() - FlowRecord.IDLE_TIMEOUT_MS,
        )
        // At exactly the timeout boundary, (now - lastSeen) == IDLE_TIMEOUT_MS
        // which is NOT < IDLE_TIMEOUT_MS, so should be false
        assertFalse(record.isActive)
    }

    @Test
    fun `default byte and packet counts are zero`() {
        val record = FlowRecord(key = makeKey())
        assertEquals(0L, record.bytesSent)
        assertEquals(0L, record.bytesReceived)
        assertEquals(0, record.packetsSent)
        assertEquals(0, record.packetsReceived)
    }

    @Test
    fun `mutable fields can be updated`() {
        val record = FlowRecord(key = makeKey())
        record.bytesSent = 1000L
        record.bytesReceived = 500L
        record.packetsSent = 10
        record.packetsReceived = 5
        record.dnsHostname = "example.com"
        record.country = "US"

        assertEquals(1000L, record.bytesSent)
        assertEquals(500L, record.bytesReceived)
        assertEquals(10, record.packetsSent)
        assertEquals(5, record.packetsReceived)
        assertEquals("example.com", record.dnsHostname)
        assertEquals("US", record.country)
    }

    @Test
    fun `IDLE_TIMEOUT_MS is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, FlowRecord.IDLE_TIMEOUT_MS)
    }
}
