package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.model.PlexAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val account = PlexAccount(
    id = 42,
    uuid = "abc",
    username = "viewer",
    title = "Viewer",
    email = "viewer@example.com",
    thumbUrl = null,
)

class TokenStoreTest {

    @Test
    fun aStoredTokenIsReadBackAfterARestart() {
        val secure = FakeSecureStore()
        val plain = FakeKeyValueStore()

        TokenStore(secure, plain).storeAccountToken("token-value", account)

        val afterRestart = TokenStore(secure.reopen(), plain.reopen())

        assertEquals("token-value", afterRestart.accountToken())
        assertEquals("Viewer", afterRestart.signedInUsername())
        assertTrue(afterRestart.isSignedIn())
    }

    @Test
    fun theTokenNeverLandsInPlainStorage() {
        val secure = FakeSecureStore()
        val plain = FakeKeyValueStore()

        TokenStore(secure, plain).storeAccountToken("token-value", account)

        assertNull(plain.getString(StorageKeys.ACCOUNT_TOKEN))
        assertNotNull(secure.getSecret(StorageKeys.ACCOUNT_TOKEN))
    }

    @Test
    fun signOutClearsTheTokenButKeepsTheClientIdentifier() {
        val secure = FakeSecureStore()
        val plain = FakeKeyValueStore()
        val identifier = IdentityHeaderProvider(plain, testDevice).clientIdentifier
        val store = TokenStore(secure, plain)
        store.storeAccountToken("token-value", account)

        store.signOut()

        assertFalse(store.isSignedIn())
        assertNull(store.accountToken())
        // The identifier belongs to the installation, not the account. Regenerating it
        // would duplicate the device entry on the next sign in.
        assertEquals(identifier, plain.getString(StorageKeys.CLIENT_IDENTIFIER))
    }

    @Test
    fun switchingHomeUserReplacesTheToken() {
        val secure = FakeSecureStore()
        val plain = FakeKeyValueStore()
        val store = TokenStore(secure, plain)
        store.storeAccountToken("admin-token", account)

        store.storeSwitchedToken("child-token", "uuid-child")

        assertEquals("child-token", store.accountToken())
        assertEquals("uuid-child", plain.getString(StorageKeys.HOME_USER_UUID))
    }
}
