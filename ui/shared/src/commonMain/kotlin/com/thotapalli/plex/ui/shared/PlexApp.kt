package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.material.glass
import com.thotapalli.plex.ui.shared.player.PlayerScreen
import com.thotapalli.plex.ui.shared.screens.DetailScreen
import com.thotapalli.plex.ui.shared.screens.DownloadsScreen
import com.thotapalli.plex.ui.shared.screens.HomeScreen
import com.thotapalli.plex.ui.shared.screens.HomeUserPicker
import com.thotapalli.plex.ui.shared.screens.LibraryScreen
import com.thotapalli.plex.ui.shared.screens.SearchScreen
import com.thotapalli.plex.ui.shared.screens.SettingsScreen
import com.thotapalli.plex.ui.shared.screens.SignInScreen

/** The four destinations below the player. Nothing here is a discovery surface. */
enum class Destination(val label: String, val icon: PlexIconKind) {
    HOME("Home", PlexIconKind.HOME),
    SEARCH("Search", PlexIconKind.SEARCH),
    DOWNLOADS("Downloads", PlexIconKind.DOWNLOADS),
    SETTINGS("Settings", PlexIconKind.SETTINGS),
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
                        LoadingScreen(if (state.phase == AppPhase.STARTING) "Starting" else "Connecting")

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

                    AppPhase.ERROR -> ErrorScreen(state.error ?: "Something went wrong.")

                    AppPhase.READY -> ReadyContent(
                        state = state,
                        viewModel = viewModel,
                        destination = destination,
                        onDestinationChange = { destination = it },
                        onPlay = onPlay,
                    )
                }

                // The full-screen player sits above everything, including the navigation, so
                // it is truly full bleed. Its own back and the hardware back both leave it.
                val playback = state.playback
                if (playback != null) {
                    PlayerScreen(
                        container = container,
                        item = playback.item,
                        serverScope = playback.serverScope,
                        urls = playback.urls,
                        startAtMs = playback.startAtMs,
                        onExit = viewModel::closePlayer,
                        modifier = Modifier.fillMaxSize(),
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

    // A drill-in is anything with somewhere to go back to below a top-level destination.
    val canGoBack = state.detail != null ||
        state.library?.openCollection != null ||
        (state.library != null && destination == Destination.HOME)

    val body: @Composable (Modifier) -> Unit = { bodyModifier ->
        when {
            state.detail != null -> DetailScreen(
                server = server,
                state = state.detail,
                onPlay = onPlay,
                onDownload = viewModel::download,
                onToggleWatched = viewModel::toggleWatched,
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
                onPlay = onPlay,
                modifier = bodyModifier,
            )
        }
    }

    // The body carries a floating back control when there is somewhere to go, drawn over the
    // content so a cinematic backdrop bleeds to the top edge behind it.
    val bodyWithBack: @Composable (Modifier) -> Unit = { m ->
        Box(m.fillMaxSize()) {
            body(Modifier.fillMaxSize())
            if (canGoBack) {
                TopBarIconButton(
                    kind = PlexIconKind.BACK,
                    onClick = viewModel::back,
                    scrim = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(Spacing.sm),
                )
            }
        }
    }

    // Compact uses a bottom bar, medium and expanded a side rail, television a top row.
    // See CLAUDE.md section 13. Insets keep the chrome clear of the S26 cutout and gesture bar.
    when (sizeClass.navigation) {
        com.thotapalli.plex.ui.design.NavigationStyle.BOTTOM_BAR -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { bodyWithBack(Modifier) }
            NavigationBar(destination, onDestinationChange, horizontal = true)
        }

        com.thotapalli.plex.ui.design.NavigationStyle.SIDE_RAIL -> Row(Modifier.fillMaxSize()) {
            NavigationBar(destination, onDestinationChange, horizontal = false)
            Box(Modifier.weight(1f)) { bodyWithBack(Modifier) }
        }

        com.thotapalli.plex.ui.design.NavigationStyle.TOP_ROW -> Column(Modifier.fillMaxSize()) {
            NavigationBar(destination, onDestinationChange, horizontal = true)
            Box(Modifier.weight(1f)) { bodyWithBack(Modifier) }
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
            NavItem(entry = entry, selected = entry == current, onClick = { onChange(entry) })
        }
    }

    if (horizontal) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = androidx.compose.ui.graphics.RectangleShape)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) { entries() }
    } else {
        Column(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .glass(shape = androidx.compose.ui.graphics.RectangleShape)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { entries() }
    }
}

/** A navigation entry: icon over label, with an accent tint and pill when selected. */
@Composable
private fun NavItem(entry: Destination, selected: Boolean, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    val tint = if (selected) colours.accent else colours.textSecondary

    Column(
        modifier = Modifier
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.card)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlexIcon(kind = entry.icon, tint = tint, size = 26.dp)
        Spacer(Modifier.height(Spacing.xxs))
        PlexText(
            text = entry.label,
            style = PlexTheme.type.caption,
            colour = tint,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Box(
            Modifier
                .size(width = 18.dp, height = 3.dp)
                .clip(Radius.pill)
                .background(if (selected) colours.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun LoadingScreen(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(label = label)
    }
}

@Composable
private fun ErrorScreen(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        PlexText(text = text, colour = PlexTheme.colours.textSecondary)
    }
}
