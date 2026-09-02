package com.thotapalli.plex.mobile

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.session.AndroidKeyValueStore
import com.thotapalli.plex.core.session.AndroidSecureStore
import com.thotapalli.plex.core.download.AndroidDownloadFileSystem
import com.thotapalli.plex.core.download.AndroidNetworkConditions
import com.thotapalli.plex.core.session.UpdateTarget
import com.thotapalli.plex.core.session.currentDeviceInfo
import com.thotapalli.plex.player.exo.ExoPlayerEngine
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.installImageLoader
import kotlinx.coroutines.launch

/**
 * Holds the one [AppContainer] for the process.
 *
 * On the application rather than the activity so a configuration change, which on a tablet
 * means every rotation, does not rebuild the HTTP client and the database.
 */
class ThotapalliApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Read on every network check rather than captured once, so toggling the setting takes
     * effect on the queue immediately instead of at the next launch.
     */
    private val settingsUnmeteredOnly: Boolean
        get() = if (::container.isInitialized) container.settings.unmeteredDownloadsOnly else true

    override fun onCreate() {
        super.onCreate()

        // Install the shared Coil loader (generous memory cache + 512 MB disk cache) before any
        // screen can request a poster, so artwork is cached from its first fetch. See CLAUDE.md
        // sections 5 and 13.
        installImageLoader()

        container = AppContainer(
            keyValueStore = AndroidKeyValueStore(this),
            secureStore = AndroidSecureStore(this),
            device = currentDeviceInfo(appVersion = BuildConfig.VERSION_NAME),
            driverFactory = DatabaseDriverFactory(this),
            isTelevision = isTelevision(),
            nowMs = System::currentTimeMillis,
            downloadFileSystem = AndroidDownloadFileSystem(this),
            networkConditions = AndroidNetworkConditions(this) { settingsUnmeteredOnly },
            updateTarget = UpdateTarget.MOBILE,
            currentVersionCode = BuildConfig.VERSION_CODE,
            updateManifestUrl = UPDATE_MANIFEST_URL,
        ).also {
            // "Download on unmetered networks only" defaults on for Android.
            // See CLAUDE.md section 11.
            it.settings.defaultUnmetered = true
        }

        observeDownloadsForForegroundService()
        observePlaybackForForegroundService()
    }

    /**
     * Holds the process in the foreground while a video plays, so a memory-constrained tablet does
     * not kill the app mid-playback and relaunch it — which loses the last progress report and
     * resumes an earlier point on return. Nothing else keeps the process alive once the activity is
     * backgrounded (PiP or screen-off), which is exactly when the system reclaims it.
     *
     * Started only once playback is actually playing ([ExoPlayerEngine.sessionActive]), never merely
     * when the session is built: a `mediaPlayback` foreground service started before its session has
     * a playing player cannot post its notification in the Android 14+ window and crashes the app.
     * By then Media3 posts the media notification immediately, so the service goes foreground in
     * time. Stopped when playback is released. Mirrors [observeDownloadsForForegroundService].
     */
    private fun observePlaybackForForegroundService() {
        var running = false
        container.scope.launch {
            ExoPlayerEngine.sessionActive.collect { active ->
                if (active && !running) {
                    running = true
                    runCatching {
                        ContextCompat.startForegroundService(
                            this@ThotapalliApplication,
                            Intent(this@ThotapalliApplication, PlaybackService::class.java),
                        )
                    }
                } else if (!active && running) {
                    running = false
                    runCatching {
                        stopService(Intent(this@ThotapalliApplication, PlaybackService::class.java))
                    }
                }
            }
        }
    }

    /**
     * A download must survive the app being backgrounded. Nothing else keeps the process alive while
     * no activity is showing, so the queue going busy is what starts [DownloadService], which
     * promotes the process to foreground for the duration.
     *
     * Observing the queue's active flow here (rather than at each enqueue call site) covers every
     * entry point that can start a download. The [downloadServiceRunning] latch keeps a burst of
     * non-null emissions — the queue stepping from one item to the next — from starting the service
     * repeatedly; the service itself mirrors progress and stops when the flow returns to null.
     */
    private fun observeDownloadsForForegroundService() {
        val queue = container.downloadQueue ?: return
        var downloadServiceRunning = false
        container.scope.launch {
            queue.active.collect { active ->
                if (active != null) {
                    if (!downloadServiceRunning) {
                        downloadServiceRunning = true
                        ContextCompat.startForegroundService(
                            this@ThotapalliApplication,
                            Intent(this@ThotapalliApplication, DownloadService::class.java),
                        )
                    }
                } else {
                    downloadServiceRunning = false
                }
            }
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

/**
 * The update manifest from CLAUDE.md section 17 point 3: a static JSON file at a fixed
 * release URL. Hosted on GitHub Releases, so no server is required.
 */
private const val UPDATE_MANIFEST_URL =
    "https://github.com/Aswin-Thotapalli/Thotapalli-Plex/releases/latest/download/update-manifest.json"
