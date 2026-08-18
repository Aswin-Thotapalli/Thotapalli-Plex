package com.thotapalli.plex.core.session

/**
 * Plain persistent storage. Holds the client identifier and the selected server, neither of
 * which is a secret. Tokens go to [SecureStore] instead and never here.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

/**
 * Encrypted persistent storage for tokens.
 *
 * Android backs this with EncryptedSharedPreferences. Windows backs it with DPAPI through
 * CryptProtectData scoped to the current user. See CLAUDE.md section 5 step 4.
 */
interface SecureStore {
    fun getSecret(key: String): String?
    fun putSecret(key: String, value: String)
    fun removeSecret(key: String)
    fun clear()
}

object StorageKeys {
    /** Generated once on first launch and persisted forever. Changing it duplicates the device. */
    const val CLIENT_IDENTIFIER = "client_identifier"
    const val ACCOUNT_TOKEN = "account_token"
    const val ACCOUNT_ID = "account_id"
    const val ACCOUNT_USERNAME = "account_username"
    const val SELECTED_SERVER = "selected_server"
    const val HOME_USER_UUID = "home_user_uuid"
}
