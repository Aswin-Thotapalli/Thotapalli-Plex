package com.thotapalli.plex.mobile

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.PlexApp

/**
 * Thotapalli Plex on Android phone and tablet.
 *
 * The size class is measured from the window, so a tablet rotation changes the grid
 * column count without this activity doing anything. See CLAUDE.md section 13.
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    /** Mirrors the activity's Picture-in-Picture state into composition so the player overlay can
     *  collapse to a bare picture while in the PiP thumbnail. See [onPictureInPictureModeChanged]. */
    private var inPictureInPicture by mutableStateOf(false)

    // A single network transition (Wi-Fi to cellular, a VPN coming up) fires several
    // onLost/onAvailable/onCapabilitiesChanged in quick succession. They are coalesced into one
    // re-probe by cancelling and re-posting a delayed runnable, so the connection selection in
    // CLAUDE.md section 5 point 4 re-probes once per transition rather than per raw callback.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reprobe = Runnable { viewModel.onNetworkChanged() }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // The result is not acted on: playback and the foreground media service work whether or not the
    // permission is granted (the service posts its notification either way; denial just hides it). The
    // prompt exists so the lock-screen / notification transport controls can appear. See CLAUDE.md
    // section 8. Registered here (before the activity is started) as the Activity Result API requires.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        maybeRequestNotificationPermission()

        val container = (application as ThotapalliApplication).container

        viewModel = ViewModelProvider(
            this,
            viewModelFactory { initializer { AppViewModel(container) } },
        )[AppViewModel::class.java]

        // Process-death restore: the OS can reclaim a backgrounded process, then recreate this
        // activity with the breadcrumb saved in onSaveInstanceState (which survives that reclaim but
        // not an explicit swipe-away). Reopen the library/item the viewer was on. Best-effort — a
        // stale id just leaves them on Home. Not on a fresh launch (savedInstanceState is null then).
        savedInstanceState?.let { saved ->
            viewModel.restoreLocation(
                libraryKey = saved.getString(SAVED_LIBRARY_KEY),
                detailRatingKey = saved.getString(SAVED_DETAIL_RATING_KEY),
            )
        }

        // Hardware and gesture Back pop the in-app stack (player, detail, collection,
        // library) and only leave the app once there is nowhere left to go. See CLAUDE.md
        // section 13: Back never exits from below the home screen.
        onBackPressedDispatcher.addCallback(this) {
            if (!viewModel.back()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setContent {
            PlexApp(
                container = container,
                viewModel = viewModel,
                onOpenUrl = ::openUrl,
                onPlay = viewModel::play,
                isInPictureInPicture = inPictureInPicture,
            )
        }
    }

    /**
     * Register for OS connectivity changes while the app is in the foreground, so the server
     * connection is re-probed the moment the device's network changes. See CLAUDE.md section 5,
     * connection selection point 4: "Re-probe immediately on a device network change."
     *
     * The callback is bound to [mainHandler], so every callback — and therefore
     * [AppViewModel.onNetworkChanged] — is delivered on the main thread. There is no point
     * probing while backgrounded, so the callback lives on the started..stopped window and is
     * unregistered in [onStop], which also guarantees no leaked callback.
     */
    override fun onStart() {
        super.onStart()
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = cm
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleReprobe()
            override fun onLost(network: Network) = scheduleReprobe()
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = scheduleReprobe()
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback, mainHandler) }
    }

    override fun onStop() {
        networkCallback?.let { cb -> runCatching { connectivityManager?.unregisterNetworkCallback(cb) } }
        networkCallback = null
        mainHandler.removeCallbacks(reprobe)
        super.onStop()
    }

    /**
     * Saves the current in-app location so a process death can restore it. The Bundle survives the
     * OS reclaiming the process but not an explicit swipe-away, which is exactly the restore we want.
     * The player is deliberately not saved — resuming a video on relaunch is jarring, and the
     * server-side resume position brings the viewer back to the right spot when they replay.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::viewModel.isInitialized) return
        val state = viewModel.state.value
        state.library?.library?.key?.let { outState.putString(SAVED_LIBRARY_KEY, it) }
        state.detail?.item?.ratingKey?.let { outState.putString(SAVED_DETAIL_RATING_KEY, it) }
    }

    /** Coalesce a burst of connectivity callbacks into a single re-probe on the main thread. */
    private fun scheduleReprobe() {
        mainHandler.removeCallbacks(reprobe)
        mainHandler.postDelayed(reprobe, NETWORK_CHANGE_DEBOUNCE_MS)
    }

    /**
     * When the user leaves the app (Home button or the home gesture) while a video is playing,
     * fold the activity into a floating Picture-in-Picture window so playback continues. PiP is a
     * phone/tablet feature only; the television module never enters it. This is not casting — the
     * video keeps decoding on this device — so it stays in scope per CLAUDE.md section 1.
     *
     * "Playing" is read straight off the app state: a non-null [AppState.playback] means the
     * player screen is up with a source loaded. The existing SurfaceView (CLAUDE.md section 8)
     * keeps rendering in the PiP window unchanged; no TextureView is involved.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (viewModel.state.value.playback == null) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    // 16:9 is the common case and a safe default; the OS clamps to the allowed
                    // aspect range regardless. The true item aspect is not plumbed up to the
                    // activity, so it is not second-guessed here.
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    /**
     * PiP mode changes are delivered here. The player keeps rendering in the small window either
     * way; [inPictureInPicture] is mirrored into composition so the shared player collapses its
     * transport overlay to a bare picture while in the thumbnail (a PiP window is too small for
     * controls, and the system supplies its own). Leaving PiP flips it back and the overlay
     * returns with no reload. See [PlexApp]'s isInPictureInPicture.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    /**
     * Asks for the notification permission on Android 13+ (a runtime permission there), so the media
     * session's lock-screen / notification transport controls can be shown while a video plays. It is
     * requested once; if the user denies it, playback and the foreground media service still work —
     * the service posts its notification regardless, the system just does not display it. Below
     * Android 13 the permission is granted at install time, so nothing is asked. See CLAUDE.md
     * section 8 (request #2).
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    /**
     * The PIN approval page opens in the system browser rather than inside the app, which
     * is what lets an existing plex.tv session sign the user in without retyping anything.
     * See CLAUDE.md section 5.
     */
    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private companion object {
        /** Window over which rapid connectivity callbacks collapse into one re-probe. */
        const val NETWORK_CHANGE_DEBOUNCE_MS = 800L

        /** Saved-instance-state keys for the process-death location restore. */
        const val SAVED_LIBRARY_KEY = "thotapalli.saved.libraryKey"
        const val SAVED_DETAIL_RATING_KEY = "thotapalli.saved.detailRatingKey"
    }
}
