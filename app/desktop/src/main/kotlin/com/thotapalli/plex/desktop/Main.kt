package com.thotapalli.plex.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.PlexApp
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
    // Lets Compose content (the player overlay) composite over the embedded heavyweight mpv
    // video canvas instead of being hidden behind it.
    System.setProperty("compose.interop.blending", "true")
    ui()
}

private fun ui() = application {
    val container = remember { buildContainer() }
    val viewModel = remember { AppViewModel(container) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Thotapalli Plex",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
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
        PlexApp(
            container = container,
            viewModel = viewModel,
            onOpenUrl = ::openBrowser,
            onPlay = viewModel::play,
        )
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
