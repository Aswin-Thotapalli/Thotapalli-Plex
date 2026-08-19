package com.thotapalli.plex.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.download.WindowsDownloadFileSystem
import com.thotapalli.plex.core.download.WindowsNetworkConditions
import com.thotapalli.plex.core.session.DpapiSecureStore
import com.thotapalli.plex.core.session.FileKeyValueStore
import com.thotapalli.plex.core.session.UpdateTarget
import com.thotapalli.plex.core.session.currentDeviceInfo
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.AppState
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.PlexApp
import com.thotapalli.plex.ui.shared.player.PlayerOverlay
import com.thotapalli.plex.ui.shared.player.TrackSheet
import com.thotapalli.plex.ui.shared.player.TrackSheetKind
import java.awt.Desktop
import java.net.URI

private const val APP_VERSION = "0.1.0"
private const val APP_VERSION_CODE = 1

/**
 * The update manifest from CLAUDE.md section 17 point 3: a static JSON file at a fixed
 * release URL. Hosted on GitHub Releases, so no server is required.
 */
private const val UPDATE_MANIFEST_URL =
    "https://github.com/Aswin-Thotapalli/Thotapalli-Plex/releases/latest/download/update-manifest.json"

/**
 * Thotapalli Plex on Windows.
 *
 * The window drives the size class and is recomputed during the resize drag, which
 * [PlexApp] handles by measuring rather than by reading a value captured at start up.
 * See CLAUDE.md section 13.
 */
fun main() {
    ui()
}

private fun ui() = application {
    val container = remember { buildContainer() }
    val viewModel = remember { AppViewModel(container) }
    val appState by viewModel.state.collectAsState()

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    // The player's full-screen toggle drives the real window placement.
    LaunchedEffect(appState.isFullScreen) {
        windowState.placement =
            if (appState.isFullScreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    // The main window's AWT frame, so the floating overlay can track its content area.
    var mainWindow by remember { mutableStateOf<ComposeWindow?>(null) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Thotapalli Plex",
        state = windowState,
        // Escape pops the in-app stack (player, detail, library) the same way Back does on
        // Android, so the whole application is operable from the keyboard alone.
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                viewModel.back()
            } else {
                false
            }
        },
    ) {
        SideEffect { mainWindow = window }
        PlexApp(
            container = container,
            viewModel = viewModel,
            onOpenUrl = ::openBrowser,
            onPlay = viewModel::play,
        )
    }

    // The floating player controls. Because the video is a heavyweight native window the
    // Compose overlay cannot paint over, the controls live in their own transparent,
    // always-on-top window that tracks the main window's content area — so the toolbar
    // floats over the picture and fades away exactly like a normal video player.
    if (container.playerBridge.active) {
        PlayerOverlayWindow(container, viewModel, appState, mainWindow)
    }
}

@Composable
private fun ApplicationScope.PlayerOverlayWindow(
    container: AppContainer,
    viewModel: AppViewModel,
    appState: AppState,
    anchor: ComposeWindow?,
) {
    val overlayState = rememberWindowState(width = 960.dp, height = 540.dp)

    Window(
        onCloseRequest = viewModel::closePlayer,
        state = overlayState,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = true,
        title = "",
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                viewModel.closePlayer()
                true
            } else {
                false
            }
        },
    ) {
        val overlayWindow = window

        // Glue the overlay to the main window's content area (below its title bar), following
        // every move, resize and the switch to full screen, so the controls sit exactly over
        // the video while the title bar stays clickable.
        DisposableEffect(anchor, overlayWindow) {
            val a = anchor
            if (a == null) return@DisposableEffect onDispose {}
            fun sync() {
                val location = runCatching { a.contentPane.locationOnScreen }.getOrNull() ?: return
                val size = a.contentPane.size
                if (size.width > 0 && size.height > 0) {
                    overlayWindow.setBounds(location.x, location.y, size.width, size.height)
                }
            }
            sync()
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentMoved(e: java.awt.event.ComponentEvent?) = sync()
                override fun componentResized(e: java.awt.event.ComponentEvent?) = sync()
            }
            a.addComponentListener(listener)
            onDispose { a.removeComponentListener(listener) }
        }

        ThotapalliTheme(forceDark = true) {
            val playerState by container.playerBridge.stateFlow.collectAsState()
            // The window owns full screen, not the controller, so those two fields and the
            // toggle are supplied here rather than coming from the engine's state.
            val shown = playerState.copy(
                showFullScreenToggle = true,
                isFullScreen = appState.isFullScreen,
            )
            val overlayActions = container.playerBridge.actions.copy(
                onToggleFullScreen = viewModel::toggleFullScreen,
            )

            Box(
                Modifier
                    .fillMaxSize()
                    // Any pointer movement over the picture reveals the controls, the way a
                    // tap does; the events are not consumed, so the buttons still receive them.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                container.playerBridge.onActivity()
                            }
                        }
                    },
            ) {
                PlayerOverlay(state = shown, actions = overlayActions, modifier = Modifier.fillMaxSize())

                when (shown.openSheet) {
                    TrackSheetKind.AUDIO -> TrackSheet(
                        title = "Audio",
                        tracks = shown.audioTracks,
                        allowNone = false,
                        onSelect = overlayActions.onSelectAudioTrack,
                        onDismiss = overlayActions.onDismissSheet,
                        modifier = Modifier.fillMaxSize(),
                    )
                    TrackSheetKind.SUBTITLE -> TrackSheet(
                        title = "Subtitles",
                        tracks = shown.subtitleTracks,
                        allowNone = true,
                        onSelect = overlayActions.onSelectSubtitleTrack,
                        onDismiss = overlayActions.onDismissSheet,
                        modifier = Modifier.fillMaxSize(),
                    )
                    null -> Unit
                }
            }
        }
    }
}

private fun buildContainer(): AppContainer {
    // The network conditions need to read a setting that lives on the container, so the
    // reference is late-bound rather than captured: the lambda runs on every check, which
    // is also what makes toggling the setting take effect immediately.
    lateinit var container: AppContainer

    container = AppContainer(
        keyValueStore = FileKeyValueStore(),
        secureStore = DpapiSecureStore(),
        device = currentDeviceInfo(appVersion = APP_VERSION),
        driverFactory = DatabaseDriverFactory(),
        isTelevision = false,
        isDesktop = true,
        nowMs = System::currentTimeMillis,
        downloadFileSystem = WindowsDownloadFileSystem(),
        networkConditions = WindowsNetworkConditions { container.settings.unmeteredDownloadsOnly },
        updateTarget = UpdateTarget.DESKTOP,
        currentVersionCode = APP_VERSION_CODE,
        updateManifestUrl = UPDATE_MANIFEST_URL,
    )

    // "Download on unmetered networks only" defaults off for Windows, since the desktop has
    // no reliable metered signal to act on. See CLAUDE.md section 11 rule 6.
    container.settings.defaultUnmetered = false

    return container
}

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
