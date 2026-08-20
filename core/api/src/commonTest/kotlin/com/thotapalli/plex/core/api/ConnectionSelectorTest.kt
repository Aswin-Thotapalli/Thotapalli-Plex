package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.ServerConnection
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The selector must resolve as soon as a good connection answers rather than waiting on the
 * slowest probe, while still honouring the CLAUDE.md section 5 ranking and never returning a
 * connection whose probe did not actually succeed.
 *
 * These cases pin down the *result* of that ranking with immediate responses. The timing
 * behaviour itself (early return on local, the grace window) is not asserted here: the Ktor
 * MockEngine runs handler delays on a real dispatcher, so runTest's virtual clock does not
 * drive probe timing, and a delay-based test would depend on wall-clock ordering. Faking the
 * clock would mean faking the HTTP layer wholesale, which these tests deliberately do not do.
 */
class ConnectionSelectorTest {

    private val machineId = "machine-1"
    private val headers = PlexHeaders { mapOf(PlexHeaderNames.CLIENT_IDENTIFIER to "client-1") }

    private fun connection(host: String, local: Boolean, relay: Boolean) =
        ServerConnection(uri = "https://$host:32400", local = local, relay = relay)

    private fun server(vararg connections: ServerConnection) = PlexServer(
        machineIdentifier = machineId,
        name = "Server",
        accessToken = "server-token",
        owned = false,
        connections = connections.toList(),
    )

    /** Answers /identity per host: a host in [identities] returns that machine id, others 404. */
    private fun selector(identities: Map<String, String>): ConnectionSelector {
        val client = HttpClient(
            MockEngine { request: HttpRequestData ->
                val id = identities[request.url.host]
                if (id == null) {
                    respondError(HttpStatusCode.NotFound)
                } else {
                    respond(
                        content = """{"MediaContainer":{"machineIdentifier":"$id","size":1}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            },
        ) {
            expectSuccess = false
            install(ContentNegotiation) { json(plexJson) }
            install(HttpTimeout)
        }
        return ConnectionSelector(PlexHttp(client), headers, nowMs = { 0L }, elapsedMs = { 0L })
    }

    private fun allReachable(vararg conns: ServerConnection) =
        conns.associate { it.uri.removePrefix("https://").substringBefore(':') to machineId }

    @Test
    fun aLocalConnectionWinsOverEveryRemoteOne() = runTest {
        val local = connection("local.test", local = true, relay = false)
        val direct = connection("direct.test", local = false, relay = false)
        val relay = connection("relay.test", local = false, relay = true)

        val selected = selector(allReachable(local, direct, relay)).select(server(local, direct, relay))

        assertEquals(local, selected?.connection)
    }

    @Test
    fun withNoLocalTheNonRelayConnectionWins() = runTest {
        val direct = connection("direct.test", local = false, relay = false)
        val relay = connection("relay.test", local = false, relay = true)

        val selected = selector(allReachable(direct, relay)).select(server(direct, relay))

        assertEquals(direct, selected?.connection)
    }

    @Test
    fun aConnectionThatDoesNotAnswerIsNeverSelectedEvenWhenTopRanked() = runTest {
        // The local host is reachable but is a *different* server, so its probe fails the
        // identity check. The winner must be the connection whose probe actually succeeded.
        val local = connection("local.test", local = true, relay = false)
        val direct = connection("direct.test", local = false, relay = false)
        val identities = mapOf("local.test" to "someone-else", "direct.test" to machineId)

        val selected = selector(identities).select(server(local, direct))

        assertEquals(direct, selected?.connection)
    }

    @Test
    fun nothingIsSelectedWhenNoConnectionAnswers() = runTest {
        val direct = connection("direct.test", local = false, relay = false)
        val relay = connection("relay.test", local = false, relay = true)

        val selected = selector(emptyMap()).select(server(direct, relay))

        assertNull(selected)
    }

    @Test
    fun anEmptyServerSelectsNothing() = runTest {
        assertNull(selector(emptyMap()).select(server()))
    }
}
