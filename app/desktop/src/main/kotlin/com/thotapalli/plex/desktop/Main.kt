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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
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
    // In case the process dies while full screen, put the window frame styles back is moot, but
    // any taskbar we hid as a fallback must be shown again.
    Runtime.getRuntime().addShutdownHook(Thread { BorderlessFullscreen.showTaskbar() })
    ui()
}

/**
 * True borderless full screen on Windows, driven directly through Win32.
 *
 * On entry the window's title bar and resize frame styles are removed and the window is sized to
 * exactly cover the monitor it is on. Windows recognises a borderless window that fills a whole
 * monitor as a full-screen window and hides the taskbar for it automatically — the reliable
 * mechanism, where hiding the taskbar by hand kept losing to the shell re-showing it. On exit the
 * original styles and bounds are restored, and Windows brings the taskbar back on its own.
 *
 * As a belt-and-suspenders measure the taskbar is also hidden and restored explicitly.
 */
private object BorderlessFullscreen {
    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_NOZORDER = 0x0004
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val SW_HIDE = 0
    private const val SW_SHOW = 5

    private var savedStyle: Int? = null
    private var savedRect: com.sun.jna.platform.win32.WinDef.RECT? = null

    private val user32 get() = com.sun.jna.platform.win32.User32.INSTANCE

    private fun hwnd(window: java.awt.Window): com.sun.jna.platform.win32.WinDef.HWND? =
        runCatching {
            com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getWindowPointer(window))
        }.getOrNull()

    fun enter(window: java.awt.Window) {
        try {
            val h = hwnd(window) ?: return
            val cur = com.sun.jna.platform.win32.WinDef.RECT()
            user32.GetWindowRect(h, cur)
            savedRect = cur
            val style = user32.GetWindowLong(h, GWL_STYLE)
            savedStyle = style
            user32.SetWindowLong(h, GWL_STYLE, style and (WS_CAPTION or WS_THICKFRAME).inv())

            val mon = user32.MonitorFromWindow(h, MONITOR_DEFAULTTONEAREST)
            val mi = com.sun.jna.platform.win32.WinUser.MONITORINFO()
            user32.GetMonitorInfo(mon, mi)
            val r = mi.rcMonitor
            user32.SetWindowPos(h, null, r.left, r.top, r.right - r.left, r.bottom - r.top, SWP_FRAMECHANGED or SWP_NOZORDER)
            setTaskbar(false)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun exit(window: java.awt.Window) {
        try {
            val h = hwnd(window) ?: return
            savedStyle?.let { user32.SetWindowLong(h, GWL_STYLE, it) }
            savedRect?.let { r ->
                user32.SetWindowPos(h, null, r.left, r.top, r.right - r.left, r.bottom - r.top, SWP_FRAMECHANGED or SWP_NOZORDER)
            }
            savedStyle = null
            savedRect = null
            setTaskbar(true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun showTaskbar() = setTaskbar(true)

    private fun setTaskbar(visible: Boolean) {
        runCatching {
            val cmd = if (visible) SW_SHOW else SW_HIDE
            listOf("Shell_TrayWnd", "Shell_SecondaryTrayWnd").forEach { cls ->
                user32.FindWindow(cls, null)?.let { user32.ShowWindow(it, cmd) }
            }
        }
    }
}

private fun ui() = application {
    val container = remember { buildContainer() }
    val viewModel = remember { AppViewModel(container) }
    val appState by viewModel.state.collectAsState()

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    // The main window's AWT frame, so the floating overlay can track its content area.
    var mainWindow by remember { mutableStateOf<ComposeWindow?>(null) }

    // Full screen, done as true borderless full screen through Win32.
    //
    // Earlier attempts fought the wrong battle: WindowPlacement.Fullscreen captures the display
    // and hides the controls window; growing a decorated window over the taskbar and hiding the
    // taskbar with ShowWindow left the taskbar re-appearing on window activation. The reliable
    // way — the one games use — is to strip the window's title bar and resize frame and size it
    // to exactly the monitor. Windows then recognises it as a full-screen window and hides the
    // taskbar itself, restoring it when the window leaves that state. Nothing captures the
    // display, so the separate controls window keeps floating on top.
    LaunchedEffect(appState.isFullScreen, mainWindow) {
        val w = mainWindow ?: return@LaunchedEffect
        if (appState.isFullScreen) BorderlessFullscreen.enter(w) else BorderlessFullscreen.exit(w)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Thotapalli Plex",
        icon = painterResource("icon.png"),
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
    val overlayState = rememberWindowState(
        position = WindowPosition(0.dp, 0.dp),
        width = 960.dp,
        height = 540.dp,
    )

    // Mirror the main window's content area into the overlay's WindowState so the controls
    // cover the picture exactly, following every move, resize and the switch to full screen.
    //
    // The bounds are pushed through WindowState rather than window.setBounds because Compose
    // re-applies the state's own bounds on every frame and would otherwise snap the overlay
    // back to its initial size. AWT reports component bounds in logical pixels, and Compose's
    // density on this display makes one logical pixel equal one dp, so the numbers map across
    // directly.
    if (anchor != null) {
        DisposableEffect(anchor) {
            fun sync() {
                val loc = runCatching { anchor.contentPane.locationOnScreen }.getOrNull()
                    ?: return
                val size = anchor.contentPane.size
                if (size.width > 0 && size.height > 0) {
                    overlayState.position = WindowPosition(loc.x.dp, loc.y.dp)
                    overlayState.size = DpSize(size.width.dp, size.height.dp)
                }
            }
            sync()
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentMoved(e: java.awt.event.ComponentEvent?) = sync()
                override fun componentResized(e: java.awt.event.ComponentEvent?) = sync()
            }
            anchor.addComponentListener(listener)
            onDispose { anchor.removeComponentListener(listener) }
        }
    }

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
        // In full screen the main window is raised above the taskbar (top-most), so the controls
        // window must re-assert itself above it to stay visible and clickable.
        val overlayWindow = window
        LaunchedEffect(appState.isFullScreen) {
            if (appState.isFullScreen) {
                overlayWindow.isAlwaysOnTop = true
                overlayWindow.toFront()
            }
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
