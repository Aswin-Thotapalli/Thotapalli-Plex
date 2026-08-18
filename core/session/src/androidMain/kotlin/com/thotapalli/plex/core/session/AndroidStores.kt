package com.thotapalli.plex.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    private val prefs: SharedPreferences by lazy {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            app,
            NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getSecret(key: String): String? = prefs.getString(key, null)

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
    }
}
