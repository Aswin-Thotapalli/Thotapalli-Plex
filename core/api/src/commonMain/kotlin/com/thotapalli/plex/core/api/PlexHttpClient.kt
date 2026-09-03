package com.thotapalli.plex.core.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The engine differs by platform: OkHttp on Android, the JDK client on the desktop.
 * Everything above the engine is identical, which is the point of configuring it here.
 */
expect fun plexHttpClientEngine(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Plex answers with fields this client does not model and occasionally changes types, so
 * the parser ignores unknown keys and coerces rather than throwing mid-list.
 */
val plexJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Owns the HTTP client.
 *
 * Ktor is an implementation detail of core/api and appears nowhere in its public API, so
 * nothing above this module needs Ktor on its compile classpath. That is what keeps a
 * change of HTTP library inside one module.
 */
class PlexHttp internal constructor(
    internal val client: HttpClient,
) : AutoCloseable {

    override fun close() {
        client.close()
    }

    /**
     * Fetches a plain document and returns it as text.
     *
     * Exists for the update manifest in CLAUDE.md section 17, which is a static JSON file
     * on a release host rather than a Plex endpoint: it carries no identity headers and no
     * token. Returning a String keeps Ktor from leaking into the caller, which is what
     * lets core/session read the manifest without an HTTP dependency of its own.
     */
    suspend fun fetchText(url: String): String {
        val response = client.get(url)
        response.requireSuccess("fetch ${'$'}url")
        return response.bodyAsText()
    }

    companion object {
        /** Retry a transient failure at most twice before surfacing it. */
        private const val MAX_RETRIES = 2

        /**
         * @param requestTimeoutMs whole-request budget. Connection probing overrides this
         *   with the 3000 ms in CLAUDE.md section 5; ordinary calls use the default.
         */
        fun create(
            requestTimeoutMs: Long = 15_000,
            connectTimeoutMs: Long = 10_000,
        ): PlexHttp = PlexHttp(
            plexHttpClientEngine {
                expectSuccess = false

                install(ContentNegotiation) {
                    json(plexJson)
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = requestTimeoutMs
                    connectTimeoutMillis = connectTimeoutMs
                    socketTimeoutMillis = requestTimeoutMs
                }

                // Ride out a transient blip — a 5xx while the server is busy, a connection reset on a
                // flaky link — rather than surfacing it as a hard failure. Bounded and backed off so a
                // genuinely down server is still reported quickly. A timeout is NOT retried (the
                // caller's own timeout already expresses the budget), and the connection probe opts out
                // per-request because its parallel race is its own retry. See ConnectionSelector.
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = MAX_RETRIES)
                    retryOnException(maxRetries = MAX_RETRIES, retryOnTimeout = false)
                    exponentialDelay(base = 2.0, baseDelayMs = 500L)
                }
            },
        )
    }
}
