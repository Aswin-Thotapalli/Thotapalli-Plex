package com.thotapalli.plex.core.session

import kotlin.random.Random

/**
 * A version 4 UUID.
 *
 * Written out rather than taken from a platform API so the client identifier and the
 * per-session identifier are produced identically on every target.
 */
fun randomUuidV4(random: Random = Random.Default): String {
    val bytes = ByteArray(16).also(random::nextBytes)
    // Version 4, variant 10xx.
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val hex = bytes.joinToString("") { b ->
        val v = b.toInt() and 0xFF
        HEX[v shr 4].toString() + HEX[v and 0x0F]
    }
    return buildString {
        append(hex, 0, 8); append('-')
        append(hex, 8, 12); append('-')
        append(hex, 12, 16); append('-')
        append(hex, 16, 20); append('-')
        append(hex, 20, 32)
    }
}

private const val HEX = "0123456789abcdef"
