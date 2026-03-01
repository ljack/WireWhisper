package com.wirewhisper.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wirewhisper.core.model.PacketInfo
import com.wirewhisper.core.model.Protocol
import com.wirewhisper.firewall.BlockingEngine
import com.wirewhisper.flow.FlowTracker
import com.wirewhisper.flow.HostnameResolver
import com.wirewhisper.flow.UidResolver
import com.wirewhisper.geo.GeoResolver
import com.wirewhisper.packet.PacketParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel as CoroutineChannel

/**
 * Reads raw IP packets from the TUN file descriptor, extracts metadata
 * for the monitoring pipeline, and relays packets to their actual destinations.
 *
 * Pipeline per packet:
 * 1. Read from TUN (outgoing device traffic)
 * 2. Parse IP + transport headers → [PacketInfo]
 * 3. Update [FlowTracker] with packet metadata
 * 4. Resolve UID via [UidResolver]
 * 5. Relay packet to actual destination via protected socket
 * 6. Receive response → write back to TUN (incoming traffic)
 *
 * ### Relay strategy
 * - **UDP**: Stateless relay via [DatagramChannel]. One channel per src-port,
 *   responses are read via a shared [Selector].
 * - **TCP**: Session-based proxy via [SocketChannel]. We handle the TCP state
 *   machine toward the local app while using a normal stream socket to the
 *   remote server. See [TcpSession] for state tracking.
 */
class TunProcessor(
    private val tunFd: ParcelFileDescriptor,
    private val vpnService: VpnService,
    private val flowTracker: FlowTracker,
    private val uidResolver: UidResolver,
    private val hostnameResolver: HostnameResolver,
    private val geoResolver: GeoResolver,
    private val blockingEngine: BlockingEngine,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "TunProcessor"
        private const val MAX_PACKET_SIZE = 32_767
        private const val UDP_IDLE_TIMEOUT_MS = 60_000L
        private const val SELECTOR_TIMEOUT_MS = 100L
        private const val TCP_CONNECT_TIMEOUT_MS = 5_000
    }

    private val parser = PacketParser()
    private val tunInput = FileInputStream(tunFd.fileDescriptor)
    private val tunOutput = FileOutputStream(tunFd.fileDescriptor)
    private val geoResolvingIps = ConcurrentHashMap.newKeySet<InetAddress>()

    // UDP relay: local srcPort → DatagramChannel
    private val udpChannels = ConcurrentHashMap<Int, UdpSession>()
    // TCP relay: FlowKey hash → TcpSession
    private val tcpSessions = ConcurrentHashMap<Long, TcpSession>()

    private var readJob: Job? = null
    private var udpSelectorJob: Job? = null
    private var cleanupJob: Job? = null

    @Volatile
    private var running = false

    fun start() {
        running = true
        readJob = scope.launch(Dispatchers.IO) { readLoop() }
        udpSelectorJob = scope.launch(Dispatchers.IO) { udpResponseLoop() }
        cleanupJob = scope.launch(Dispatchers.IO) { cleanupLoop() }
        Log.i(TAG, "TunProcessor started")
    }

    fun stop() {
        running = false
        readJob?.cancel()
        udpSelectorJob?.cancel()
        cleanupJob?.cancel()

        // Close all relay channels
        udpChannels.values.forEach { it.channel.close() }
        udpChannels.clear()
        tcpSessions.values.forEach { it.close() }
        tcpSessions.clear()

        try { tunInput.close() } catch (_: IOException) {}
        try { tunOutput.close() } catch (_: IOException) {}

        Log.i(TAG, "TunProcessor stopped")
    }

    // ── TUN read loop ──────────────────────────────────────────

    private var packetCount = 0L

    private suspend fun readLoop() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        Log.d(TAG, "Read loop entered, waiting for packets...")
        while (running && scope.isActive) {
            val length = try {
                tunInput.read(buffer)
            } catch (e: IOException) {
                if (running) Log.e(TAG, "TUN read error", e)
                break
            }
            if (length <= 0) continue

            packetCount++
            if (packetCount <= 5 || packetCount % 100 == 0L) {
                Log.d(TAG, "Packet #$packetCount: $length bytes, first byte=0x${String.format("%02X", buffer[0])}")
            }

            val packet = ByteBuffer.wrap(buffer, 0, length)
            processPacket(packet, length)
        }
        Log.d(TAG, "Read loop exited, total packets: $packetCount")
    }

    private fun processPacket(packet: ByteBuffer, length: Int) {
        val info = parser.parse(packet)
        if (info == null) {
            if (packetCount <= 5) {
                val firstByte = packet.get(packet.position()).toInt() and 0xFF
                Log.w(TAG, "Parse returned null for packet #$packetCount, version=${firstByte shr 4}, len=$length")
            }
            return
        }
        if (packetCount <= 5 || packetCount % 100 == 0L) {
            Log.d(TAG, "Flow: ${info.protocol} ${info.srcAddress.hostAddress}:${info.srcPort} → ${info.dstAddress.hostAddress}:${info.dstPort} ($length bytes)")
        }

        // 1. Track the flow
        flowTracker.onPacket(info, outgoing = true)

        // 2-3. Enrichment (best-effort, must not crash the relay)
        try {
            uidResolver.resolveAndEnrich(info, flowTracker)

            val cachedHostname = hostnameResolver.lookupHostname(info.dstAddress)
            if (cachedHostname != null) {
                flowTracker.enrichFlowGeo(info.flowKey, country = null, hostname = cachedHostname)
            }

            // Geo-resolve destination IP (fire-and-forget, deduped by IP)
            val dstAddr = info.dstAddress
            val flowKey = info.flowKey
            if (geoResolvingIps.add(dstAddr)) {
                scope.launch {
                    try {
                        val geo = geoResolver.resolve(dstAddr)
                        if (geo != null) {
                            flowTracker.enrichFlowGeo(flowKey, country = geo.countryCode, hostname = null)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Geo resolve failed for ${dstAddr.hostAddress}", e)
                    }
                }
            }
            if (info.protocol == Protocol.UDP && info.dstPort == 53) {
                val dnsPayloadOffset = info.ipHeaderLength + UdpHeader_LENGTH
                if (dnsPayloadOffset < length) {
                    val dnsBuf = ByteBuffer.wrap(packet.array(), dnsPayloadOffset, length - dnsPayloadOffset)
                    hostnameResolver.onDnsQuery(dnsBuf)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Enrichment error (non-fatal)", e)
        }

        // 4. Check if blocked & record traffic (after enrichment so UID is resolved)
        val flow = flowTracker.getFlow(info.flowKey)
        val country = flow?.country ?: geoResolver.resolveCountrySync(info.dstAddress)
        val isBlocked = blockingEngine.isBlocked(flow?.packageName, flow?.dnsHostname, country)
        flowTracker.recordTrafficSample(info, outgoing = true, blocked = isBlocked)
        if (isBlocked) {
            blockingEngine.notifyBlocked(flow?.packageName, flow?.dnsHostname, country)
            return  // drop packet
        }

        // 5. Relay to actual destination
        when (info.protocol) {
            Protocol.UDP -> relayUdp(info, packet, length)
            Protocol.TCP -> relayTcp(info, packet, length)
            else -> {} // Drop ICMP etc. for now – Phase 2
        }
    }

    // ── UDP relay ──────────────────────────────────────────────

    private fun relayUdp(info: PacketInfo, packet: ByteBuffer, packetLength: Int) {
        val session = udpChannels.getOrPut(info.srcPort) {
            val channel = DatagramChannel.open().also { ch ->
                ch.configureBlocking(false)
                vpnService.protect(ch.socket())
            }
            UdpSession(channel = channel, lastActive = System.currentTimeMillis())
        }

        session.lastActive = System.currentTimeMillis()
        // Remember the source info so we can construct the response back to TUN
        session.pendingSources[info.dstPort] = UdpSource(
            srcAddress = info.srcAddress,
            srcPort = info.srcPort,
            dstAddress = info.dstAddress,
            dstPort = info.dstPort,
            ipVersion = info.ipVersion,
        )

        // Extract UDP payload (after IP header + 8-byte UDP header)
        val payloadOffset = info.ipHeaderLength + UdpHeader_LENGTH
        if (payloadOffset >= packetLength) return
        val payload = ByteBuffer.wrap(
            packet.array(), payloadOffset, packetLength - payloadOffset
        )

        try {
            session.channel.send(
                payload,
                InetSocketAddress(info.dstAddress, info.dstPort)
            )
        } catch (e: IOException) {
            Log.w(TAG, "UDP send failed to ${info.dstAddress}:${info.dstPort}", e)
        }
    }

    /** Polls all UDP channels for responses and writes them back to TUN. */
    private suspend fun udpResponseLoop() {
        val selector = Selector.open()
        val responseBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE)

        while (running && scope.isActive) {
            // Register any new channels
            for (session in udpChannels.values) {
                if (session.channel.isOpen && session.selectionKey == null) {
                    session.selectionKey = session.channel.register(
                        selector, SelectionKey.OP_READ, session
                    )
                }
            }

            val ready = selector.select(SELECTOR_TIMEOUT_MS)
            if (ready == 0) continue

            val iter = selector.selectedKeys().iterator()
            while (iter.hasNext()) {
                val key = iter.next()
                iter.remove()
                if (!key.isReadable) continue

                val session = key.attachment() as UdpSession
                responseBuffer.clear()
                val sender = try {
                    session.channel.receive(responseBuffer) as? InetSocketAddress
                } catch (e: IOException) {
                    Log.w(TAG, "UDP receive error", e)
                    continue
                } ?: continue

                responseBuffer.flip()
                val sourceInfo = session.pendingSources[sender.port] ?: continue

                // Parse DNS responses (port 53)
                if (sender.port == 53 && responseBuffer.remaining() > 0) {
                    try {
                        val dnsBuf = responseBuffer.duplicate()
                        hostnameResolver.onDnsResponse(dnsBuf)
                    } catch (e: Exception) {
                        Log.w(TAG, "DNS response parse error (non-fatal)", e)
                    }
                }

                // Track incoming flow
                val responseInfo = PacketInfo(
                    srcAddress = sender.address,
                    srcPort = sender.port,
                    dstAddress = sourceInfo.srcAddress,
                    dstPort = sourceInfo.srcPort,
                    protocol = Protocol.UDP,
                    ipVersion = sourceInfo.ipVersion,
                    ipHeaderLength = 0,
                    transportHeaderLength = UdpHeader_LENGTH,
                    payloadLength = responseBuffer.remaining(),
                    totalLength = responseBuffer.remaining(),
                )
                flowTracker.onPacket(responseInfo, outgoing = false)
                flowTracker.recordTrafficSample(responseInfo, outgoing = false)

                // Build response IP+UDP packet and write to TUN
                val responsePacket = buildUdpResponsePacket(
                    sourceInfo = sourceInfo,
                    senderPort = sender.port,
                    payload = responseBuffer,
                )
                if (responsePacket != null) {
                    try {
                        synchronized(tunOutput) {
                            tunOutput.write(responsePacket)
                        }
                    } catch (e: IOException) {
                        if (running) Log.w(TAG, "TUN write error", e)
                    }
                }
            }
        }
        selector.close()
    }

    // ── TCP relay ──────────────────────────────────────────────

    private fun relayTcp(info: PacketInfo, packet: ByteBuffer, packetLength: Int) {
        val sessionKey = info.flowKey.hashCode().toLong()

        if (info.isSyn && !info.isSynAck) {
            // Extract client's initial sequence number from TCP header
            val tcpOffset = packet.position() + info.ipHeaderLength
            val clientSeqNum = packet.getInt(tcpOffset + 4).toLong() and 0xFFFFFFFFL
            // Launch TCP connect asynchronously — blocking connect must not stall the read loop
            scope.launch(Dispatchers.IO) {
                try {
                    val session = openTcpSession(info, clientSeqNum + 1) ?: return@launch
                    tcpSessions[sessionKey] = session
                    val synAck = buildTcpControlPacket(
                        session = session,
                        flags = TCP_SYN or TCP_ACK,
                    )
                    writeTun(synAck)
                    session.ourSeqNum++ // SYN-ACK consumes 1 sequence number
                    session.state = TcpState.SYN_RECEIVED
                } catch (e: Exception) {
                    Log.w(TAG, "TCP session setup failed to ${info.dstAddress}:${info.dstPort}", e)
                }
            }
            return
        }

        val session = tcpSessions[sessionKey] ?: return

        when {
            info.isRst -> {
                session.close()
                tcpSessions.remove(sessionKey)
            }

            info.isFin -> {
                val finAck = buildTcpControlPacket(session, TCP_FIN or TCP_ACK)
                writeTun(finAck)
                session.close()
                tcpSessions.remove(sessionKey)
            }

            info.isAck && session.state == TcpState.SYN_RECEIVED -> {
                session.state = TcpState.ESTABLISHED
                session.readerJob = scope.launch(Dispatchers.IO) {
                    tcpReadFromRemote(session, sessionKey)
                }
                session.writerJob = scope.launch(Dispatchers.IO) {
                    tcpWriteToRemote(session, sessionKey)
                }
            }

            session.state == TcpState.ESTABLISHED -> {
                val payloadOffset = info.ipHeaderLength + info.transportHeaderLength
                if (payloadOffset < packetLength) {
                    val payload = ByteArray(packetLength - payloadOffset)
                    System.arraycopy(packet.array(), payloadOffset, payload, 0, payload.size)

                    if (!session.sniChecked && info.dstPort == 443 && payload.isNotEmpty()) {
                        session.sniChecked = true
                        try {
                            hostnameResolver.onTlsClientHello(info.flowKey, ByteBuffer.wrap(payload))
                        } catch (e: Exception) {
                            Log.w(TAG, "SNI parse error (non-fatal)", e)
                        }
                    }

                    session.theirSeqNum += payload.size

                    val ack = buildTcpControlPacket(session, TCP_ACK)
                    writeTun(ack)

                    session.writeQueue.trySend(payload)
                }
            }
        }
    }

    private fun openTcpSession(info: PacketInfo, theirSeqNum: Long): TcpSession? {
        return try {
            val channel = SocketChannel.open()
            vpnService.protect(channel.socket())
            // Use Socket.connect with timeout to avoid blocking for 75+ seconds
            channel.socket().connect(
                InetSocketAddress(info.dstAddress, info.dstPort),
                TCP_CONNECT_TIMEOUT_MS,
            )
            channel.configureBlocking(true)

            TcpSession(
                srcAddress = info.srcAddress,
                srcPort = info.srcPort,
                dstAddress = info.dstAddress,
                dstPort = info.dstPort,
                ipVersion = info.ipVersion,
                channel = channel,
                ourSeqNum = System.nanoTime() and 0xFFFFFFFFL,  // random-ish ISN
                theirSeqNum = theirSeqNum, // client SYN seq + 1
                state = TcpState.CONNECTING,
            )
        } catch (e: IOException) {
            Log.w(TAG, "TCP connect failed to ${info.dstAddress}:${info.dstPort}", e)
            null
        }
    }

    /** Reads from remote destination and sends data packets back to app via TUN. */
    private suspend fun tcpReadFromRemote(session: TcpSession, sessionKey: Long) {
        val buf = ByteBuffer.allocate(MAX_PACKET_SIZE - 60) // leave room for headers
        try {
            while (running && session.state == TcpState.ESTABLISHED && scope.isActive) {
                buf.clear()
                val read = session.channel.read(buf)
                if (read <= 0) break

                buf.flip()
                val payload = ByteArray(read)
                buf.get(payload)

                // Track incoming data
                val responseInfo = PacketInfo(
                    srcAddress = session.dstAddress,
                    srcPort = session.dstPort,
                    dstAddress = session.srcAddress,
                    dstPort = session.srcPort,
                    protocol = Protocol.TCP,
                    ipVersion = session.ipVersion,
                    ipHeaderLength = 0,
                    transportHeaderLength = 0,
                    payloadLength = read,
                    totalLength = read,
                )
                flowTracker.onPacket(responseInfo, outgoing = false)
                flowTracker.recordTrafficSample(responseInfo, outgoing = false)

                // Build TCP data packet back to app
                val dataPacket = buildTcpDataPacket(session, payload)
                session.ourSeqNum += payload.size
                writeTun(dataPacket)
            }
        } catch (e: IOException) {
            if (running) Log.d(TAG, "TCP remote read ended", e)
        }

        // Remote closed: send FIN to app
        if (session.state == TcpState.ESTABLISHED) {
            val fin = buildTcpControlPacket(session, TCP_FIN or TCP_ACK)
            writeTun(fin)
        }
        session.close()
        tcpSessions.remove(sessionKey)
    }

    /** Drains the write queue and sends data to the remote server. */
    private suspend fun tcpWriteToRemote(session: TcpSession, sessionKey: Long) {
        try {
            for (data in session.writeQueue) {
                session.channel.write(ByteBuffer.wrap(data))
            }
        } catch (e: IOException) {
            if (running) Log.w(TAG, "TCP write to remote failed", e)
            sendRst(session, sessionKey)
        }
    }

    private fun sendRst(session: TcpSession, sessionKey: Long) {
        val rst = buildTcpControlPacket(session, TCP_RST or TCP_ACK)
        writeTun(rst)
        session.close()
        tcpSessions.remove(sessionKey)
    }

    // ── Cleanup loop ───────────────────────────────────────────

    private suspend fun cleanupLoop() {
        while (running && scope.isActive) {
            kotlinx.coroutines.delay(30_000)
            val now = System.currentTimeMillis()

            // Clean idle UDP channels
            val udpIter = udpChannels.entries.iterator()
            while (udpIter.hasNext()) {
                val entry = udpIter.next()
                if (now - entry.value.lastActive > UDP_IDLE_TIMEOUT_MS) {
                    entry.value.channel.close()
                    udpIter.remove()
                }
            }

            // Clean dead TCP sessions
            val tcpIter = tcpSessions.entries.iterator()
            while (tcpIter.hasNext()) {
                val entry = tcpIter.next()
                if (!entry.value.channel.isConnected) {
                    entry.value.close()
                    tcpIter.remove()
                }
            }

            // Tell flow tracker to flush timed-out flows
            flowTracker.flushTimedOut()
        }
    }

    // ── Packet construction helpers ────────────────────────────

    private fun writeTun(packet: ByteArray?) {
        if (packet == null) return
        try {
            synchronized(tunOutput) {
                tunOutput.write(packet)
            }
        } catch (e: IOException) {
            if (running) Log.w(TAG, "TUN write failed", e)
        }
    }

    /**
     * Builds an IP+UDP packet for a response from the internet back to the
     * originating app.
     */
    private fun buildUdpResponsePacket(
        sourceInfo: UdpSource,
        senderPort: Int,
        payload: ByteBuffer,
    ): ByteArray? {
        val payloadBytes = ByteArray(payload.remaining())
        payload.get(payloadBytes)

        return if (sourceInfo.ipVersion == 4) {
            buildIp4UdpPacket(
                srcAddr = sourceInfo.dstAddress,
                srcPort = senderPort,
                dstAddr = sourceInfo.srcAddress,
                dstPort = sourceInfo.srcPort,
                payload = payloadBytes,
            )
        } else {
            buildIp6UdpPacket(
                srcAddr = sourceInfo.dstAddress,
                srcPort = senderPort,
                dstAddr = sourceInfo.srcAddress,
                dstPort = sourceInfo.srcPort,
                payload = payloadBytes,
            )
        }
    }

    private fun buildTcpControlPacket(session: TcpSession, flags: Int): ByteArray? {
        return buildTcpPacket(session, flags, ByteArray(0))
    }

    private fun buildTcpDataPacket(session: TcpSession, payload: ByteArray): ByteArray? {
        return buildTcpPacket(session, TCP_ACK or TCP_PSH, payload)
    }

    private fun buildTcpPacket(session: TcpSession, flags: Int, payload: ByteArray): ByteArray? {
        val tcpHeaderLen = 20
        val tcpLen = tcpHeaderLen + payload.size

        val tcp = ByteBuffer.allocate(tcpLen)
        tcp.putShort(session.dstPort.toShort())    // src port (from server perspective)
        tcp.putShort(session.srcPort.toShort())    // dst port (back to app)
        tcp.putInt(session.ourSeqNum.toInt())
        tcp.putInt(session.theirSeqNum.toInt())    // ack
        tcp.putShort(((tcpHeaderLen / 4 shl 12) or flags).toShort())
        tcp.putShort(65535.toShort()) // window
        tcp.putShort(0)              // checksum placeholder
        tcp.putShort(0)              // urgent pointer
        if (payload.isNotEmpty()) tcp.put(payload)
        tcp.flip()

        // Compute TCP checksum (mandatory for both IPv4 and IPv6)
        val srcAddr = session.dstAddress  // from server perspective
        val dstAddr = session.srcAddress  // back to app
        computeTcpChecksum(tcp, srcAddr, dstAddr, tcpLen)

        return if (session.ipVersion == 4) {
            wrapIp4(srcAddr, dstAddr, 6, tcp, tcpLen)
        } else {
            wrapIp6(srcAddr, dstAddr, 6, tcp, tcpLen)
        }
    }

    /**
     * Computes the TCP checksum over the pseudo-header + TCP segment.
     * Writes the result into the checksum field at offset 16 in the segment.
     */
    private fun computeTcpChecksum(
        segment: ByteBuffer,
        srcAddr: InetAddress,
        dstAddr: InetAddress,
        segmentLength: Int,
    ) {
        var sum = 0L
        val pos = segment.position()

        // Pseudo-header: src address + dst address + zero + protocol(6) + TCP length
        val srcBytes = srcAddr.address
        val dstBytes = dstAddr.address
        for (i in srcBytes.indices step 2) {
            sum += ((srcBytes[i].toInt() and 0xFF) shl 8) or (srcBytes[i + 1].toInt() and 0xFF)
        }
        for (i in dstBytes.indices step 2) {
            sum += ((dstBytes[i].toInt() and 0xFF) shl 8) or (dstBytes[i + 1].toInt() and 0xFF)
        }
        sum += 6  // protocol = TCP
        sum += segmentLength

        // TCP segment (checksum field at offset 16 is already 0)
        for (i in 0 until segmentLength - 1 step 2) {
            sum += ((segment.get(pos + i).toInt() and 0xFF) shl 8) or
                    (segment.get(pos + i + 1).toInt() and 0xFF)
        }
        if (segmentLength % 2 != 0) {
            sum += (segment.get(pos + segmentLength - 1).toInt() and 0xFF) shl 8
        }

        // Fold 32-bit sum into 16 bits
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv().toInt() and 0xFFFF

        segment.putShort(pos + 16, checksum.toShort())
    }

    private fun buildIp4UdpPacket(
        srcAddr: InetAddress, srcPort: Int,
        dstAddr: InetAddress, dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val udp = ByteBuffer.allocate(udpLen)
        udp.putShort(srcPort.toShort())
        udp.putShort(dstPort.toShort())
        udp.putShort(udpLen.toShort())
        udp.putShort(0) // checksum (optional for IPv4 UDP)
        udp.put(payload)
        udp.flip()

        return wrapIp4(srcAddr, dstAddr, 17, udp, udpLen)!!
    }

    private fun buildIp6UdpPacket(
        srcAddr: InetAddress, srcPort: Int,
        dstAddr: InetAddress, dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val udp = ByteBuffer.allocate(udpLen)
        udp.putShort(srcPort.toShort())
        udp.putShort(dstPort.toShort())
        udp.putShort(udpLen.toShort())
        udp.putShort(0) // checksum
        udp.put(payload)
        udp.flip()

        return wrapIp6(srcAddr, dstAddr, 17, udp, udpLen)!!
    }

    private fun wrapIp4(
        src: InetAddress, dst: InetAddress,
        protocol: Int, transportData: ByteBuffer, transportLen: Int,
    ): ByteArray? {
        val ipHeaderLen = 20
        val totalLen = ipHeaderLen + transportLen
        val packet = ByteBuffer.allocate(totalLen)

        packet.put((0x45).toByte())           // Version=4, IHL=5
        packet.put(0)                          // DSCP/ECN
        packet.putShort(totalLen.toShort())
        packet.putShort(0)                     // Identification
        packet.putShort(0x4000.toShort())      // Flags=Don't Fragment, offset=0
        packet.put(64)                         // TTL
        packet.put(protocol.toByte())
        packet.putShort(0)                     // checksum placeholder
        packet.put(src.address)
        packet.put(dst.address)

        // Calculate IP header checksum
        val checksumPos = 10
        var sum = 0L
        for (i in 0 until ipHeaderLen step 2) {
            sum += (packet.get(i).toInt() and 0xFF shl 8) or (packet.get(i + 1).toInt() and 0xFF)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv().toInt() and 0xFFFF
        packet.putShort(checksumPos, checksum.toShort())

        // Append transport data
        packet.position(ipHeaderLen)
        packet.put(transportData)

        return packet.array()
    }

    private fun wrapIp6(
        src: InetAddress, dst: InetAddress,
        nextHeader: Int, transportData: ByteBuffer, transportLen: Int,
    ): ByteArray? {
        val ipHeaderLen = 40
        val packet = ByteBuffer.allocate(ipHeaderLen + transportLen)

        packet.put(0x60.toByte())              // Version=6
        packet.put(0)                          // Traffic class
        packet.putShort(0)                     // Flow label
        packet.putShort(transportLen.toShort())
        packet.put(nextHeader.toByte())
        packet.put(64)                         // Hop limit
        packet.put(src.address)
        packet.put(dst.address)
        packet.put(transportData)

        return packet.array()
    }

    // ── Session data classes ───────────────────────────────────

    private data class UdpSource(
        val srcAddress: InetAddress,
        val srcPort: Int,
        val dstAddress: InetAddress,
        val dstPort: Int,
        val ipVersion: Int,
    )

    private class UdpSession(
        val channel: DatagramChannel,
        var lastActive: Long,
        var selectionKey: SelectionKey? = null,
        val pendingSources: ConcurrentHashMap<Int, UdpSource> = ConcurrentHashMap(),
    )

    private class TcpSession(
        val srcAddress: InetAddress,
        val srcPort: Int,
        val dstAddress: InetAddress,
        val dstPort: Int,
        val ipVersion: Int,
        val channel: SocketChannel,
        var ourSeqNum: Long,
        var theirSeqNum: Long,
        var state: TcpState,
        var sniChecked: Boolean = false,
        var readerJob: Job? = null,
        var writerJob: Job? = null,
        val writeQueue: CoroutineChannel<ByteArray> = CoroutineChannel(CoroutineChannel.UNLIMITED),
    ) {
        fun close() {
            readerJob?.cancel()
            writerJob?.cancel()
            writeQueue.close()
            try { channel.close() } catch (_: IOException) {}
            state = TcpState.CLOSED
        }
    }

    private enum class TcpState {
        CONNECTING, SYN_RECEIVED, ESTABLISHED, CLOSED,
    }
}

// Avoid importing from UdpHeader just for a constant
private const val UdpHeader_LENGTH = 8

// TCP flag constants for packet construction
private const val TCP_FIN = 0x01
private const val TCP_SYN = 0x02
private const val TCP_RST = 0x04
private const val TCP_PSH = 0x08
private const val TCP_ACK = 0x10
