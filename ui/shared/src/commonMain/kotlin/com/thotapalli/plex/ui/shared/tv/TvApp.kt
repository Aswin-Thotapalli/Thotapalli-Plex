package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.AppPhase
import com.thotapalli.plex.ui.shared.AppViewModel
import com.thotapalli.plex.ui.shared.BrandSplash
import com.thotapalli.plex.ui.shared.Destination
import com.thotapalli.plex.ui.shared.ImmersiveSystemBars
import com.thotapalli.plex.ui.shared.WithSizeClass
import com.thotapalli.plex.ui.shared.player.LocalPlaybackTuning
import com.thotapalli.plex.ui.shared.player.rememberPlaybackTuning
import kotlinx.coroutines.delay

/**
 * The television application root. Built for one input — a remote — on the focus contract in
 * TvFocus.kt, and sharing nothing above the view model with the phone, tablet or desktop app:
 * the screens that navigate with a pointer or a thumb are the wrong screens for a D-pad, however
 * many `if (isTv)` branches they carry. See CLAUDE.md sections 13 and 18 item 5.
 */
@Composable
fun TvApp(
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

    WithSizeClass(isTelevision = true) { sizeClass ->
        // The ten-foot UI is dark by design: the palette is tuned for a living room.
        ThotapalliTheme(sizeClass = sizeClass, forceDark = true) {
            Box(modifier.fillMaxSize().background(TvPalette.ground)) {
                when (state.phase) {
                    AppPhase.STARTING, AppPhase.CONNECTING ->
                        TvEmpty(if (state.phase == AppPhase.STARTING) "Starting…" else "Connecting…")

                    AppPhase.SIGNED_OUT -> TvSignIn(
                        state = state.signIn,
                        onSignIn = viewModel::signIn,
                        onCancel = viewModel::signOut,
                    )

                    AppPhase.PICKING_HOME_USER -> TvHomeUserPicker(
                        users = state.homeUsers,
                        onSelect = { user, pin -> viewModel.selectHomeUser(user, pin) },
                    )

                    AppPhase.ERROR -> TvEmpty("Something went wrong", state.error)

                    AppPhase.READY -> TvReady(
                        state = state,
                        viewModel = viewModel,
                        destination = destination,
                        onDestinationChange = { destination = it },
                        onPlay = onPlay,
                    )
                }

                // The player sits above everything and owns the remote while it is up.
                val playback = state.playback
                ImmersiveSystemBars(hidden = playback != null)
                if (playback != null) {
                    val tuning = rememberPlaybackTuning(container, state.settingsRevision)
                    CompositionLocalProvider(LocalPlaybackTuning provides tuning) {
                    TvPlayer(
                        container = container,
                        item = playback.item,
                        serverScope = playback.serverScope,
                        urls = playback.urls,
                        startAtMs = playback.startAtMs,
                        onExit = viewModel::closePlayer,
                        networkRegained = viewModel.networkRegained,
                        modifier = Modifier.fillMaxSize(),
                    )
                    }
                }

                // A transient result of an action, as a quiet toast no remote has to dismiss.
                TvToast(message = state.notice, onShown = viewModel::dismissNotice, visible = playback == null)

                var splashDone by remember { mutableStateOf(false) }
                if (!splashDone) {
                    BrandSplash(
                        ready = state.phase == AppPhase.READY,
                        onFinished = { splashDone = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvReady(
    state: com.thotapalli.plex.ui.shared.AppState,
    viewModel: AppViewModel,
    destination: Destination,
    onDestinationChange: (Destination) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
) {
    val server = state.server ?: return

    // The item whose menu is open (a held select on a card), and whether it came from Continue
    // Watching, which adds the remove action.
    var menu by remember { mutableStateOf<Pair<MediaItem, Boolean>?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    // A screen's own dialog (a download's actions, a settings choice) also takes the remote.
    var screenDialog by remember { mutableStateOf(false) }
    val dialogOpen = menu != null || confirmSignOut || screenDialog

    // Selecting a rail destination leaves whatever was open: a detail, a library, a collection.
    // Home and Library are their own contexts; the other tabs simply switch.
    val onSelect: (Destination) -> Unit = { chosen ->
        viewModel.closeDetail()
        when (chosen) {
            Destination.HOME -> { viewModel.closeLibrary(); onDestinationChange(Destination.HOME) }
            Destination.LIBRARY -> { viewModel.closeLibrary(); onDestinationChange(Destination.LIBRARY) }
            else -> onDestinationChange(chosen)
        }
    }

    TvShell(
        current = destination,
        onSelect = onSelect,
        accountName = state.accountName,
        focusEnabled = state.playback == null && !dialogOpen,
        modifier = Modifier.onPreviewKeyEvent { event ->
            // Back from a top-level tab returns to Home before the activity is allowed to pop the
            // app's own stack (detail, collection, library) and, from Home, exit. See CLAUDE.md §13.
            val isBack = event.key == Key.Back || event.key == Key.Escape
            val atTopLevel = state.detail == null && state.library == null
            if (isBack && atTopLevel && destination != Destination.HOME) {
                if (event.type == KeyEventType.KeyUp) onDestinationChange(Destination.HOME)
                true
            } else {
                false
            }
        },
    ) {
        when {
            state.detail != null -> TvDetail(
                server = server,
                state = state.detail,
                onPlay = onPlay,
                onDownload = viewModel::download,
                onToggleWatched = viewModel::toggleWatched,
                onSeasonSelected = viewModel::selectSeason,
                onSelectEpisode = viewModel::selectEpisode,
                onSetContainerWatched = viewModel::setContainerWatched,
                onItemMenu = { item -> menu = item to false },
            )
            destination == Destination.SEARCH -> TvSearch(
                server = server,
                state = state.search,
                onQueryChange = viewModel::onSearchQueryChanged,
                onItemClick = viewModel::openDetail,
            )
            destination == Destination.DOWNLOADS -> TvDownloads(
                entries = state.downloads,
                totalBytesOnDisk = state.downloadBytesOnDisk,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onDelete = viewModel::deleteDownload,
                onPlayDownload = { entry -> viewModel.playDownload(entry) { onPlay(it, it.viewOffsetMs) } },
                onDialogOpen = { screenDialog = it },
            )
            destination == Destination.SETTINGS -> TvSettings(
                state = viewModel.settingsState(),
                onMatchDisplayRateChange = viewModel::setMatchDisplayRate,
                onTunnelledPlaybackChange = viewModel::setTunnelledPlayback,
                onAudioPassthroughChange = viewModel::setAudioPassthrough,
                onUnmeteredOnlyChange = viewModel::setUnmeteredOnly,
                onAudioLanguageChange = viewModel::setAudioLanguage,
                onSubtitleLanguageChange = viewModel::setSubtitleLanguage,
                onSubtitlesOnChange = viewModel::setSubtitlesOn,
                onStreamingBitrateChange = viewModel::setStreamingBitrate,
                onSubtitleScaleChange = viewModel::setSubtitleScale,
                onSelectServer = viewModel::selectServer,
                onSignOut = { confirmSignOut = true },
                serverUpdate = state.serverUpdate,
                serverUpdateApplying = state.serverUpdateApplying,
                onCheckServerUpdate = viewModel::checkServerUpdate,
                onApplyServerUpdate = viewModel::applyServerUpdate,
                onDialogOpen = { screenDialog = it },
            )
            state.library != null -> TvLibrary(
                server = server,
                state = state.library,
                onItemClick = viewModel::openDetail,
                onCollectionClick = viewModel::openCollection,
                onUnwatchedOnlyChange = viewModel::setUnwatchedOnly,
                onCloseCollection = viewModel::closeCollection,
                onItemMenu = { item -> menu = item to false },
            )
            destination == Destination.LIBRARY -> TvLibraries(
                libraries = state.libraries,
                onOpenLibrary = { library: Library -> viewModel.openLibrary(library) },
            )
            else -> TvHome(
                server = server,
                continueWatching = state.continueWatching,
                libraries = state.libraries,
                libraryPreviews = state.libraryPreviews,
                onItemClick = viewModel::openDetail,
                onOpenLibrary = { library: Library -> viewModel.openLibrary(library) },
                onPlay = onPlay,
                onItemMenu = { item, fromContinueWatching -> menu = item to fromContinueWatching },
            )
        }
    }

    menu?.let { (item, fromContinueWatching) ->
        TvMenuDialog(
            title = item.title,
            onDismiss = { menu = null },
            actions = buildList {
                add(TvMenuAction(if (item.viewCount > 0) "Mark unwatched" else "Mark watched") {
                    viewModel.setWatched(item, item.viewCount == 0)
                })
                add(TvMenuAction("Download") { viewModel.download(item) })
                if (fromContinueWatching) {
                    add(TvMenuAction("Remove from Continue Watching") { viewModel.removeFromContinueWatching(item) })
                }
                add(TvMenuAction("Refresh metadata") { viewModel.refreshItemMetadata(item) })
            },
        )
    }
    if (confirmSignOut) {
        TvConfirmDialog(
            title = "Sign out?",
            body = "You will need to approve this device again to sign back in.",
            confirmLabel = "Sign out",
            destructive = true,
            onConfirm = viewModel::signOut,
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** A quiet notice, bottom centre, gone on its own. Never a target, never in the way. */
@Composable
private fun TvToast(message: String?, onShown: () -> Unit, visible: Boolean) {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        shown = message
        delay(TOAST_MS)
        shown = null
        onShown()
    }
    val text = shown ?: return
    if (!visible) return
    Box(Modifier.fillMaxSize().padding(bottom = TvDims.overscanY + Spacing.lg), contentAlignment = Alignment.BottomCenter) {
        TvPanel(shape = TvShape.pill) {
            PlexText(
                text = text,
                style = PlexTheme.type.label,
                colour = TvPalette.text,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
}

private const val TOAST_MS = 3200L
