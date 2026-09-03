package com.thotapalli.plex.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.download.WindowsDownloadFileSystem
import com.thotapalli.plex.core.download.WindowsNetworkConditions
import com.thotapalli.plex.core.session.DpapiSecureStore
import com.thotapalli.plex.core.session.FileKeyValueStore
import com.thotapalli.plex.core.session.UpdateTarget
import com.thotapalli.plex.core.session.currentDeviceInfo
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.installImageLoader
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.PlexApp
import com.thotapalli.plex.ui.shared.input.PlayerKeyAction
import com.thotapalli.plex.ui.shared.input.keyToPlayerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.NetworkInterface
import java.net.URI

// From gradle.properties via the generated BuildInfo, so the reported version and the update-check
// version code always match the release the build produced. See app/desktop/build.gradle.kts.
private val APP_VERSION = BuildInfo.VERSION_NAME
private val APP_VERSION_CODE = BuildInfo.VERSION_CODE

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
    // Install the shared Coil loader (generous memory cache + 512 MB disk cache under
    // %LOCALAPPDATA%\ThotapalliPlex\image_cache) before the Compose window opens and any poster is
    // requested, so artwork is cached from its first fetch. See CLAUDE.md sections 5 and 13.
    installImageLoader()
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

    // Windows has no ConnectivityManager, so this is a best-effort stand-in for the OS
    // network-change signal the Android apps get for free. Every 30s the set of up, non-loopback
    // network interfaces (name + bound addresses) is snapshotted; when it differs from the last
    // snapshot the device's network has changed, so the server connection is re-probed. See
    // CLAUDE.md section 5, connection selection point 4. The first snapshot is taken without
    // firing — startup already probes — and the poll runs off the UI thread so it never blocks
    // rendering or startup.
    LaunchedEffect(viewModel) {
        var last = withContext(Dispatchers.IO) { upInterfaceSignature() }
        while (true) {
            delay(30_000)
            val current = withContext(Dispatchers.IO) { upInterfaceSignature() }
            if (current != last) {
                last = current
                viewModel.onNetworkChanged()
            }
        }
    }

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
        // The main window owns the keyboard. While a video is playing it drives the player through
        // the shared [keyToPlayerAction] map (so phone, TV and Windows agree on each key), because
        // the controls window is non-focusable and often hidden. Otherwise Escape pops the in-app
        // stack (detail, library) the way Back does on Android.
        onKeyEvent = { event ->
            val bridge = container.playerBridge
            if (bridge.active) {
                when (keyToPlayerAction(event)) {
                    PlayerKeyAction.PLAY_PAUSE -> { bridge.actions.onPlayPause(); true }
                    PlayerKeyAction.SEEK_BACK -> { bridge.actions.onSeekBack(); true }
                    PlayerKeyAction.SEEK_FORWARD -> { bridge.actions.onSeekForward(); true }
                    PlayerKeyAction.TOGGLE_FULL_SCREEN -> { viewModel.toggleFullScreen(); true }
                    PlayerKeyAction.BACK -> { viewModel.closePlayer(); true }
                    PlayerKeyAction.CYCLE_SUBTITLES -> { bridge.actions.onOpenSubtitleTracks(); true }
                    PlayerKeyAction.CYCLE_AUDIO -> { bridge.actions.onOpenAudioTracks(); true }
                    null ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            viewModel.back(); true
                        } else {
                            false
                        }
                }
            } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                viewModel.back(); true
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

    // No separate controls window any more: the desktop player is a single window in which mpv
    // renders the video into a GL framebuffer and the overlay is composited straight over it (see
    // VideoSurface.jvm). The main window still owns the keyboard shortcuts below, reaching the live
    // player actions through the bridge; the bridge no longer carries an overlay.
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

    // The Windows single-window player never switches the display mode: the video is composited into
    // an embedded OpenGL surface that a Windows refresh-rate change would destroy (the picture goes
    // black, only audio survives) — the exact opposite of what a media player should do. So section
    // 9's display-rate matching is not wired on the desktop; drift is handled by mpv's
    // video-sync=display-resample plus the vsync'd swap, and the "Match display rate" setting is
    // hidden on Windows (see AppViewModel.settingsState). Android TV keeps section 9 through Media3,
    // where the platform changes the mode cleanly.
    return container
}

/**
 * A stable fingerprint of the machine's currently usable network interfaces: for every interface
 * that is up and not loopback, its name plus each bound IP address. A change in this set between
 * polls means an adapter went up or down, a cable was plugged, Wi-Fi switched, or a VPN connected
 * or dropped — i.e. a device network change worth re-probing on. Any failure yields an empty set
 * rather than throwing, so the poll can never crash the app.
 */
private fun upInterfaceSignature(): Set<String> = runCatching {
    val out = mutableSetOf<String>()
    val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching emptySet()
    for (iface in ifaces) {
        if (!iface.isUp || iface.isLoopback) continue
        for (addr in iface.inetAddresses) {
            out += "${iface.name}|${addr.hostAddress}"
        }
    }
    out
}.getOrDefault(emptySet())

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
