package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.api.PLEX_PRODUCT
import com.thotapalli.plex.core.api.PlexHeaderNames
import com.thotapalli.plex.core.api.PlexHeaders

/**
 * Produces the identity headers sent on every request to plex.tv and to a server.
 *
 * The client identifier is generated once on first launch and persisted forever. Changing
 * it creates a duplicate device entry on the account, so it is read from [store] and only
 * written when absent. See CLAUDE.md section 5.
 *
 * The token is not included here. plex.tv receives the account token and a server receives
 * its own access token, and the two must never be interchanged, so each call site adds it.
 */
class IdentityHeaderProvider(
    private val store: KeyValueStore,
    private val device: DeviceInfo,
    private val newUuid: () -> String = ::randomUuidV4,
) : PlexHeaders {

    /**
     * Read once and cached for the process. Reading through to [store] every time would
     * make every request a disk read for a value that cannot change while running.
     */
    val clientIdentifier: String by lazy {
        store.getString(StorageKeys.CLIENT_IDENTIFIER)
            ?.takeIf { it.isNotBlank() }
            ?: newUuid().also { store.putString(StorageKeys.CLIENT_IDENTIFIER, it) }
    }

    private val base: Map<String, String> by lazy {
        mapOf(
            PlexHeaderNames.CLIENT_IDENTIFIER to clientIdentifier,
            PlexHeaderNames.PRODUCT to PLEX_PRODUCT,
            PlexHeaderNames.VERSION to device.appVersion,
            PlexHeaderNames.PLATFORM to device.platform,
            PlexHeaderNames.PLATFORM_VERSION to device.platformVersion,
            PlexHeaderNames.DEVICE to device.device,
            PlexHeaderNames.DEVICE_NAME to device.deviceName,
        )
    }

    override suspend fun headers(): Map<String, String> = base

    /**
     * Headers for a playback session. The session identifier is generated per playback
     * session, not per request, because the server uses it to correlate timeline updates
     * with one continuous view. See CLAUDE.md section 5.
     */
    fun playbackHeaders(sessionIdentifier: String): Map<String, String> =
        base + (PlexHeaderNames.SESSION_IDENTIFIER to sessionIdentifier)

    /** A fresh session identifier. One per playback session. */
    fun newSessionIdentifier(): String = newUuid()
}
