package com.thotapalli.plex.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as ThotapalliApplication).container

        val viewModel: AppViewModel = ViewModelProvider(
            this,
            viewModelFactory { initializer { AppViewModel(container) } },
        )[AppViewModel::class.java]

        setContent {
            PlexApp(
                container = container,
                viewModel = viewModel,
                onOpenUrl = ::openUrl,
                onPlay = { _, _ ->
                    // The player arrives in phase 5.
                },
            )
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
}
