package com.thotapalli.plex.core.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/** Plain preferences. Client identifier and selected server, no secrets. */
class AndroidKeyValueStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val NAME = "thotapalli_plex"
    }
}

/**
 * The account token, held in EncryptedSharedPreferences behind a master key in the
 * Android keystore. See CLAUDE.md section 5 step 4.
 *
 * EncryptedSharedPreferences and MasterKey are deprecated as of androidx.security-crypto
 * 1.1.0: Google has stopped developing the library and points at the Android keystore
 * directly. It is still shipped and still works, and section 5 names it, so it stays.
 * The suppression is here rather than at each call so the deprecation shows up once, in
 * the one place that has to change if the library is ever removed.
 */
@Suppress("DEPRECATION")
class AndroidSecureStore(context: Context) : SecureStore {

    private val app = context.applicationContext

    private val prefs: SharedPreferences by lazy { openPrefs() }

    /**
     * Opens the encrypted store, recovering from a corrupted keystore key or file rather than
     * crashing every launch.
     *
     * After a device restore, a keystore reset, or plain keystore corruption (a documented Android
     * failure mode), [EncryptedSharedPreferences.create] throws and would take the app down on the
     * first token read on every launch, with no way out. The token is disposable — the user signs in
     * again — so a failure clears both the encrypted file and the master key and rebuilds from clean.
     */
    private fun openPrefs(): SharedPreferences =
        runCatching { createEncrypted() }.getOrElse { first ->
            Log.w(TAG, "encrypted store unreadable; clearing and rebuilding", first)
            runCatching { app.deleteSharedPreferences(NAME) }
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    .takeIf { it.containsAlias(MASTER_KEY_ALIAS) }
                    ?.deleteEntry(MASTER_KEY_ALIAS)
            }
            createEncrypted()
        }

    private fun createEncrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            app,
            NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // A single unreadable value (one entry whose GCM tag no longer verifies) should read as absent,
    // not throw — the caller treats a null token as signed out and recovers, where an exception would
    // propagate out of a launch-time read.
    override fun getSecret(key: String): String? =
        runCatching { prefs.getString(key, null) }.getOrNull()

    override fun putSecret(key: String, value: String) {
        // commit rather than apply: a token that is written and then lost to a process
        // death leaves the user signed out with no way to tell why.
        prefs.edit().putString(key, value).commit()
    }

    override fun removeSecret(key: String) {
        prefs.edit().remove(key).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val NAME = "thotapalli_plex_secure"
        const val TAG = "ThotapalliSecureStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        // The fixed alias androidx.security-crypto's MasterKey.DEFAULT_MASTER_KEY_ALIAS uses.
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    }
}
