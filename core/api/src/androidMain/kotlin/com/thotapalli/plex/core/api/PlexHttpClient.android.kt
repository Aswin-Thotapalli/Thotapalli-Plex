package com.thotapalli.plex.core.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

actual fun plexHttpClientEngine(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        block()
        engine {
            // Plex servers on a local network commonly present a self signed certificate
            // behind a plex.direct hostname. OkHttp's defaults handle that correctly
            // because plex.direct resolves to a real certificate, so no trust override
            // is installed here and none should ever be.
            config {
                retryOnConnectionFailure(true)
            }
        }
    }
