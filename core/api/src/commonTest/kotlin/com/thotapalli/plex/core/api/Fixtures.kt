package com.thotapalli.plex.core.api

/**
 * Reads a recorded Plex response from core/api/src/commonTest/resources.
 *
 * Kotlin Multiplatform has no common resource API, so this is expect/actual even though
 * both targets here happen to be JVM.
 */
expect fun fixture(name: String): String
