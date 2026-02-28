package com.wirewhisper.core.model

import java.net.InetAddress

/**
 * 5-tuple identifying a unique network flow.
 * Used as the key for flow aggregation in [com.wirewhisper.flow.FlowTracker].
 */
data class FlowKey(
    val srcAddress: InetAddress,
    val srcPort: Int,
    val dstAddress: InetAddress,
    val dstPort: Int,
    val protocol: Protocol,
)
