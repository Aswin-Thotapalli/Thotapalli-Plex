package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thotapalli.plex.core.api.ServerActivity
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.Motion
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.design.backgroundBrush
import androidx.compose.runtime.CompositionLocalProvider
import com.thotapalli.plex.ui.shared.player.LocalPlaybackTuning
import com.thotapalli.plex.ui.shared.player.PlayerScreen
import com.thotapalli.plex.ui.shared.player.rememberPlaybackTuning
import com.thotapalli.plex.ui.shared.screens.DetailScreen
import com.thotapalli.plex.ui.shared.screens.DownloadsScreen
import com.thotapalli.plex.ui.shared.screens.HomeScreen
import com.thotapalli.plex.ui.shared.screens.HomeUserPicker
import com.thotapalli.plex.ui.shared.screens.LibrariesScreen
import com.thotapalli.plex.ui.shared.screens.LibraryScreen
import com.thotapalli.plex.ui.shared.screens.SearchScreen
import com.thotapalli.plex.ui.shared.screens.SettingsScreen
import com.thotapalli.plex.ui.shared.screens.SignInScreen
import com.thotapalli.plex.ui.shared.tv.TvApp

/** The destinations below the player. Nothing here is a discovery surface. */
enum class Destination(val label: String, val icon: PlexIconKind) {
    HOME("Home", PlexIconKind.HOME),
    SEARCH("Search", PlexIconKind.SEARCH),
    DOWNLOADS("Downloads", PlexIconKind.DOWNLOADS),
    // The library-chooser tab. It draws its own books glyph (see [LibrariesGlyph]) in both the
    // compact bottom bar and the wide sidebar, where it is a first-class top-level destination that
    // routes to [LibrariesScreen], so [icon] here is an unused placeholder.
    LIBRARY("Library", PlexIconKind.HOME),
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
    /** True while the host activity is in a Picture-in-Picture window (mobile only); collapses the
     *  player's transport overlay to a bare picture. Defaults false on TV and desktop. */
    isInPictureInPicture: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // A television is its own application on the TV focus contract: one input, a remote,
    // and nothing borrowed from the pointer-and-touch screens below. See ui/shared/tv.
    if (container.isTelevision) {
        TvApp(container = container, viewModel = viewModel, onOpenUrl = onOpenUrl, onPlay = onPlay, modifier = modifier)
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.HOME) }

    LaunchedEffect(Unit) {
        viewModel.onOpenUrl = onOpenUrl
        viewModel.start()
    }

    WithSizeClass(isTelevision = container.isTelevision) { sizeClass ->
        ThotapalliTheme(sizeClass = sizeClass, themeMode = state.themeMode) {
            Box(modifier.fillMaxSize().background(PlexTheme.colours.backgroundBrush())) {
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
                        onSelect = { user, pin -> viewModel.selectHomeUser(user, pin) },
                    )

                    AppPhase.ERROR -> ErrorScreen(state.error ?: "Something went wrong.")

                    AppPhase.READY -> ReadyContent(
                        state = state,
                        viewModel = viewModel,
                        destination = destination,
                        onDestinationChange = { destination = it },
                        onPlay = onPlay,
                        isDesktop = container.isDesktop,
                    )
                }

                // The full-screen player sits above everything, including the navigation, so
                // it is truly full bleed. Its own back and the hardware back both leave it. While it
                // is up, the system bars are hidden (swipe to reveal) so the picture is not sharing
                // the screen with the OS navigation bar. See CLAUDE.md section 14 item 7.
                val playback = state.playback
                ImmersiveSystemBars(hidden = playback != null)
                if (playback != null) {
                    val tuning = rememberPlaybackTuning(container, state.settingsRevision)
                    CompositionLocalProvider(LocalPlaybackTuning provides tuning) {
                    PlayerScreen(
                        container = container,
                        item = playback.item,
                        serverScope = playback.serverScope,
                        urls = playback.urls,
                        startAtMs = playback.startAtMs,
                        onExit = viewModel::closePlayer,
                        onToggleFullScreen = viewModel::toggleFullScreen,
                        isFullScreen = state.isFullScreen,
                        // Retry a failed stream when the connection returns, no restart (§10, #1).
                        networkRegained = viewModel.networkRegained,
                        collapseControls = isInPictureInPicture,
                        modifier = Modifier.fillMaxSize(),
                    )
                    }
                }

                // The transient result of a menu action (marked watched, deleted, scan
                // started). Shown as a snackbar and dismissed once seen, and kept out of the
                // player, which owns the whole screen while it is up.
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(state.notice) {
                    val message = state.notice
                    if (message != null) {
                        snackbarHostState.showSnackbar(message)
                        viewModel.dismissNotice()
                    }
                }
                if (playback == null) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(Spacing.md),
                    ) { data ->
                        Snackbar(
                            containerColor = PlexTheme.colours.surfaceElevated,
                            contentColor = PlexTheme.colours.textPrimary,
                            shape = Radius.card,
                        ) {
                            PlexText(
                                text = data.visuals.message,
                                colour = PlexTheme.colours.textPrimary,
                            )
                        }
                    }
                }

                // The branded launch animation, over everything, once per process launch. It
                // covers the first frames while the app loads underneath, then fades to reveal it.
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
private fun ReadyContent(
    state: AppState,
    viewModel: AppViewModel,
    destination: Destination,
    onDestinationChange: (Destination) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    isDesktop: Boolean,
) {
    val server = state.server ?: return
    val sizeClass = PlexTheme.sizeClass

    // One factory binds every server action to a given item, so the tiles and the detail
    // overflow all raise the same menu. Remove-from-Continue-Watching is always bound; the
    // menu only offers it where the host says the tile is a Continue Watching one.
    // Remembered against the view model (which is itself remembered), so the factory keeps one
    // identity across recompositions instead of a fresh lambda each pass — which lets the screens
    // that take it as a parameter skip when nothing else of theirs changed.
    val itemActions: (MediaItem) -> ItemActions = remember(viewModel) {
        { item ->
            ItemActions(
                onMarkWatched = { viewModel.setWatched(item, true) },
                onMarkUnwatched = { viewModel.setWatched(item, false) },
                onDownload = { viewModel.download(item) },
                onRefreshMetadata = { viewModel.refreshItemMetadata(item) },
                onDelete = { viewModel.deleteItem(item) },
                onRemoveFromContinueWatching = { viewModel.removeFromContinueWatching(item) },
            )
        }
    }

    // Detail carries the floating back control. The library grid now has its own back button in
    // its header (which also closes an open collection), so it does not use the floating one.
    val canGoBack = state.detail != null

    // The chrome screens (search, downloads, settings, a library grid) open with a title at the
    // very top, so they must clear the status bar and any camera cutout. Home and detail bleed a
    // cinematic backdrop to the top edge instead, so they are left without it. See CLAUDE.md §13.
    val topSafe = Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
    )

    val body: @Composable (Modifier) -> Unit = { bodyModifier ->
        when {
            state.detail != null -> DetailScreen(
                server = server,
                state = state.detail,
                onPlay = onPlay,
                onDownload = viewModel::download,
                onToggleWatched = viewModel::toggleWatched,
                onSeasonSelected = viewModel::selectSeason,
                onSelectEpisode = viewModel::selectEpisode,
                onSetContainerWatched = viewModel::setContainerWatched,
                actions = itemActions(state.detail.item),
                modifier = bodyModifier,
            )

            state.library != null &&
                (destination == Destination.HOME || destination == Destination.LIBRARY) -> LibraryScreen(
                server = server,
                state = state.library,
                onItemClick = viewModel::openDetail,
                onCollectionClick = viewModel::openCollection,
                onUnwatchedOnlyChange = viewModel::setUnwatchedOnly,
                onSortChange = viewModel::setSort,
                onGenreChange = viewModel::setGenre,
                onCloseCollection = viewModel::closeCollection,
                onScanLibrary = viewModel::scanLibrary,
                onBack = viewModel::back,
                itemActions = itemActions,
                // The scan indicator is the single global overlay (below) now, shown on every screen;
                // the in-library bar is dropped so a scan never renders twice at once. See §5, #3.
                scanActivity = null,
                modifier = bodyModifier.then(topSafe),
            )

            destination == Destination.SEARCH -> SearchScreen(
                server = server,
                state = state.search,
                onQueryChange = viewModel::onSearchQueryChanged,
                onItemClick = viewModel::openDetail,
                modifier = bodyModifier.then(topSafe),
            )

            destination == Destination.DOWNLOADS -> DownloadsScreen(
                entries = state.downloads,
                totalBytesOnDisk = state.downloadBytesOnDisk,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onDelete = viewModel::deleteDownload,
                onPlayDownload = { entry ->
                    // Resolve the downloaded row to its item; the player then serves the local file
                    // offline via the resolver (§11, #1). Resume from where it was left off.
                    viewModel.playDownload(entry) { onPlay(it, it.viewOffsetMs) }
                },
                modifier = bodyModifier.then(topSafe),
            )

            destination == Destination.SETTINGS -> SettingsScreen(
                state = viewModel.settingsState(),
                onMatchDisplayRateChange = viewModel::setMatchDisplayRate,
                onAutoPlayNextChange = viewModel::setAutoPlayNext,
                onUnmeteredOnlyChange = viewModel::setUnmeteredOnly,
                onAudioLanguageChange = viewModel::setAudioLanguage,
                onSubtitleLanguageChange = viewModel::setSubtitleLanguage,
                onSubtitlesOnChange = viewModel::setSubtitlesOn,
                onStreamingBitrateChange = viewModel::setStreamingBitrate,
                onSubtitleScaleChange = viewModel::setSubtitleScale,
                onSubtitleForegroundChange = viewModel::setSubtitleForeground,
                onSubtitleBackgroundOpacityChange = viewModel::setSubtitleBackgroundOpacity,
                onSelectServer = viewModel::selectServer,
                onSignOut = viewModel::signOut,
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                serverUpdate = state.serverUpdate,
                serverUpdateApplying = state.serverUpdateApplying,
                onCheckServerUpdate = viewModel::checkServerUpdate,
                onApplyServerUpdate = viewModel::applyServerUpdate,
                modifier = bodyModifier.then(topSafe),
            )

            else -> HomeScreen(
                server = server,
                continueWatching = state.continueWatching,
                libraries = state.libraries,
                libraryPreviews = state.libraryPreviews,
                onItemClick = viewModel::openDetail,
                onLibraryClick = { library: Library -> viewModel.openLibrary(library) },
                onPlay = onPlay,
                itemActions = itemActions,
                // "View all" on the Libraries row opens the library list (chooser), not a library.
                onOpenLibrariesList = { onDestinationChange(Destination.LIBRARY) },
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

    // Home clears any open library and returns to the Home destination. The compact chooser opens a
    // library and drops back to the Home destination, where the body's library grid shows; the wide
    // sidebar instead keeps the Library tab active while a library is open. See CLAUDE.md section 13.
    val onHome: () -> Unit = { viewModel.closeLibrary(); onDestinationChange(Destination.HOME) }
    val onOpenLibrary: (Library) -> Unit = { library ->
        viewModel.openLibrary(library)
        onDestinationChange(Destination.HOME)
    }

    // The wide sidebar's unified top-level selection. Home and Library are their own contexts, so
    // choosing either first closes any open library — Library then shows its chooser, Home its rows.
    // The other tabs simply switch destination and leave any open library in the background.
    val onWideSelect: (Destination) -> Unit = { dest ->
        when (dest) {
            Destination.HOME -> onHome()
            Destination.LIBRARY -> { viewModel.closeLibrary(); onDestinationChange(Destination.LIBRARY) }
            else -> onDestinationChange(dest)
        }
    }

    // The shell sits directly on the solid ground the root paints — no ambient backdrop, no glass
    // frosting. Compact wears a top app bar and a floating bottom tab bar around its top-level tabs;
    // every wider class — medium, expanded and television alike — gets the persistent left sidebar so
    // the D-pad has a natural first column and the libraries are always one hop away.
    Box(Modifier.fillMaxSize()) {
        when {
            sizeClass.navigation == com.thotapalli.plex.ui.design.NavigationStyle.BOTTOM_BAR -> {
                // A detail and an open library grid are immersive: each carries its own header and
                // back control and takes the whole screen, so neither the top app bar nor the
                // floating tab bar is drawn over them.
                val immersive = state.detail != null || state.library != null
                if (immersive) {
                    bodyWithBack(Modifier)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        CompactTopBar(
                            accountName = state.accountName,
                            onSearch = { onDestinationChange(Destination.SEARCH) },
                            onProfile = { onDestinationChange(Destination.SETTINGS) },
                        )
                        // The top app bar above already clears the status bar and stands in for the
                        // top inset, so the tab bodies must not add it a second time.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                                ),
                        ) {
                            if (destination == Destination.LIBRARY) {
                                LibraryChooser(
                                    libraries = state.libraries,
                                    scanActivities = state.scanActivities,
                                    onOpenLibrary = onOpenLibrary,
                                    onScanLibrary = viewModel::scanLibrary,
                                    onGrantLibraryAccess = viewModel::grantLibraryAccess,
                                    onMoveLibrary = viewModel::moveLibrary,
                                )
                            } else {
                                body(Modifier)
                            }
                        }
                        CompactBottomTabBar(
                            current = destination,
                            onHome = onHome,
                            onSelect = onDestinationChange,
                        )
                    }
                }
            }

            // A movie/show detail takes the WHOLE screen on tablet and desktop — the sidebar hides so
            // the backdrop bleeds full-width and the episode list gets the room, exactly like Plex.
            // The detail carries its own back control. See CLAUDE.md sections 13 and 14.
            sizeClass != SizeClass.COMPACT && state.detail != null -> {
                bodyWithBack(Modifier.fillMaxSize())
            }

            // Medium (desktop) and expanded (tablet) otherwise keep the persistent left sidebar: it
            // gives the D-pad and pointer a natural first column with the libraries one hop away.
            else -> Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    current = destination,
                    narrow = sizeClass == SizeClass.MEDIUM,
                    accountName = state.accountName,
                    // Desktop (per the Windows mockups) keeps the libraries listed in the sidebar with
                    // reorder + grant-access in each row's overflow; tablet instead uses the
                    // unified Library tab that opens the libraries chooser.
                    isDesktop = isDesktop,
                    libraries = state.libraries,
                    selectedLibraryKey = state.library?.library?.key,
                    scanActivities = state.scanActivities,
                    onSelect = onWideSelect,
                    onOpenLibrary = onOpenLibrary,
                    onScanLibrary = viewModel::scanLibrary,
                    onGrantLibraryAccess = viewModel::grantLibraryAccess,
                    onMoveLibrary = viewModel::moveLibrary,
                    onOpenProfile = { onDestinationChange(Destination.SETTINGS) },
                )
                Box(Modifier.weight(1f)) {
                    // The Library tab with nothing opened shows the libraries chooser; once a library
                    // (or a detail) is open the ordinary body takes over, showing LibraryScreen — the
                    // gate in [body] keeps the library visible while the Library tab stays selected.
                    val showChooser = destination == Destination.LIBRARY &&
                        state.library == null && state.detail == null
                    if (showChooser) {
                        LibrariesScreen(
                            server = server,
                            libraries = state.libraries,
                            continueWatching = state.continueWatching,
                            libraryPreviews = state.libraryPreviews,
                            onOpenLibrary = { viewModel.openLibrary(it) },
                            onItemClick = viewModel::openDetail,
                            itemActions = itemActions,
                            modifier = Modifier.fillMaxSize().then(topSafe),
                        )
                    } else {
                        bodyWithBack(Modifier)
                    }
                }
            }
        }

        // A GLOBAL scan indicator: floats over every screen so a running scan of ANY library is
        // visible from anywhere, not only inside that library or on the one screen that matched it.
        // This is the real fix for "scan only shows for one library" — it no longer gates on the
        // open/selected library at all. Hidden inside a detail (which is its own immersive surface).
        if (state.scanActivities.isNotEmpty() && state.detail == null) {
            ScanStatusOverlay(
                activities = state.scanActivities,
                libraries = state.libraries,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(Spacing.md),
            )
        }
    }
}

/**
 * A small floating card listing every library currently being scanned, with a title, the current
 * step and a percentage — visible on any screen so no scan is ever hidden. Names are resolved from
 * [libraries] by section id, falling back to the activity's own title. See CLAUDE.md section 5.
 */
@Composable
private fun ScanStatusOverlay(
    activities: List<ServerActivity>,
    libraries: List<Library>,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .material(GlassRole.SHEET, Radius.card)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlexText(
            text = if (activities.size == 1) "Scanning" else "Scanning ${activities.size} libraries",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
            maxLines = 1,
        )
        activities.forEach { activity ->
            val name = libraries.firstOrNull { it.key == activity.librarySectionId }?.title
                ?: activity.title.ifBlank { "Library" }
            val pct = (activity.progress.coerceIn(0f, 1f) * 100f).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlexText(
                        text = name,
                        style = PlexTheme.type.label,
                        colour = colours.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    if (activity.progress > 0f) {
                        PlexText(text = "$pct%", style = PlexTheme.type.label, colour = colours.accent, maxLines = 1)
                    }
                }
                val step = activity.subtitle
                if (!step.isNullOrBlank()) {
                    PlexText(text = step, style = PlexTheme.type.caption, colour = colours.textSecondary, maxLines = 1)
                }
                // A scan that has only just started (or an optimistic placeholder) reports no
                // progress yet, so show an indeterminate sweep rather than a bar stuck at 0%.
                if (activity.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { activity.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(Radius.pill),
                        color = colours.accent,
                        trackColor = colours.surface,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(Radius.pill),
                        color = colours.accent,
                        trackColor = colours.surface,
                    )
                }
            }
        }
    }
}

/**
 * The persistent left sidebar for medium and expanded (desktop and tablet). Television instead uses
 * the top-nav [TvShell]. A solid full-height panel (the
 * [GlassRole.CHROME] material): the brand mark and wordmark at the top, then one unified list of the
 * five top-level destinations — Home, Search, Downloads, Library, Settings — as pill rows, the
 * selected one carrying the amber accent pill. Libraries are no longer listed here; the Library tab
 * routes to the libraries chooser instead. A profile chip is pinned to the foot. See CLAUDE.md
 * section 13.
 */
@Composable
private fun NavigationRail(
    current: Destination,
    narrow: Boolean,
    accountName: String?,
    isDesktop: Boolean,
    libraries: List<Library>,
    selectedLibraryKey: String?,
    scanActivities: List<ServerActivity>,
    onSelect: (Destination) -> Unit,
    onOpenLibrary: (Library) -> Unit,
    onScanLibrary: (Library) -> Unit,
    onGrantLibraryAccess: (email: String, libraryKeys: List<String>) -> Unit,
    onMoveLibrary: (fromIndex: Int, toIndex: Int) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val colours = PlexTheme.colours
    // On desktop the libraries live in their own section here, so the Library tab is dropped from the
    // top-level list; tablet and TV keep it (it opens the chooser).
    val showLibrarySection = isDesktop && libraries.isNotEmpty()
    val navEntries = if (isDesktop) {
        Destination.entries.filter { it != Destination.LIBRARY }
    } else {
        Destination.entries
    }

    Column(
        modifier = Modifier
            .width(if (narrow) 200.dp else 248.dp)
            .fillMaxHeight()
            // A solid side panel: the CHROME material paints an opaque surface fill with a hairline
            // border, flush to the left edge — the clean, modern look of the reference mockups.
            .material(GlassRole.CHROME, RectangleShape)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        // The brand mark and wordmark crown the rail: "Thotapalli" in primary, "Plex" in the accent.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AppLogo(size = 28.dp)
            // A fixed wordmark size (rather than the size-class title, which is large on expanded and
            // television) so "Thotapalli Plex" always fits the rail without truncating.
            val brandStyle = PlexTheme.type.title.copy(fontSize = 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlexText(
                    text = "Thotapalli ",
                    style = brandStyle,
                    colour = colours.textPrimary,
                    maxLines = 1,
                )
                PlexText(
                    text = "Plex",
                    style = brandStyle,
                    colour = colours.accent,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // The top-level destinations, in fixed order. On desktop the Library entry is dropped in
        // favour of the LIBRARIES section below; elsewhere all five show. The selected row wears the
        // amber accent pill.
        navEntries.forEach { entry ->
            RailNavRow(
                destination = entry,
                selected = entry == current,
                onClick = { onSelect(entry) },
            )
        }

        if (showLibrarySection) {
            // Reorder is a mode, not a per-row nudge: the overflow's "Reorder" turns it on, every row
            // then shows a drag handle so any library can be dragged to any position, and "Done" ends
            // it.
            var reordering by remember { mutableStateOf(false) }
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlexText(
                    text = if (reordering) "REORDER" else "LIBRARIES",
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (reordering) {
                    PlexText(
                        text = "Done",
                        style = PlexTheme.type.label,
                        colour = colours.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .plexFocusable(
                                shape = Radius.pill,
                                onClick = { reordering = false },
                                scaleOnFocus = false,
                            )
                            .clip(Radius.pill)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }
            }
            // A weighted scroller so a long library list never pushes the profile chip off the foot.
            val listModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
            if (reordering) {
                ReorderLibraryColumn(
                    libraries = libraries,
                    onMove = onMoveLibrary,
                    modifier = listModifier,
                ) { library, dragging, dragHandle ->
                    SidebarLibraryRow(
                        library = library,
                        selected = false,
                        reordering = true,
                        dragging = dragging,
                        dragHandle = dragHandle,
                        allLibraries = libraries,
                        scanActivity = null,
                        onClick = {},
                        onReorder = {},
                        onScan = {},
                        onGrantAccess = { _, _ -> },
                    )
                }
            } else {
                Column(
                    modifier = listModifier,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    libraries.forEach { library ->
                        SidebarLibraryRow(
                            library = library,
                            selected = library.key == selectedLibraryKey,
                            reordering = false,
                            dragging = false,
                            dragHandle = Modifier,
                            allLibraries = libraries,
                            // Any library's live scan shows right here on its row, so a scan is
                            // visible no matter which screen is open — not only inside that library.
                            scanActivity = scanActivities.firstOrNull { it.librarySectionId == library.key },
                            onClick = { onOpenLibrary(library) },
                            onReorder = { reordering = true },
                            onScan = { onScanLibrary(library) },
                            onGrantAccess = onGrantLibraryAccess,
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // The signed-in account, pinned to the foot. Tapping it opens Settings.
        ProfileChip(
            name = accountName,
            onClick = onOpenProfile,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * A library row in the desktop sidebar's LIBRARIES section. Normally the kind glyph and full title
 * select the library and a "⋮" opens reorder / scan / grant-access; the title wraps to two lines
 * rather than truncating so the full name is always readable. In [reordering] mode the "⋮" is
 * replaced by a drag handle ([dragHandle]) — press and drag it to move the library anywhere — and the
 * row lifts while [dragging]. Selected draws the amber accent pill, matching [RailNavRow]. See
 * CLAUDE.md section 13.
 */
@Composable
private fun SidebarLibraryRow(
    library: Library,
    selected: Boolean,
    reordering: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    allLibraries: List<Library>,
    scanActivity: ServerActivity?,
    onClick: () -> Unit,
    onReorder: () -> Unit,
    onScan: () -> Unit,
    onGrantAccess: (email: String, libraryKeys: List<String>) -> Unit,
) {
    val colours = PlexTheme.colours
    val rowShape = Radius.glassSmall
    var menuOpen by remember { mutableStateOf(false) }
    var grantOpen by remember { mutableStateOf(false) }
    val iconTint = if (selected) colours.accent else colours.textSecondary
    val labelTint = if (selected) colours.accent else colours.textPrimary
    val rowBackground = when {
        dragging -> colours.surfaceElevated
        selected -> colours.accent.copy(alpha = 0.14f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBackground, rowShape)
            .then(if (dragging) Modifier.border(1.dp, colours.border, rowShape) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (reordering) Modifier
                        else Modifier.plexFocusable(shape = rowShape, onClick = onClick, scaleOnFocus = false),
                    )
                    .padding(start = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LibraryAutoIcon(
                    name = library.title,
                    kind = library.kind,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
                PlexText(
                    text = library.title,
                    style = PlexTheme.type.label,
                    colour = labelTint,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
            if (reordering) {
                // A drag handle: press and drag it up or down to move the library to any position
                // while the mode is on.
                DragHandle(modifier = dragHandle)
                Spacer(Modifier.width(Spacing.xxs))
            } else {
                Box {
                    OverflowButton(onClick = { menuOpen = true })
                    LibraryOverflowMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onReorder = { menuOpen = false; onReorder() },
                        onScan = { menuOpen = false; onScan() },
                        onGrantAccess = { menuOpen = false; grantOpen = true },
                    )
                }
            }
        }

        // A live scan of THIS library shows inline on its row — a percentage and a slim amber bar —
        // so a running scan is visible from any screen, not only from inside that library.
        if (scanActivity != null) {
            val fraction = scanActivity.progress.coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                PlexText(
                    text = "Scanning • ${(fraction * 100).toInt()}%",
                    style = PlexTheme.type.caption,
                    colour = colours.accent,
                    maxLines = 1,
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(Radius.pill),
                    color = colours.accent,
                    trackColor = colours.surface,
                )
            }
        }
    }

    if (grantOpen) {
        GrantAccessDialog(
            libraries = allLibraries,
            initialLibraryKey = library.key,
            onShare = { email, keys -> grantOpen = false; onGrantAccess(email, keys) },
            onDismiss = { grantOpen = false },
        )
    }
}

/**
 * Runs [libraries] as a drag-to-reorder list: a plain (non-lazy) scrolling column — the list is a
 * handful of libraries — where each row carries a [dragHandle] the caller wires to its handle control.
 * Pressing and dragging a handle lifts that row and, as it passes a neighbour, calls [onMove] to swap
 * them live (persisted by the view model). Rows are keyed by library so an in-flight drag survives the
 * reorder. See CLAUDE.md section 13.
 */
@Composable
private fun ReorderLibraryColumn(
    libraries: List<Library>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (library: Library, dragging: Boolean, dragHandle: Modifier) -> Unit,
) {
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    Column(modifier) {
        libraries.forEach { library ->
            key(library.key) {
                val dragging = library.key == draggedKey
                val handle = Modifier.pointerInput(library.key) {
                    detectDragGestures(
                        onDragStart = { draggedKey = library.key; dragDelta = 0f },
                        onDragEnd = { draggedKey = null; dragDelta = 0f },
                        onDragCancel = { draggedKey = null; dragDelta = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDelta += dragAmount.y
                            val cur = libraries.indexOfFirst { it.key == draggedKey }
                            if (cur < 0) return@detectDragGestures
                            val h = heights[draggedKey] ?: return@detectDragGestures
                            when {
                                dragDelta > h / 2f && cur < libraries.lastIndex -> {
                                    onMove(cur, cur + 1); dragDelta -= h
                                }
                                dragDelta < -h / 2f && cur > 0 -> {
                                    onMove(cur, cur - 1); dragDelta += h
                                }
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .onSizeChanged { heights[library.key] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragDelta else 0f },
                ) {
                    row(library, dragging, handle)
                }
            }
        }
    }
}

/** A drag-grip glyph (three short bars) carrying [modifier]'s drag gesture, shown in reorder mode. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier.size(40.dp).then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val sw = size.minDimension * 0.10f
            listOf(0.34f, 0.5f, 0.66f).forEach { fy ->
                drawLine(
                    colours.textSecondary,
                    Offset(w * 0.24f, size.height * fy),
                    Offset(w * 0.76f, size.height * fy),
                    sw,
                    StrokeCap.Round,
                )
            }
        }
    }
}


/**
 * The account chip pinned to the foot of the sidebar: a circular accent avatar carrying the account's
 * initial with a small green online dot, over a two-line "Welcome back," / display-name block.
 * Tapping it opens Settings. A subtle [GlassRole.CARD] surface so it reads as a distinct, tappable
 * footer.
 */
@Composable
private fun ProfileChip(
    name: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val display = name?.takeIf { it.isNotBlank() } ?: "Account"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .material(GlassRole.CARD, Radius.card)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ProfileDisc(name = display, showOnlineDot = true)
        Column(Modifier.weight(1f)) {
            PlexText(
                text = "Welcome back,",
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                maxLines = 1,
            )
            PlexText(
                text = display,
                style = PlexTheme.type.label,
                colour = colours.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The online-status green for the account avatar's presence dot. The design palette (CLAUDE.md §12)
 * carries only a single amber accent and no status green, and this module cannot add a token, so this
 * one status colour is defined locally. TODO: promote to a `colours.online` token in `ui:design`.
 */
private val OnlineGreen = Color(0xFF32D74B)

/**
 * A solid accent avatar disc carrying the account's initial, optionally with a small green online dot
 * ringed against the surface so it reads over the disc. Non-interactive; the enclosing control owns
 * the tap. Mirrors the look of [ProfileAvatar].
 */
@Composable
private fun ProfileDisc(name: String?, showOnlineDot: Boolean, modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    val initial = name?.takeIf { it.isNotBlank() }
        ?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
    Box(modifier = modifier.size(36.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(Radius.pill)
                .background(colours.accent, Radius.pill),
            contentAlignment = Alignment.Center,
        ) {
            PlexText(
                text = initial,
                style = PlexTheme.type.label,
                colour = if (colours.isDark) colours.background else Color.White,
                maxLines = 1,
            )
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(Radius.pill)
                    .background(colours.surface, Radius.pill)
                    .padding(2.dp)
                    .clip(Radius.pill)
                    .background(OnlineGreen, Radius.pill),
            )
        }
    }
}

/**
 * The live library-scan read-out: one row per running server job with its title, current step in
 * caption and a slim amber progress bar, in a [GlassRole.CARD] container. Mirrors Plex's own
 * always-on scan progress. See CLAUDE.md section 5.
 */
@Composable
private fun ScanningSection(
    activities: List<ServerActivity>,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Column(
        modifier = modifier
            .fillMaxWidth()
            .material(GlassRole.CARD, Radius.glassSmall)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlexText(
            text = "Scanning",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
            maxLines = 1,
        )
        activities.forEach { activity ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                PlexText(
                    text = activity.title,
                    style = PlexTheme.type.label,
                    colour = colours.textPrimary,
                    maxLines = 1,
                )
                val subtitle = activity.subtitle
                if (!subtitle.isNullOrBlank()) {
                    PlexText(
                        text = subtitle,
                        style = PlexTheme.type.caption,
                        colour = colours.textSecondary,
                        maxLines = 1,
                    )
                }
                LinearProgressIndicator(
                    progress = { activity.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(Radius.pill),
                    color = colours.accent,
                    trackColor = colours.surface,
                )
            }
        }
    }
}

/**
 * A top-level destination row in the unified sidebar: a leading glyph and its label. Selected draws a
 * rounded accent pill and tints both glyph and label amber; unselected keeps the glyph quiet
 * (secondary) and the label at primary strength so every destination stays plainly legible. The
 * Library destination draws the three-line books glyph the icon set does not carry. Every row is
 * [plexFocusable] so a remote or keyboard draws the accent focus ring. See CLAUDE.md section 13.
 */
@Composable
private fun RailNavRow(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    val rowShape = Radius.glassSmall
    val iconTint = if (selected) colours.accent else colours.textSecondary
    val labelTint = if (selected) colours.accent else colours.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .plexFocusable(shape = rowShape, onClick = onClick, scaleOnFocus = false)
            .clip(rowShape)
            .background(
                if (selected) colours.accent.copy(alpha = 0.14f) else Color.Transparent,
                rowShape,
            )
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (destination == Destination.LIBRARY) {
            LibrariesGlyph(iconTint)
        } else {
            PlexIcon(kind = destination.icon, tint = iconTint, size = 24.dp)
        }
        PlexText(
            text = destination.label,
            style = PlexTheme.type.label,
            colour = labelTint,
            maxLines = 1,
        )
    }
}

/**
 * The grant-access dialog raised from a library's overflow. An email field and a checkbox list of
 * every library (the one the menu was opened on pre-checked), sharing through the view model. A
 * [GlassRole.SHEET] surface, matching the delete-confirmation dialog. See CLAUDE.md section 5.
 */
@Composable
private fun GrantAccessDialog(
    libraries: List<Library>,
    initialLibraryKey: String,
    onShare: (email: String, libraryKeys: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colours = PlexTheme.colours
    var email by remember { mutableStateOf("") }
    val checked = remember { mutableStateListOf<String>().apply { add(initialLibraryKey) } }
    val canShare = email.isNotBlank() && checked.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.material(GlassRole.SHEET, Radius.card),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = colours.textPrimary,
        textContentColor = colours.textSecondary,
        shape = Radius.card,
        title = { PlexText(text = "Grant access", style = PlexTheme.type.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .material(GlassRole.SECONDARY, Radius.glassSmall)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        if (email.isEmpty()) {
                            PlexText(
                                text = "Plex account email",
                                style = PlexTheme.type.body,
                                colour = colours.textSecondary,
                            )
                        }
                        BasicTextField(
                            value = email,
                            onValueChange = { email = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.merge(PlexTheme.type.body)
                                .copy(color = colours.textPrimary),
                            cursorBrush = SolidColor(colours.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                PlexText(
                    text = "Libraries",
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    libraries.forEach { library ->
                        val isChecked = checked.contains(library.key)
                        LibraryCheckRow(
                            title = library.title,
                            checked = isChecked,
                            onToggle = {
                                if (isChecked) checked.remove(library.key) else checked.add(library.key)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                label = "Share",
                onClick = { onShare(email.trim(), checked.toList()) },
                enabled = canShare,
            )
        },
        dismissButton = { SecondaryButton(label = "Cancel", onClick = onDismiss) },
    )
}

/** A single library checkbox row inside [GrantAccessDialog]: an accent tick box and the title. */
@Composable
private fun LibraryCheckRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    val colours = PlexTheme.colours
    val box = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.glassSmall, onClick = onToggle, scaleOnFocus = false)
            .clip(Radius.glassSmall)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(box)
                .background(if (checked) colours.accent else Color.Transparent, box)
                .then(if (checked) Modifier else Modifier.border(1.dp, colours.border, box)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                PlexIcon(
                    kind = PlexIconKind.CHECK,
                    size = 16.dp,
                    tint = if (colours.isDark) colours.background else Color.White,
                )
            }
        }
        PlexText(
            text = title,
            style = PlexTheme.type.label,
            colour = colours.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The compact top app bar: a slim Row on the ground, shown across the top-level tabs (it is hidden by
 * the shell over an open library grid or a detail, which carry their own header). The brand mark and
 * wordmark sit on the left with "Plex" in the accent; a search shortcut and the account avatar sit on
 * the right. See CLAUDE.md sections 13 and 15.
 */
@Composable
private fun CompactTopBar(
    accountName: String?,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
) {
    val colours = PlexTheme.colours
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The ground material paints the whole strip, status-bar area included, so nothing shows
            // through above the bar; the top inset then drops the content clear of the cutout.
            .material(GlassRole.GROUND, RectangleShape)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .height(56.dp)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppLogo(size = 28.dp)
        // A fixed wordmark size so "Thotapalli Plex" always fits beside the search and profile
        // controls; the weight pushes those controls to the right and lets the wordmark take the slack.
        val brandStyle = PlexTheme.type.title.copy(fontSize = 20.sp)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlexText(
                text = "Thotapalli ",
                style = brandStyle,
                colour = colours.textPrimary,
                maxLines = 1,
            )
            PlexText(
                text = "Plex",
                style = brandStyle,
                colour = colours.accent,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .plexFocusable(shape = Radius.pill, onClick = onSearch, scaleOnFocus = false),
            contentAlignment = Alignment.Center,
        ) {
            PlexIcon(kind = PlexIconKind.SEARCH, tint = colours.textPrimary, size = 24.dp)
        }
        ProfileAvatar(name = accountName, onClick = onProfile)
    }
}

/**
 * The account avatar in the compact top bar: a solid accent disc carrying the account's initial, that
 * opens Settings on tap. Mirrors the sidebar's [ProfileChip] in a single compact control.
 */
@Composable
private fun ProfileAvatar(name: String?, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    val initial = name?.takeIf { it.isNotBlank() }
        ?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
    Box(
        modifier = Modifier
            .size(36.dp)
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.pill)
            .background(colours.accent, Radius.pill),
        contentAlignment = Alignment.Center,
    ) {
        PlexText(
            text = initial,
            style = PlexTheme.type.label,
            colour = if (colours.isDark) colours.background else Color.White,
            maxLines = 1,
        )
    }
}

/**
 * The compact floating bottom tab bar: a rounded glass pill inset from the screen edges, holding the
 * five top-level tabs — Home, Search, Downloads, Library and Settings. The selected tab tints its
 * glyph and label in the accent; the rest sit quiet in the secondary text colour. See CLAUDE.md
 * section 13.
 */
@Composable
private fun CompactBottomTabBar(
    current: Destination,
    onHome: () -> Unit,
    onSelect: (Destination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The bottom inset lifts the pill clear of the gesture area; the horizontal margin and the
            // rounded-glass material give it the floating look.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .material(GlassRole.SHEET, RoundedCornerShape(28.dp))
            .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { entry ->
            CompactTabItem(
                destination = entry,
                selected = entry == current,
                onClick = { if (entry == Destination.HOME) onHome() else onSelect(entry) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A single tab in the compact bottom bar: a glyph over a small label, both accent when selected. The
 * Library tab draws the three-line books glyph the icon set does not carry. */
@Composable
private fun CompactTabItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val tint = if (selected) colours.accent else colours.textSecondary
    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.glassSmall)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (destination == Destination.LIBRARY) {
            LibrariesGlyph(tint)
        } else {
            PlexIcon(kind = destination.icon, tint = tint, size = 24.dp)
        }
        Spacer(Modifier.height(Spacing.xxs))
        PlexText(text = destination.label, style = PlexTheme.type.caption, colour = tint, maxLines = 1)
    }
}

/**
 * The compact library chooser, shown under the Library tab: every library the server exposes as a
 * tappable card carrying its kind glyph, its title and a trailing overflow that scans it or grants an
 * account access to it. Tapping a card opens that library full-screen. See CLAUDE.md sections 5 and 13.
 */
@Composable
private fun LibraryChooser(
    libraries: List<Library>,
    scanActivities: List<ServerActivity>,
    onOpenLibrary: (Library) -> Unit,
    onScanLibrary: (Library) -> Unit,
    onGrantLibraryAccess: (email: String, libraryKeys: List<String>) -> Unit,
    onMoveLibrary: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val colours = PlexTheme.colours
    // Reorder is a mode: "Reorder" in a row's overflow turns it on, every row then shows a drag
    // handle so any library can be dragged to any position until "Done".
    var reordering by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SectionHeader(if (reordering) "Reorder" else "Libraries", Modifier.weight(1f))
                if (reordering) {
                    PlexText(
                        text = "Done",
                        style = PlexTheme.type.label,
                        colour = colours.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .plexFocusable(
                                shape = Radius.pill,
                                onClick = { reordering = false },
                                scaleOnFocus = false,
                            )
                            .clip(Radius.pill)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }
            }
        }
        if (scanActivities.isNotEmpty()) {
            item { ScanningSection(activities = scanActivities) }
        }
        if (reordering) {
            // A drag-to-reorder list rendered as a single non-lazy item (the library count is small):
            // each row carries a drag handle and dragging one past a neighbour moves it live.
            item {
                ReorderLibraryColumn(
                    libraries = libraries,
                    onMove = onMoveLibrary,
                    modifier = Modifier.fillMaxWidth(),
                ) { library, dragging, dragHandle ->
                    ChooserLibraryRow(
                        library = library,
                        allLibraries = libraries,
                        reordering = true,
                        dragging = dragging,
                        dragHandle = dragHandle,
                        onClick = {},
                        onReorder = {},
                        onScan = {},
                        onGrantAccess = { _, _ -> },
                    )
                }
            }
        } else {
            itemsIndexed(libraries, key = { _, library -> library.key }) { _, library ->
                ChooserLibraryRow(
                    library = library,
                    allLibraries = libraries,
                    reordering = false,
                    dragging = false,
                    dragHandle = Modifier,
                    onClick = { onOpenLibrary(library) },
                    onReorder = { reordering = true },
                    onScan = { onScanLibrary(library) },
                    onGrantAccess = onGrantLibraryAccess,
                )
            }
        }
    }
}

/** A library card in the compact chooser: the kind glyph and title select it, the overflow scans it
 * or opens the grant-access dialog. */
@Composable
private fun ChooserLibraryRow(
    library: Library,
    allLibraries: List<Library>,
    reordering: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onClick: () -> Unit,
    onReorder: () -> Unit,
    onScan: () -> Unit,
    onGrantAccess: (email: String, libraryKeys: List<String>) -> Unit,
) {
    val colours = PlexTheme.colours
    var menuOpen by remember { mutableStateOf(false) }
    var grantOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (reordering) Modifier.padding(vertical = Spacing.xxs) else Modifier)
            .material(GlassRole.CARD, Radius.card)
            .then(if (dragging) Modifier.border(1.dp, colours.accent, Radius.card) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (reordering) Modifier
                    else Modifier.plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false),
                )
                .padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            LibraryAutoIcon(
                name = library.title,
                kind = library.kind,
                tint = colours.textPrimary,
                modifier = Modifier.size(18.dp),
            )
            PlexText(
                text = library.title,
                style = PlexTheme.type.label,
                colour = colours.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        if (reordering) {
            DragHandle(modifier = dragHandle)
        } else {
            Box {
                OverflowButton(onClick = { menuOpen = true })
                LibraryOverflowMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onReorder = { menuOpen = false; onReorder() },
                    onScan = { menuOpen = false; onScan() },
                    onGrantAccess = { menuOpen = false; grantOpen = true },
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
    }

    if (grantOpen) {
        GrantAccessDialog(
            libraries = allLibraries,
            initialLibraryKey = library.key,
            onShare = { email, keys -> grantOpen = false; onGrantAccess(email, keys) },
            onDismiss = { grantOpen = false },
        )
    }
}

/**
 * The vertical three-dot overflow control. A full 40 dp touch target holds the tap, but the focus
 * and hover indicator is drawn on a compact rounded-square core rather than the whole circle, so the
 * accent ring reads as a small, tasteful chip instead of a large amber disc swallowing the row.
 */
@Composable
private fun OverflowButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
                .clip(Radius.glassSmall),
            contentAlignment = Alignment.Center,
        ) {
            OverflowDots(tint = PlexTheme.colours.textSecondary)
        }
    }
}

/**
 * A per-library overflow menu: enter reorder mode (where any library can be moved until Done), scan
 * the library's files, or grant an account access to it.
 */
@Composable
private fun LibraryOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReorder: () -> Unit,
    onScan: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    val colours = PlexTheme.colours
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.material(GlassRole.SHEET, Radius.glassSmall),
    ) {
        DropdownMenuItem(
            text = {
                PlexText(
                    text = "Reorder",
                    style = PlexTheme.type.label,
                    colour = colours.textPrimary,
                    maxLines = 1,
                )
            },
            onClick = onReorder,
        )
        DropdownMenuItem(
            text = {
                PlexText(
                    text = "Scan library files",
                    style = PlexTheme.type.label,
                    colour = colours.textPrimary,
                    maxLines = 1,
                )
            },
            onClick = onScan,
        )
        DropdownMenuItem(
            text = {
                PlexText(
                    text = "Grant access…",
                    style = PlexTheme.type.label,
                    colour = colours.textPrimary,
                    maxLines = 1,
                )
            },
            onClick = onGrantAccess,
        )
    }
}

/** Three stacked dots, the vertical overflow glyph drawn to match the hand-drawn icon set. */
@Composable
private fun OverflowDots(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val cx = size.width / 2f
        val r = size.minDimension * 0.09f
        listOf(0.26f, 0.5f, 0.74f).forEach { fy ->
            drawCircle(tint, r, Offset(cx, size.height * fy))
        }
    }
}

/**
 * A small glyph marking a library's kind: a film frame for a movie library, a screen for a show
 * library, a plain rounded tile otherwise. The icon set does not draw these, so they are hand-drawn
 * to match its weight.
 */
@Composable
private fun LibraryKindGlyph(kind: LibraryKind, tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = size.width * 0.09f
        when (kind) {
            LibraryKind.MOVIE -> {
                // A film frame: a rounded rectangle with a row of sprocket holes down each side.
                val left = size.width * 0.2f
                val right = size.width * 0.8f
                val top = size.height * 0.18f
                val bottom = size.height * 0.82f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(sw, sw),
                    style = Stroke(width = sw),
                )
                val holeR = size.width * 0.05f
                listOf(0.34f, 0.5f, 0.66f).forEach { fy ->
                    drawCircle(tint, holeR, Offset(left + sw * 1.6f, size.height * fy))
                    drawCircle(tint, holeR, Offset(right - sw * 1.6f, size.height * fy))
                }
            }
            LibraryKind.SHOW -> {
                // A screen: a rounded rectangle over a short stand.
                val left = size.width * 0.16f
                val right = size.width * 0.84f
                val top = size.height * 0.24f
                val bottom = size.height * 0.66f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(sw, sw),
                    style = Stroke(width = sw),
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.36f, size.height * 0.8f),
                    Offset(size.width * 0.64f, size.height * 0.8f),
                    sw,
                    StrokeCap.Round,
                )
            }
            LibraryKind.UNSUPPORTED -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.22f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(sw, sw),
                    style = Stroke(width = sw),
                )
            }
        }
    }
}

/** A three-line list glyph standing in for the libraries the icon set does not draw. */
@Composable
private fun LibrariesGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val x0 = size.width * 0.26f
        val x1 = size.width * 0.74f
        val sw = size.width * 0.09f
        listOf(0.34f, 0.5f, 0.66f).forEach { fy ->
            drawLine(tint, Offset(x0, size.height * fy), Offset(x1, size.height * fy), sw, StrokeCap.Round)
        }
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
