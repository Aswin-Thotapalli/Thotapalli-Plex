package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.api.PLEX_PRODUCT
import com.thotapalli.plex.core.api.PlexHeaderNames
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityHeaderProviderTest {

    /**
     * CLAUDE.md section 5: the client identifier is generated once on first launch and
     * persisted forever. Changing it creates a duplicate device entry on the account.
     */
    @Test
    fun clientIdentifierSurvivesARestart() {
        val store = FakeKeyValueStore()

        val firstLaunch = IdentityHeaderProvider(store, testDevice)
        val generated = firstLaunch.clientIdentifier

        // Simulated restart: a new provider over a new store instance, same persisted data.
        val afterRestart = IdentityHeaderProvider(store.reopen(), testDevice)

        assertEquals(generated, afterRestart.clientIdentifier)
    }

    @Test
    fun clientIdentifierIsWrittenExactlyOnce() {
        val store = FakeKeyValueStore()

        val provider = IdentityHeaderProvider(store, testDevice)
        repeat(5) { provider.clientIdentifier }
        IdentityHeaderProvider(store.reopen(), testDevice).clientIdentifier

        assertEquals(1, store.writes, "the identifier must be written on first launch only")
    }

    @Test
    fun separateInstallationsGetSeparateIdentifiers() {
        val a = IdentityHeaderProvider(FakeKeyValueStore(), testDevice).clientIdentifier
        val b = IdentityHeaderProvider(FakeKeyValueStore(), testDevice).clientIdentifier

        assertNotEquals(a, b)
    }

    @Test
    fun aBlankStoredIdentifierIsReplaced() {
        val store = FakeKeyValueStore()
        store.putString(StorageKeys.CLIENT_IDENTIFIER, "   ")

        val identifier = IdentityHeaderProvider(store, testDevice).clientIdentifier

        assertTrue(identifier.isNotBlank())
        assertEquals(identifier, store.getString(StorageKeys.CLIENT_IDENTIFIER))
    }

    @Test
    fun headersCarryEveryIdentityFieldAndNoToken() = runTest {
        val provider = IdentityHeaderProvider(FakeKeyValueStore(), testDevice)

        val headers = provider.headers()

        assertEquals(PLEX_PRODUCT, headers[PlexHeaderNames.PRODUCT])
        assertEquals("0.1.0", headers[PlexHeaderNames.VERSION])
        assertEquals("Android", headers[PlexHeaderNames.PLATFORM])
        assertEquals("16 (API 37)", headers[PlexHeaderNames.PLATFORM_VERSION])
        assertEquals("Pixel Test", headers[PlexHeaderNames.DEVICE])
        assertEquals("Google Pixel Test", headers[PlexHeaderNames.DEVICE_NAME])
        assertContains(headers, PlexHeaderNames.CLIENT_IDENTIFIER)

        // The token is added per call site, because plex.tv and a server take different
        // tokens and the account token must never reach a server.
        assertTrue(PlexHeaderNames.TOKEN !in headers)
        assertTrue(PlexHeaderNames.SESSION_IDENTIFIER !in headers)
    }

    @Test
    fun playbackHeadersAddASessionIdentifier() {
        val provider = IdentityHeaderProvider(FakeKeyValueStore(), testDevice)

        val session = provider.newSessionIdentifier()
        val headers = provider.playbackHeaders(session)

        assertEquals(session, headers[PlexHeaderNames.SESSION_IDENTIFIER])
        assertNotEquals(session, provider.newSessionIdentifier())
    }
}

class UuidTest {

    @Test
    fun looksLikeAVersionFourUuid() {
        val pattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        repeat(200) {
            val uuid = randomUuidV4()
            assertTrue(pattern.matches(uuid), "not a v4 UUID: " + uuid)
        }
    }

    @Test
    fun doesNotRepeat() {
        val seen = buildSet { repeat(1_000) { add(randomUuidV4()) } }
        assertEquals(1_000, seen.size)
    }
}
