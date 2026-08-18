package com.thotapalli.plex.core.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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

    companion object {
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
            },
        )
    }
}
