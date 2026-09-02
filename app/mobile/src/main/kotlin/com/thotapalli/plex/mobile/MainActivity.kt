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
     * way, which is the requirement. Hiding the Compose control overlay while in PiP would need a
     * signal into the shared UI (a flag on AppViewModel/AppState), which lives in another module —
     * out of bounds for this change. TODO: expose an isInPip flag on AppViewModel so the overlay
     * can collapse to a bare surface while in PiP.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
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
    }
}
