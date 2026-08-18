package com.thotapalli.plex.mobile

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.session.AndroidKeyValueStore
import com.thotapalli.plex.core.session.AndroidSecureStore
import com.thotapalli.plex.core.session.currentDeviceInfo
import com.thotapalli.plex.ui.shared.AppContainer

/**
 * Holds the one [AppContainer] for the process.
 *
 * On the application rather than the activity so a configuration change, which on a tablet
 * means every rotation, does not rebuild the HTTP client and the database.
 */
class ThotapalliApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(
            keyValueStore = AndroidKeyValueStore(this),
            secureStore = AndroidSecureStore(this),
            device = currentDeviceInfo(appVersion = BuildConfig.VERSION_NAME),
            driverFactory = DatabaseDriverFactory(this),
            isTelevision = isTelevision(),
            nowMs = System::currentTimeMillis,
        ).also {
            // "Download on unmetered networks only" defaults on for Android.
            // See CLAUDE.md section 11.
            it.settings.defaultUnmetered = true
        }
    }

    /**
     * Television is detected rather than assumed, because the phone build can be sideloaded
     * onto a television and tunnelling and the type scale both depend on knowing.
     * See CLAUDE.md section 8.
     */
    private fun isTelevision(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
