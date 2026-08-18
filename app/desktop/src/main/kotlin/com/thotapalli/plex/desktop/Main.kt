package com.thotapalli.plex.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.session.DpapiSecureStore
import com.thotapalli.plex.core.session.FileKeyValueStore
import com.thotapalli.plex.core.session.currentDeviceInfo
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.PlexApp
import java.awt.Desktop
import java.net.URI

private const val APP_VERSION = "0.1.0"

/**
 * Thotapalli Plex on Windows.
 *
 * The window drives the size class and is recomputed during the resize drag, which
 * [PlexApp] handles by measuring rather than by reading a value captured at start up.
 * See CLAUDE.md section 13.
 */
fun main() = application {
    val container = remember {
        AppContainer(
            keyValueStore = FileKeyValueStore(),
            secureStore = DpapiSecureStore(),
            device = currentDeviceInfo(appVersion = APP_VERSION),
            driverFactory = DatabaseDriverFactory(),
            isTelevision = false,
            nowMs = System::currentTimeMillis,
        ).also {
            // "Download on unmetered networks only" defaults off for Windows.
            // See CLAUDE.md section 11.
            it.settings.defaultUnmetered = false
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Thotapalli Plex",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        val viewModel = remember { AppViewModel(container) }

        PlexApp(
            container = container,
            viewModel = viewModel,
            onOpenUrl = ::openBrowser,
            onPlay = { _, _ ->
                // The player arrives in phase 5.
            },
        )
    }
}

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
