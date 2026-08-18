package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.screens.DetailScreen
import com.thotapalli.plex.ui.shared.screens.DownloadsScreen
import com.thotapalli.plex.ui.shared.screens.SettingsScreen
import com.thotapalli.plex.ui.shared.screens.HomeScreen
import com.thotapalli.plex.ui.shared.screens.HomeUserPicker
import com.thotapalli.plex.ui.shared.screens.LibraryScreen
import com.thotapalli.plex.ui.shared.screens.SearchScreen
import com.thotapalli.plex.ui.shared.screens.SignInScreen

/** The four destinations below the player. Nothing here is a discovery surface. */
enum class Destination(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings"),
}

/**
 * The application below the player, shared by phone, television and Windows.
 *
 * Each target supplies its own [AppContainer] and its own way of opening a browser and
 * starting playback. Everything else is identical, which is what keeps the three from
 * drifting apart.
 */
@Composable
fun PlexApp(
    container: AppContainer,
    viewModel: AppViewModel,
    onOpenUrl: (String) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.HOME) }

    LaunchedEffect(Unit) {
        viewModel.onOpenUrl = onOpenUrl
        viewModel.start()
    }

    WithSizeClass(isTelevision = container.isTelevision) { sizeClass ->
        ThotapalliTheme(sizeClass = sizeClass) {
            Box(modifier.fillMaxSize().background(PlexTheme.colours.background)) {
                when (state.phase) {
                    AppPhase.STARTING, AppPhase.CONNECTING ->
                        Centered(if (state.phase == AppPhase.STARTING) "Starting" else "Connecting")

                    AppPhase.SIGNED_OUT -> SignInScreen(
                        state = state.signIn,
                        onSignIn = viewModel::signIn,
                        onCancel = viewModel::signOut,
                        onOpenUrl = onOpenUrl,
                    )

                    AppPhase.PICKING_HOME_USER -> HomeUserPicker(
                        users = state.homeUsers,
                        onSelect = { viewModel.selectHomeUser(it) },
                    )

                    AppPhase.ERROR -> Centered(state.error ?: "Something went wrong.")

                    AppPhase.READY -> ReadyContent(
                        state = state,
                        viewModel = viewModel,
                        destination = destination,
                        onDestinationChange = { destination = it },
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: AppState,
    viewModel: AppViewModel,
    destination: Destination,
    onDestinationChange: (Destination) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
) {
    val server = state.server ?: return
    val sizeClass = PlexTheme.sizeClass

    val body: @Composable (Modifier) -> Unit = { bodyModifier ->
        when {
            state.detail != null -> DetailScreen(
                server = server,
                state = state.detail,
                onPlay = onPlay,
                onDownload = viewModel::download,
                onToggleWatched = { /* wired in phase 5 */ },
                onSeasonSelected = viewModel::selectSeason,
                modifier = bodyModifier,
            )

            state.library != null && destination == Destination.HOME -> LibraryScreen(
                server = server,
                state = state.library,
                onItemClick = viewModel::openDetail,
                onCollectionClick = viewModel::openCollection,
                onUnwatchedOnlyChange = viewModel::setUnwatchedOnly,
                onCloseCollection = viewModel::closeCollection,
                modifier = bodyModifier,
            )

            destination == Destination.SEARCH -> SearchScreen(
                server = server,
                state = state.search,
                onQueryChange = viewModel::onSearchQueryChanged,
                onItemClick = viewModel::openDetail,
                modifier = bodyModifier,
            )

            destination == Destination.DOWNLOADS -> DownloadsScreen(
                entries = state.downloads,
                totalBytesOnDisk = state.downloadBytesOnDisk,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onDelete = viewModel::deleteDownload,
                modifier = bodyModifier,
            )

            destination == Destination.SETTINGS -> SettingsScreen(
                state = viewModel.settingsState(),
                onMatchDisplayRateChange = viewModel::setMatchDisplayRate,
                onUnmeteredOnlyChange = viewModel::setUnmeteredOnly,
                onAudioLanguageChange = viewModel::setAudioLanguage,
                onSubtitleLanguageChange = viewModel::setSubtitleLanguage,
                onSubtitlesOnChange = viewModel::setSubtitlesOn,
                onSelectServer = viewModel::selectServer,
                onSignOut = viewModel::signOut,
                modifier = bodyModifier,
            )

            else -> HomeScreen(
                server = server,
                continueWatching = state.continueWatching,
                libraries = state.libraries,
                onItemClick = viewModel::openDetail,
                onLibraryClick = { library: Library -> viewModel.openLibrary(library) },
                modifier = bodyModifier,
            )
        }
    }

    // Compact uses a bottom bar, medium and expanded a side rail, television a top row.
    // See CLAUDE.md section 13.
    when (sizeClass.navigation) {
        com.thotapalli.plex.ui.design.NavigationStyle.BOTTOM_BAR -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { body(Modifier) }
            NavigationBar(destination, onDestinationChange, horizontal = true)
        }

        com.thotapalli.plex.ui.design.NavigationStyle.SIDE_RAIL -> Row(Modifier.fillMaxSize()) {
            NavigationBar(destination, onDestinationChange, horizontal = false)
            Box(Modifier.weight(1f)) { body(Modifier) }
        }

        com.thotapalli.plex.ui.design.NavigationStyle.TOP_ROW -> Column(Modifier.fillMaxSize()) {
            NavigationBar(destination, onDestinationChange, horizontal = true)
            Box(Modifier.weight(1f)) { body(Modifier) }
        }
    }
}

@Composable
private fun NavigationBar(
    current: Destination,
    onChange: (Destination) -> Unit,
    horizontal: Boolean,
) {
    val colours = PlexTheme.colours

    val entries: @Composable () -> Unit = {
        Destination.entries.forEach { entry ->
            val selected = entry == current
            Box(
                modifier = Modifier
                    .plexFocusable(shape = Radius.pill, onClick = { onChange(entry) }, scaleOnFocus = false)
                    .background(if (selected) colours.accent else colours.surface, Radius.pill)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                PlexText(
                    text = entry.label,
                    style = PlexTheme.type.label,
                    colour = when {
                        selected && colours.isDark -> colours.background
                        selected -> colours.surface
                        else -> colours.textSecondary
                    },
                )
            }
        }
    }

    if (horizontal) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colours.surface)
                .border(1.dp, colours.border)
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) { entries() }
    } else {
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(colours.surface)
                .border(1.dp, colours.border)
                .padding(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { entries() }
    }
}

@Composable
private fun Centered(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlexText(text = text, colour = PlexTheme.colours.textSecondary)
    }
}
