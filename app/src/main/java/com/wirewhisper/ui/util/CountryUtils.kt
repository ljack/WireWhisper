package com.wirewhisper.ui.util

import java.util.Locale

fun countryCodeToFlag(code: String): String {
    if (code.length != 2) return "\uD83C\uDF10"
    val first = 0x1F1E6 + (code[0].uppercaseChar() - 'A')
    val second = 0x1F1E6 + (code[1].uppercaseChar() - 'A')
    return String(intArrayOf(first, second), 0, 2)
}

fun countryDisplayName(code: String): String = Locale.of("", code).displayCountry
