package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thotapalli.plex.core.api.ServerActivity
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.GlassScaffold
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.design.backgroundBrush
import com.thotapalli.plex.ui.design.glassSource
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
                        onToggleFullScreen = viewModel::toggleFullScreen,
                        isFullScreen = state.isFullScreen,
                        modifier = Modifier.fillMaxSize(),
                    )
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
) {
    val server = state.server ?: return
    val sizeClass = PlexTheme.sizeClass

    // One factory binds every server action to a given item, so the tiles and the detail
    // overflow all raise the same menu. Remove-from-Continue-Watching is always bound; the
    // menu only offers it where the host says the tile is a Continue Watching one.
    val itemActions: (MediaItem) -> ItemActions = { item ->
        ItemActions(
            onMarkWatched = { viewModel.setWatched(item, true) },
            onMarkUnwatched = { viewModel.setWatched(item, false) },
            onDownload = { viewModel.download(item) },
            onRefreshMetadata = { viewModel.refreshItemMetadata(item) },
            onDelete = { viewModel.deleteItem(item) },
            onRemoveFromContinueWatching = { viewModel.removeFromContinueWatching(item) },
        )
    }

    // A drill-in is anything with somewhere to go back to below a top-level destination.
    val canGoBack = state.detail != null ||
        state.library?.openCollection != null ||
        (state.library != null && destination == Destination.HOME)

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
                actions = itemActions(state.detail.item),
                modifier = bodyModifier,
            )

            state.library != null && destination == Destination.HOME -> LibraryScreen(
                server = server,
                state = state.library,
                onItemClick = viewModel::openDetail,
                onCollectionClick = viewModel::openCollection,
                onUnwatchedOnlyChange = viewModel::setUnwatchedOnly,
                onCloseCollection = viewModel::closeCollection,
                onScanLibrary = viewModel::scanLibrary,
                itemActions = itemActions,
                scanProgress = state.scanActivities
                    .firstOrNull { it.librarySectionId == state.library?.library?.key }
                    ?.progress,
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
                modifier = bodyModifier.then(topSafe),
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

    // The library the shell currently has open, so the navigation can mark where you are. Home
    // clears it; opening any library sets it and drops back to the Home destination, where the
    // body's library grid shows. See CLAUDE.md section 13.
    val openLibraryKey = state.library?.library?.key
    val onHome: () -> Unit = { viewModel.closeLibrary(); onDestinationChange(Destination.HOME) }
    val onOpenLibrary: (Library) -> Unit = { library ->
        viewModel.openLibrary(library)
        onDestinationChange(Destination.HOME)
    }

    // One backdrop host publishes the ambient haze state; the body marks itself the glass source
    // and the navigation frosts it. Compact rides a bottom glass bar with a Libraries sheet; every
    // wider class — medium, expanded and television alike — gets the persistent left glass rail so
    // the D-pad has a natural first column and the libraries are always one hop away.
    GlassScaffold(Modifier.fillMaxSize()) {
        // Compact keeps its libraries in a sheet whose state is hoisted here, so the sheet overlays
        // the whole screen as a sibling of the nav rather than a child of the bar's column.
        var librariesSheetOpen by remember { mutableStateOf(false) }

        // The shared liquid-glass backdrop: on Android this is Kyant's AGSL layer (real refraction),
        // on desktop the Haze source. A full-bleed, darkened backdrop of the featured artwork is
        // marked as that source, so the floating navigation samples real, colourful pixels — the
        // liquid glass look. The body content is drawn opaque on top; only the nav reveals it.
        val backdrop = rememberLiquidBackdrop()
        AmbientBackdrop(
            server = server,
            item = state.continueWatching.firstOrNull() ?: state.detail?.item,
            modifier = Modifier.fillMaxSize().glassSource().liquidBackdropSource(backdrop),
        )

        when (sizeClass.navigation) {
            com.thotapalli.plex.ui.design.NavigationStyle.BOTTOM_BAR -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { bodyWithBack(Modifier) }
                NavigationBottomBar(
                    current = destination,
                    openLibraryKey = openLibraryKey,
                    librariesOpen = librariesSheetOpen,
                    backdrop = backdrop,
                    onHome = onHome,
                    onSelect = onDestinationChange,
                    onOpenLibraries = { librariesSheetOpen = true },
                )
            }

            else -> Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    current = destination,
                    openLibraryKey = openLibraryKey,
                    libraries = state.libraries,
                    scanActivities = state.scanActivities,
                    narrow = sizeClass == SizeClass.MEDIUM,
                    backdrop = backdrop,
                    onHome = onHome,
                    onSelect = onDestinationChange,
                    onOpenLibrary = onOpenLibrary,
                    onScanLibrary = viewModel::scanLibrary,
                    onMoveLibrary = viewModel::moveLibrary,
                    onGrantLibraryAccess = viewModel::grantLibraryAccess,
                )
                Box(Modifier.weight(1f)) { bodyWithBack(Modifier) }
            }
        }

        if (librariesSheetOpen) {
            LibrarySheet(
                libraries = state.libraries,
                openLibraryKey = openLibraryKey,
                scanActivities = state.scanActivities,
                onDismiss = { librariesSheetOpen = false },
                onOpenLibrary = { library -> librariesSheetOpen = false; onOpenLibrary(library) },
                onScanLibrary = viewModel::scanLibrary,
                onGrantLibraryAccess = viewModel::grantLibraryAccess,
            )
        }
    }
}

/**
 * The persistent left glass rail for medium, expanded and television. A frosted full-height sheet:
 * the four top-level destinations, a divider, then every library the server exposes as a row with a
 * trailing overflow that scans it. The open library is marked in the amber accent. See CLAUDE.md
 * section 13.
 */
@Composable
private fun NavigationRail(
    current: Destination,
    openLibraryKey: String?,
    libraries: List<Library>,
    scanActivities: List<ServerActivity>,
    narrow: Boolean,
    backdrop: LiquidBackdrop,
    onHome: () -> Unit,
    onSelect: (Destination) -> Unit,
    onOpenLibrary: (Library) -> Unit,
    onScanLibrary: (Library) -> Unit,
    onMoveLibrary: (from: Int, to: Int) -> Unit,
    onGrantLibraryAccess: (email: String, libraryKeys: List<String>) -> Unit,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = Modifier
            .width(if (narrow) 176.dp else 236.dp)
            .fillMaxHeight()
            // The showcase chrome material: on Android Kyant refracts the featured backdrop through
            // the rail (optical lens + Fresnel edge); on desktop the Haze frost. A rounded right edge
            // makes it read as a floating pane (Kyant's lens also requires a corner-based shape). The
            // CHROME role owns the calibrated tint that keeps labels legible over bright artwork.
            .material(
                GlassRole.CHROME,
                RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                backdrop,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Destination.entries.forEach { entry ->
            val selected = entry == current && (entry != Destination.HOME || openLibraryKey == null)
            RailNavRow(
                icon = entry.icon,
                label = entry.label,
                selected = selected,
                onClick = { if (entry == Destination.HOME) onHome() else onSelect(entry) },
            )
        }

        HorizontalDivider(
            color = colours.border,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )

        PlexText(
            text = "Libraries",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            itemsIndexed(libraries, key = { _, library -> library.key }) { index, library ->
                LibraryNavRow(
                    library = library,
                    selected = openLibraryKey == library.key,
                    canMoveUp = index > 0,
                    canMoveDown = index < libraries.lastIndex,
                    allLibraries = libraries,
                    onClick = { onOpenLibrary(library) },
                    onScan = { onScanLibrary(library) },
                    onMoveUp = { onMoveLibrary(index, index - 1) },
                    onMoveDown = { onMoveLibrary(index, index + 1) },
                    onGrantAccess = onGrantLibraryAccess,
                )
            }
        }

        // The always-visible live scan read-out, pinned below the library list like Plex's own
        // activity strip. Hidden when the server reports no running jobs. See CLAUDE.md section 5.
        if (scanActivities.isNotEmpty()) {
            ScanningSection(
                activities = scanActivities,
                modifier = Modifier.padding(top = Spacing.xs),
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
 * The full-bleed field the whole shell floats on and the single Haze source the navigation frosts.
 * The featured artwork under a heavy scrim, so the frosted nav carries the artwork's colour (real
 * liquid glass) while staying a dark, stable bed for the labels; a plain ground when nothing plays.
 */
@Composable
private fun AmbientBackdrop(
    server: ActiveServer,
    item: MediaItem?,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Box(modifier.background(PlexTheme.colours.backgroundBrush())) {
        val art = item?.let {
            server.urls.artwork(
                it.artPath ?: it.thumbPath,
                ArtworkSize.BACKDROP_WIDTH,
                ArtworkSize.BACKDROP_HEIGHT,
            )
        }
        if (art != null) {
            Artwork(
                url = art,
                contentDescription = null,
                fallbackTitle = "",
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(colours.background.copy(alpha = 0.42f)))
        }
    }
}

/** A top-level destination in the rail: icon, label, and an accent pill when it is the one open. */
@Composable
private fun RailNavRow(
    icon: PlexIconKind,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    // Unselected chrome reads at near-primary strength so every destination is plainly legible;
    // the amber selected state is what stands out, not a fight to read the rest.
    val tint = if (selected) colours.accent else colours.textPrimary.copy(alpha = 0.82f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.glassSmall)
            .background(
                if (selected) colours.accent.copy(alpha = 0.16f) else Color.Transparent,
                Radius.glassSmall,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlexIcon(kind = icon, tint = tint, size = 22.dp)
        PlexText(text = label, style = PlexTheme.type.label, colour = tint, maxLines = 1)
    }
}

/**
 * A library row in the rail: the title selects it, the up/down controls reorder it (persisted by
 * the view model, and D-pad/keyboard friendly for television), and the trailing overflow scans it
 * or opens the grant-access dialog. See CLAUDE.md section 5 and 13.
 */
@Composable
private fun LibraryNavRow(
    library: Library,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    allLibraries: List<Library>,
    onClick: () -> Unit,
    onScan: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onGrantAccess: (email: String, libraryKeys: List<String>) -> Unit,
) {
    val colours = PlexTheme.colours
    val tint = if (selected) colours.accent else colours.textPrimary
    var menuOpen by remember { mutableStateOf(false) }
    var grantOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.glassSmall)
            .background(
                if (selected) colours.accent.copy(alpha = 0.16f) else Color.Transparent,
                Radius.glassSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlexText(
            text = library.title,
            style = PlexTheme.type.label,
            colour = tint,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
                .padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs),
        )
        ReorderButton(up = true, enabled = canMoveUp, onClick = onMoveUp)
        ReorderButton(up = false, enabled = canMoveDown, onClick = onMoveDown)
        Box {
            OverflowButton(onClick = { menuOpen = true })
            LibraryOverflowMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onScan = { menuOpen = false; onScan() },
                onGrantAccess = { menuOpen = false; grantOpen = true },
            )
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
 * A small up or down chevron control for library reordering. A 28 dp focus target so a remote or
 * keyboard lands on it cleanly; dimmed and non-interactive at the ends of the list.
 */
@Composable
private fun ReorderButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) {
        PlexTheme.colours.textSecondary
    } else {
        PlexTheme.colours.textSecondary.copy(alpha = 0.3f)
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .then(
                if (enabled) {
                    Modifier.plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
                } else {
                    Modifier
                },
            )
            .clip(Radius.glassSmall),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val yTip = if (up) h * 0.38f else h * 0.62f
            val ySide = if (up) h * 0.62f else h * 0.38f
            val sw = w * 0.12f
            drawLine(tint, Offset(w * 0.26f, ySide), Offset(w * 0.5f, yTip), sw, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.5f, yTip), Offset(w * 0.74f, ySide), sw, StrokeCap.Round)
        }
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
 * The bottom glass bar for compact. The four fixed destinations plus a Libraries entry that raises
 * a glass sheet of every library, so the bar stays a stable four-and-one rather than growing a tab
 * per library. See CLAUDE.md section 13.
 */
@Composable
private fun NavigationBottomBar(
    current: Destination,
    openLibraryKey: String?,
    librariesOpen: Boolean,
    backdrop: LiquidBackdrop,
    onHome: () -> Unit,
    onSelect: (Destination) -> Unit,
    onOpenLibraries: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Same chrome material as the rail: Kyant refraction on Android, Haze frost on desktop,
            // translucent so the content beneath stays visible under the bar. Rounded top edge for a
            // floating pane (and Kyant's lens needs a corner-based shape).
            .material(
                GlassRole.CHROME,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                backdrop,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { entry ->
            val selected = !librariesOpen && entry == current &&
                (entry != Destination.HOME || openLibraryKey == null)
            BottomNavItem(
                icon = entry.icon,
                label = entry.label,
                selected = selected,
                onClick = { if (entry == Destination.HOME) onHome() else onSelect(entry) },
            )
        }
        BottomNavItem(
            icon = null,
            label = "Libraries",
            selected = librariesOpen,
            onClick = onOpenLibraries,
        )
    }
}

/** A bottom-bar entry: icon over label with an accent pill when selected. A null [icon] is the
 * Libraries entry, drawn as a three-line list glyph. */
@Composable
private fun BottomNavItem(
    icon: PlexIconKind?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    val tint = if (selected) colours.accent else colours.textPrimary.copy(alpha = 0.82f)
    Column(
        modifier = Modifier
            .plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.glassSmall)
            .background(
                if (selected) colours.accent.copy(alpha = 0.16f) else Color.Transparent,
                Radius.glassSmall,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) PlexIcon(kind = icon, tint = tint, size = 24.dp) else LibrariesGlyph(tint)
        Spacer(Modifier.height(Spacing.xxs))
        PlexText(text = label, style = PlexTheme.type.caption, colour = tint, maxLines = 1)
    }
}

/**
 * The compact Libraries sheet: a scrim that dismisses on tap and a bottom-anchored glass panel
 * listing every library, each selecting on tap with a trailing overflow that scans it.
 */
@Composable
private fun LibrarySheet(
    libraries: List<Library>,
    openLibraryKey: String?,
    scanActivities: List<ServerActivity>,
    onDismiss: () -> Unit,
    onOpenLibrary: (Library) -> Unit,
    onScanLibrary: (Library) -> Unit,
    onGrantLibraryAccess: (email: String, libraryKeys: List<String>) -> Unit,
) {
    val colours = PlexTheme.colours
    Box(
        Modifier
            .fillMaxSize()
            .background(colours.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .material(GlassRole.SHEET, Radius.glass)
                // Swallow taps on the panel itself so only the scrim dismisses.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(Radius.pill)
                    .background(colours.border),
            )
            Spacer(Modifier.height(Spacing.xs))
            if (scanActivities.isNotEmpty()) {
                ScanningSection(activities = scanActivities)
            }
            SectionHeader("Libraries")
            libraries.forEach { library ->
                SheetLibraryRow(
                    library = library,
                    selected = openLibraryKey == library.key,
                    allLibraries = libraries,
                    onClick = { onOpenLibrary(library) },
                    onScan = { onScanLibrary(library) },
                    onGrantAccess = onGrantLibraryAccess,
                )
            }
        }
    }
}

/** A library row inside the compact sheet: the title selects it, the overflow scans it or opens
 * the grant-access dialog. */
@Composable
private fun SheetLibraryRow(
    library: Library,
    selected: Boolean,
    allLibraries: List<Library>,
    onClick: () -> Unit,
    onScan: () -> Unit,
    onGrantAccess: (email: String, libraryKeys: List<String>) -> Unit,
) {
    val colours = PlexTheme.colours
    val tint = if (selected) colours.accent else colours.textPrimary
    var menuOpen by remember { mutableStateOf(false) }
    var grantOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.glassSmall)
            .background(
                if (selected) colours.accent.copy(alpha = 0.16f) else colours.surface,
                Radius.glassSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlexText(
            text = library.title,
            style = PlexTheme.type.label,
            colour = tint,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .plexFocusable(shape = Radius.glassSmall, onClick = onClick, scaleOnFocus = false)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
        Box {
            OverflowButton(onClick = { menuOpen = true })
            LibraryOverflowMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onScan = { menuOpen = false; onScan() },
                onGrantAccess = { menuOpen = false; grantOpen = true },
            )
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

/** A per-library overflow menu: scan the library's files, or grant an account access to it. */
@Composable
private fun LibraryOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
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
