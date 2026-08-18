package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.model.PlexAccount

/**
 * The account token and the identity behind it.
 *
 * The token goes to [SecureStore], which is EncryptedSharedPreferences on Android and
 * DPAPI on Windows. The non-secret parts go to [KeyValueStore] so a signed in account can
 * be shown without decrypting anything.
 */
class TokenStore(
    private val secure: SecureStore,
    private val plain: KeyValueStore,
) {

    fun accountToken(): String? = secure.getSecret(StorageKeys.ACCOUNT_TOKEN)

    fun storeAccountToken(token: String, account: PlexAccount) {
        secure.putSecret(StorageKeys.ACCOUNT_TOKEN, token)
        plain.putString(StorageKeys.ACCOUNT_ID, account.id.toString())
        plain.putString(StorageKeys.ACCOUNT_USERNAME, account.title)
    }

    /** Replaces the account token after a Plex Home switch. */
    fun storeSwitchedToken(token: String, homeUserUuid: String) {
        secure.putSecret(StorageKeys.ACCOUNT_TOKEN, token)
        plain.putString(StorageKeys.HOME_USER_UUID, homeUserUuid)
    }

    fun signedInUsername(): String? = plain.getString(StorageKeys.ACCOUNT_USERNAME)

    fun isSignedIn(): Boolean = !accountToken().isNullOrBlank()

    /**
     * Sign out. Clears the token and the account, and deliberately leaves the client
     * identifier in place: it is bound to this installation, not to the account, and
     * regenerating it would duplicate the device entry on the next sign in.
     */
    fun signOut() {
        secure.removeSecret(StorageKeys.ACCOUNT_TOKEN)
        plain.remove(StorageKeys.ACCOUNT_ID)
        plain.remove(StorageKeys.ACCOUNT_USERNAME)
        plain.remove(StorageKeys.HOME_USER_UUID)
        plain.remove(StorageKeys.SELECTED_SERVER)
    }
}
