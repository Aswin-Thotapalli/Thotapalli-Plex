package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.model.PlexAccount
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CLAUDE.md section 16 phase 2 step 4: a stored token is read back after restart on
 * Windows. A fresh store object over the same directory is exactly that, since nothing is
 * held in memory between the two.
 *
 * DPAPI is a Windows API, so these skip elsewhere rather than fail. The CI matrix runs the
 * desktop job on a Windows runner for that reason.
 */
class WindowsStorageTest {

    private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val directories = mutableListOf<Path>()

    private fun tempDir(prefix: String): Path =
        Files.createTempDirectory(prefix).also(directories::add)

    @AfterTest
    fun cleanUp() {
        directories.forEach { dir ->
            runCatching {
                Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    @Test
    fun dpapiRoundTripsASecretAcrossAFreshStore() {
        if (!onWindows) return
        val dir = tempDir("dpapi-round-trip")

        DpapiSecureStore(dir).putSecret(StorageKeys.ACCOUNT_TOKEN, "an-account-token")

        // A new store object over the same directory holds no state from the first one.
        val afterRestart = DpapiSecureStore(dir)

        assertEquals("an-account-token", afterRestart.getSecret(StorageKeys.ACCOUNT_TOKEN))
    }

    @Test
    fun theTokenIsNotStoredInClear() {
        if (!onWindows) return
        val dir = tempDir("dpapi-ciphertext")
        val secret = "a-very-recognisable-token-value"

        DpapiSecureStore(dir).putSecret(StorageKeys.ACCOUNT_TOKEN, secret)

        val onDisk = Files.list(dir).use { it.toList() }
        assertEquals(1, onDisk.size, "one file per secret")
        val bytes = onDisk.single().readBytes()
        assertFalse(
            String(bytes, Charsets.UTF_8).contains(secret),
            "CryptProtectData output must not contain the plaintext",
        )
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun removingASecretDeletesIt() {
        if (!onWindows) return
        val dir = tempDir("dpapi-remove")
        val store = DpapiSecureStore(dir)
        store.putSecret(StorageKeys.ACCOUNT_TOKEN, "token")

        store.removeSecret(StorageKeys.ACCOUNT_TOKEN)

        assertNull(DpapiSecureStore(dir).getSecret(StorageKeys.ACCOUNT_TOKEN))
    }

    @Test
    fun aCorruptSecretIsDroppedRatherThanThrowing() {
        if (!onWindows) return
        val dir = tempDir("dpapi-corrupt")
        val store = DpapiSecureStore(dir)
        store.putSecret(StorageKeys.ACCOUNT_TOKEN, "token")
        val file = Files.list(dir).use { it.toList() }.single()
        Files.write(file, "not base64 ciphertext".toByteArray())

        // Signing the user out beats failing every launch forever.
        assertNull(DpapiSecureStore(dir).getSecret(StorageKeys.ACCOUNT_TOKEN))
        assertFalse(Files.exists(file))
    }

    @Test
    fun theFileKeyValueStoreSurvivesARestart() {
        val dir = tempDir("kv-restart")
        val file = dir.resolve("settings.properties")

        FileKeyValueStore(file).putString(StorageKeys.CLIENT_IDENTIFIER, "an-identifier")

        assertEquals("an-identifier", FileKeyValueStore(file).getString(StorageKeys.CLIENT_IDENTIFIER))
    }

    @Test
    fun theWholeTokenStoreSurvivesARestartOnWindows() {
        if (!onWindows) return
        val dir = tempDir("token-store-restart")
        val account = PlexAccount(
            id = 7,
            uuid = "uuid",
            username = "viewer",
            title = "Viewer",
            email = null,
            thumbUrl = null,
        )

        TokenStore(DpapiSecureStore(dir), FileKeyValueStore(dir.resolve("settings.properties")))
            .storeAccountToken("stored-token", account)

        val afterRestart = TokenStore(
            DpapiSecureStore(dir),
            FileKeyValueStore(dir.resolve("settings.properties")),
        )

        assertTrue(afterRestart.isSignedIn())
        assertEquals("stored-token", afterRestart.accountToken())
        assertEquals("Viewer", afterRestart.signedInUsername())
    }
}
