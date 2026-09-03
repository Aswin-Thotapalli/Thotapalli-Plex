package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.SelectedConnection
import com.thotapalli.plex.core.model.ServerConnection
import io.ktor.client.call.body
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

        // Every probe races in parallel. Successes are published to this channel the moment
        // they answer, rather than waiting for the whole batch, so a fast local connection is
        // not held hostage by a dead or remote one still counting down its 3000 ms ceiling.
        val results = Channel<Probe>(Channel.UNLIMITED)
        val probeJobs = server.connections.map { connection ->
            launch {
                probe(server, connection)?.let { results.send(it) }
            }
        }
        // Close the channel once every probe has settled, so the loop below can tell the
        // difference between "still waiting" and "nothing left to answer".
        val closer = launch {
            probeJobs.joinAll()
            results.close()
        }

        val winner: Probe? = try {
            // Wait (bounded by the per-probe ceiling) for the first success, or for the
            // channel to close because every probe failed.
            val first = results.receiveCatching().getOrNull()
            when {
                first == null -> null // nothing answered
                // Local is always the top rank, so there is no reason to wait for anything else.
                first.connection.local -> first
                else -> {
                    val successes = mutableListOf(first)
                    // A non-local answered first. Give better connections a short grace window
                    // to arrive instead of blocking on the slowest probe, and stop the instant
                    // a local answers since nothing can beat it.
                    withTimeoutOrNull(GRACE_WINDOW_MS) {
                        while (true) {
                            val next = results.receiveCatching().getOrNull() ?: break // all settled
                            successes.add(next)
                            if (next.connection.local) break
                        }
                    }
                    successes.minWithOrNull(RANKING)
                }
            }
        } finally {
            // Decision made: cancel any probe still in flight so nothing leaks past this scope.
            probeJobs.forEach { it.cancel() }
            closer.cancel()
            results.cancel()
        }

        winner?.let {
            SelectedConnection(
                machineIdentifier = server.machineIdentifier,
                connection = it.connection,
                roundTripMs = it.roundTripMs,
                selectedAtMs = nowMs(),
            )
        }
    }

    /**
     * Confirm a single already-chosen [connection] still answers as this server, with one
     * identity probe instead of the full parallel sweep. Returns a refreshed selection, or
     * null when it no longer answers. This is what lets a relaunch reuse a persisted choice
     * without waiting for every dead or remote connection to time out. See CLAUDE.md section 5.
     */
    suspend fun verify(server: PlexServer, connection: ServerConnection): SelectedConnection? {
        val probe = probe(server, connection) ?: return null
        return SelectedConnection(
            machineIdentifier = server.machineIdentifier,
            connection = probe.connection,
            roundTripMs = probe.roundTripMs,
            selectedAtMs = nowMs(),
        )
    }

    /**
     * A probe confirms both reachability and identity. A reachable host that answers with
     * a different machine identifier is a different server, so it loses.
     */
    private suspend fun probe(server: PlexServer, connection: ServerConnection): Probe? = try {
        val startedAt = elapsedMs()
        val response = client.get("${connection.uri.trimEnd('/')}/identity") {
            timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS }
            // The parallel race across every connection is this probe's retry. Letting the client's
            // retry plugin also back off and retry a dead connection would defeat the 3 s ceiling that
            // keeps a fast local connection from waiting on a slow one.
            retry { noRetry() }
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

        /**
         * How long to keep collecting once a non-local connection has answered, before ranking
         * what arrived. Short enough that a dead connection's 3000 ms timeout is never waited on,
         * long enough for a slightly slower but better-ranked connection to still be considered.
         */
        const val GRACE_WINDOW_MS = 700L

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
