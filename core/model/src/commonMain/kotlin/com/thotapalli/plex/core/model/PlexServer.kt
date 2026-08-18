package com.thotapalli.plex.core.model

/**
 * A Plex Media Server reachable by this account.
 *
 * A shared library arrives with [owned] false. Every request to the server carries
 * [accessToken]; the account token is never sent to a server.
 */
data class PlexServer(
    val machineIdentifier: String,
    val name: String,
    val accessToken: String,
    val owned: Boolean,
    val connections: List<ServerConnection>,
)

/** One advertised route to a server. */
data class ServerConnection(
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
)

/**
 * The connection chosen for a server, with the round trip that won it.
 *
 * Ranking prefers local, then non-relay, then lowest round trip. See CLAUDE.md section 5.
 */
data class SelectedConnection(
    val machineIdentifier: String,
    val connection: ServerConnection,
    val roundTripMs: Long,
    val selectedAtMs: Long,
) {
    val uri: String get() = connection.uri
    val local: Boolean get() = connection.local
    val relay: Boolean get() = connection.relay
}
