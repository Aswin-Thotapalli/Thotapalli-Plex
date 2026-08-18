package com.thotapalli.plex.core.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.java.Java

actual fun plexHttpClientEngine(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Java) {
        block()
    }
