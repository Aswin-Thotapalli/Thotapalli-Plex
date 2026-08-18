package com.thotapalli.plex.core.api

/**
 * Supplies the identity headers sent on every request to plex.tv and to a server.
 *
 * Deliberately token free. The account token goes to plex.tv and the server access token
 * goes to the server, and the two are never interchanged, so each call site adds
 * [PlexHeaderNames.TOKEN] itself.
 *
 * Implemented in core/session, which owns the persisted client identifier.
 */
fun interface PlexHeaders {
    suspend fun headers(): Map<String, String>
}

object PlexHeaderNames {
    const val CLIENT_IDENTIFIER = "X-Plex-Client-Identifier"
    const val PRODUCT = "X-Plex-Product"
    const val VERSION = "X-Plex-Version"
    const val PLATFORM = "X-Plex-Platform"
    const val PLATFORM_VERSION = "X-Plex-Platform-Version"
    const val DEVICE = "X-Plex-Device"
    const val DEVICE_NAME = "X-Plex-Device-Name"
    const val SESSION_IDENTIFIER = "X-Plex-Session-Identifier"
    const val TOKEN = "X-Plex-Token"
}

/** Fixed product name. Sent on every request and shown on the account's device list. */
const val PLEX_PRODUCT = "Thotapalli Plex"

const val PLEX_TV_BASE = "https://plex.tv"
const val PLEX_AUTH_BASE = "https://app.plex.tv"
