package com.wirewhisper.core.model

enum class Protocol(val number: Int) {
    TCP(6),
    UDP(17),
    ICMP(1),
    ICMPV6(58),
    OTHER(-1);

    companion object {
        fun fromNumber(number: Int): Protocol =
            entries.find { it.number == number } ?: OTHER
    }
}
