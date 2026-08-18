package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.SelectedConnection
import com.thotapalli.plex.core.model.ServerConnection
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.thotapalli.plex.core.api.dto.IdentityContainer
import com.thotapalli.plex.core.api.dto.MediaContainerResponse

/**
 * Picks the connection this device should use to reach a server.
 *
 * CLAUDE.md section 5: probe every connection in parallel with a 3000 ms timeout, rank the
 * successes by local first, then non-relay, then lowest round trip, and cache the winner
 * for thirty minutes.
 */
class ConnectionSelector(
    http: PlexHttp,
    private val identityHeaders: PlexHeaders,
    private val nowMs: () -> Long,
    private val elapsedMs: () -> Long = nowMs,
) {

    private val client = http.client

    suspend fun select(server: PlexServer): SelectedConnection? = coroutineScope {
        if (server.connections.isEmpty()) return@coroutineScope null

        val probes = server.connections.map { connection ->
            async { probe(server, connection) }
        }.awaitAll().filterNotNull()

        probes.minWithOrNull(RANKING)?.let { winner ->
            SelectedConnection(
                machineIdentifier = server.machineIdentifier,
                connection = winner.connection,
                roundTripMs = winner.roundTripMs,
                selectedAtMs = nowMs(),
            )
        }
    }

    /**
     * A probe confirms both reachability and identity. A reachable host that answers with
     * a different machine identifier is a different server, so it loses.
     */
    private suspend fun probe(server: PlexServer, connection: ServerConnection): Probe? = try {
        val startedAt = elapsedMs()
        val response = client.get("${connection.uri.trimEnd('/')}/identity") {
            timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS }
            applyIdentity(server.accessToken)
        }
        if (!response.status.isSuccess()) {
            null
        } else {
            val identity = response.body<MediaContainerResponse<IdentityContainer>>()
            if (identity.mediaContainer.machineIdentifier != server.machineIdentifier) {
                null
            } else {
                Probe(connection, elapsedMs() - startedAt)
            }
        }
    } catch (_: Exception) {
        // A connection that does not answer inside the timeout is simply not a candidate.
        null
    }

    private suspend fun HttpRequestBuilder.applyIdentity(token: String) {
        val identity = identityHeaders.headers()
        headers {
            identity.forEach { (name, value) -> append(name, value) }
            append("Accept", "application/json")
            append(PlexHeaderNames.TOKEN, token)
        }
    }

    private data class Probe(val connection: ServerConnection, val roundTripMs: Long)

    private companion object {
        const val PROBE_TIMEOUT_MS = 3_000L

        /** Local first, then non-relay, then lowest round trip. */
        val RANKING: Comparator<Probe> = compareBy(
            { !it.connection.local },
            { it.connection.relay },
            { it.roundTripMs },
        )
    }
}

/** A selection is re-probed after thirty minutes, and immediately on a network change. */
const val CONNECTION_CACHE_TTL_MS = 30 * 60 * 1000L

fun SelectedConnection.isStale(nowMs: Long): Boolean =
    nowMs - selectedAtMs >= CONNECTION_CACHE_TTL_MS
