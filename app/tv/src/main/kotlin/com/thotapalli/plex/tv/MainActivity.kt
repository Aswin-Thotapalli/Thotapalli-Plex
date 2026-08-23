package com.thotapalli.plex.tv

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.PlexApp

/**
 * Thotapalli Plex on Android TV and Google TV.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
