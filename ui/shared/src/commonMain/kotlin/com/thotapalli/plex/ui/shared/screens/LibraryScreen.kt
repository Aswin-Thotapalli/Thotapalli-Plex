package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.api.ServerActivity
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CollectionTile
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.LibraryState
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PosterGrid
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.rememberArtworkPrefetch
import com.thotapalli.plex.ui.shared.SkeletonPosterGrid
import com.thotapalli.plex.ui.shared.TopBarIconButton
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.plexFocusable
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Library: a poster grid of collections (first, with a stacked poster treatment) then individual
 * titles. The order is chosen through the Sort control and the results are narrowed through the
 * quick Unwatched-only filter and the Filter sheet (genre plus the Unwatched toggle). The owner has
 * authorised overriding CLAUDE.md section 14's otherwise-fixed sort. See CLAUDE.md section 14.
 *
 * The screen is a clean, modern surface: a solid ground, a header carrying a circular back
 * control and the library title in the display weight, and a wall of poster tiles. The library
 * actions — Scan, Sort, the Unwatched-only quick filter and Filter — sit as pills on the right of
 * the header, an active filter turning solid amber. See CLAUDE.md section 12.
 *
 * [onBack] is the up action for the library root (back to Home). It is optional so the shell can
 * wire it without breaking the existing call site; inside an open collection the header back
 * control returns to the library through [onCloseCollection] instead.
 */
@Composable
fun LibraryScreen(
    server: ActiveServer,
    state: LibraryState,
    onItemClick: (MediaItem) -> Unit,
    onCollectionClick: (MediaCollection) -> Unit,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onSortChange: (String) -> Unit,
    onGenreChange: (String?) -> Unit,
    onCloseCollection: () -> Unit,
    onScanLibrary: (Library) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    onBack: (() -> Unit)? = null,
    scanActivity: ServerActivity? = null,
    modifier: Modifier = Modifier,
) {
    val insideCollection = state.openCollection != null
    // On a television the first tile in the grid takes focus on entry, so the remote lands
    // somewhere. Collections are emitted first, so the index-0 collection is the true first
    // tile; where no collections show, the index-0 poster is. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = PlexTheme.sizeClass.isTelevision)
    val collectionsShown = !insideCollection && state.collections.isNotEmpty()

    // The header's up control returns to the library while a collection is open, and up to Home
    // at the library root. The root action is optional, so a caller that has not wired it yet
    // simply leaves the control inert rather than failing to build.
    val backAction: () -> Unit = if (insideCollection) onCloseCollection else (onBack ?: {})

    val title = state.openCollection?.title ?: state.library.title
    val subtitle: String? = when {
        insideCollection -> state.library.title
        else -> state.items.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "title" else "titles"}" }
    }

    // Phone: the approved mobile layout — a centred header, a centred pair of action pills, and a
    // three-up poster wall with an on-face overflow control. The wide layout below is unchanged and
    // still serves tablet, desktop and television. See CLAUDE.md sections 12 and 13.
    if (PlexTheme.sizeClass == SizeClass.COMPACT) {
        CompactLibrary(
            server = server,
            state = state,
            title = title,
            subtitle = subtitle,
            insideCollection = insideCollection,
            backAction = backAction,
            onItemClick = onItemClick,
            onCollectionClick = onCollectionClick,
            onUnwatchedOnlyChange = onUnwatchedOnlyChange,
            onSortChange = onSortChange,
            onGenreChange = onGenreChange,
            onScanLibrary = onScanLibrary,
            itemActions = itemActions,
            scanActivity = scanActivity,
            modifier = modifier,
        )
        return
    }

    // The filter sheet overlays the whole screen when open. It is hosted here as a sibling of the
    // content column so it draws above the poster wall. See CLAUDE.md section 12.
    var filterSheetOpen by remember { mutableStateOf(false) }

    // A solid screen ground: the library reads as a clean, modern surface rather than a pane of
    // glass. See CLAUDE.md section 12.
    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().material(GlassRole.GROUND)) {
        // The header: a circular back control, the library title beside it in the display weight,
        // and — at the library root — the Scan and Unwatched-only pills on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PlexTheme.sizeClass.screenPadding,
                    vertical = Spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TopBarIconButton(kind = PlexIconKind.BACK, onClick = backAction)
                Column {
                    PlexText(text = title, style = PlexTheme.type.display, maxLines = 1)
                    if (subtitle != null) {
                        PlexText(
                            text = subtitle,
                            style = PlexTheme.type.caption,
                            colour = PlexTheme.colours.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (!insideCollection) {
                Spacer(Modifier.width(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // A per-library scan, so the owner can pull in newly added files without
                    // leaving the app. See CLAUDE.md section 5.
                    IconChip(
                        label = "Scan",
                        glyph = ChipGlyph.SCAN,
                        selected = false,
                        onClick = { onScanLibrary(state.library) },
                    )
                    // The sort control: a pill carrying the current order that opens a menu of the
                    // supported Plex sorts. The owner has authorised overriding the fixed title sort.
                    SortChip(sort = state.sort, onSortChange = onSortChange)
                    // The quick Unwatched-only filter. Solid amber with inverted content when it is
                    // on, so the active state is unmistakable and the accent marks it and nothing else.
                    IconChip(
                        label = "Unwatched only",
                        glyph = ChipGlyph.FILTER,
                        selected = state.unwatchedOnly,
                        onClick = { onUnwatchedOnlyChange(!state.unwatchedOnly) },
                    )
                    // The filter affordance: opens the filter sheet (genre chooser plus the
                    // Unwatched toggle). Reads as active while a genre is chosen.
                    IconChip(
                        label = "Filter",
                        glyph = ChipGlyph.SLIDERS,
                        selected = state.selectedGenre != null,
                        onClick = { filterSheetOpen = true },
                    )
                }
            }
        }

        // A live scan of the library's files, driven by the server's running jobs: the actual scan
        // detail (what Plex is doing, and on what) plus a percentage and a slim amber bar, directly
        // under the header. See CLAUDE.md section 5.
        if (scanActivity != null && !insideCollection) {
            LibraryScanDetails(
                activity = scanActivity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlexTheme.sizeClass.screenPadding, vertical = Spacing.xxs),
            )
        }

        if (state.loading && state.items.isEmpty() && state.collections.isEmpty()) {
            SkeletonPosterGrid(Modifier.fillMaxSize())
            return@Column
        }

        // The scrolling grid is the source the frosted navigation samples. A prefetch warms Coil's
        // cache for the posters just below the fold so a fast scroll shows loaded art, not shimmer
        // (§5, §13). URLs are index-aligned with the grid: collections first, then items.
        val gridState = rememberLazyGridState()
        val prefetchUrls = remember(state.collections, state.items, server) {
            state.collections.map {
                server.urls.artwork(it.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT)
            } + state.items.map {
                server.urls.artwork(it.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT)
            }
        }
        rememberArtworkPrefetch(prefetchUrls, gridState)
        PosterGrid(modifier = Modifier.glassSource(), state = gridState) {
            if (!insideCollection && state.collections.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Collections", Modifier.padding(top = Spacing.xs))
                }
                items(state.collections.size, key = { state.collections[it].ratingKey }) { index ->
                    val collection = state.collections[index]
                    CollectionTile(
                        collection = collection,
                        artworkUrl = server.urls.artwork(
                            collection.thumbPath,
                            ArtworkSize.POSTER_WIDTH,
                            ArtworkSize.POSTER_HEIGHT,
                        ),
                        onClick = { onCollectionClick(collection) },
                        modifier = Modifier
                            .staggeredEntrance(index, key = collection.ratingKey)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(state.library.title, Modifier.padding(top = Spacing.md))
                }
            }

            items(state.items.size, key = { state.items[it].ratingKey }) { index ->
                val item = state.items[index]
                PosterTile(
                    item = item,
                    artworkUrl = server.urls.artwork(
                        item.thumbPath,
                        ArtworkSize.POSTER_WIDTH,
                        ArtworkSize.POSTER_HEIGHT,
                    ),
                    onClick = { onItemClick(item) },
                    actions = itemActions(item),
                    modifier = Modifier
                        .staggeredEntrance(index, key = item.ratingKey)
                        .then(
                            if (!collectionsShown && index == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            if (state.items.isEmpty() && state.collections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PlexText(
                        text = if (state.unwatchedOnly) {
                            "Everything in this library has been watched."
                        } else {
                            "This library is empty."
                        },
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
    }

        if (filterSheetOpen && !insideCollection) {
            LibraryFilterSheet(
                genres = state.genres,
                selectedGenre = state.selectedGenre,
                unwatchedOnly = state.unwatchedOnly,
                onGenreChange = onGenreChange,
                onUnwatchedOnlyChange = onUnwatchedOnlyChange,
                onDismiss = { filterSheetOpen = false },
            )
        }
    }
}

/**
 * The phone (COMPACT) library. A centred header carrying a circular back control, the library
 * title and a one-line subtitle; a horizontally scrolling strip of action pills (Unwatched-only,
 * Sort, Filter and Scan); the live scan bar; then a three-column poster wall. Collections lead the
 * wall, then the titles; a long-press on any poster opens its item-actions menu, the same gesture
 * the wide layout uses. See the approved mobile mockups and CLAUDE.md sections 12–14.
 */
@Composable
private fun CompactLibrary(
    server: ActiveServer,
    state: LibraryState,
    title: String,
    subtitle: String?,
    insideCollection: Boolean,
    backAction: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onCollectionClick: (MediaCollection) -> Unit,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onSortChange: (String) -> Unit,
    onGenreChange: (String?) -> Unit,
    onScanLibrary: (Library) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    scanActivity: ServerActivity?,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    var filterSheetOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().material(GlassRole.GROUND)) {
        // The header. A circular back control on the far left, the title and subtitle centred, and a
        // balancing spacer the width of the back button on the right so the title reads as centred.
        //
        // The mockup places a cast control on that right edge, but casting is permanently out of
        // scope (CLAUDE.md section 1: "do not add these, do not scaffold them, do not leave hooks
        // for them"), so no cast control is added; the spacer holds its place for symmetry only.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarIconButton(kind = PlexIconKind.BACK, onClick = backAction)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlexText(text = title, style = PlexTheme.type.title, maxLines = 1)
                if (subtitle != null) {
                    PlexText(
                        text = subtitle,
                        style = PlexTheme.type.caption,
                        colour = colours.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.size(Layout.iconButton))
        }

        // The two library actions, centred as a pair of pills beneath the header. At the library
        // root only; an open collection carries neither. The filter turns solid amber when it is on.
        if (!insideCollection) {
            // Four actions do not fit centred on a narrow phone, so the pill strip scrolls
            // horizontally rather than clipping. See CLAUDE.md sections 12–13.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.xxs, bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(
                    label = "Unwatched only",
                    glyph = ChipGlyph.FILTER,
                    selected = state.unwatchedOnly,
                    onClick = { onUnwatchedOnlyChange(!state.unwatchedOnly) },
                )
                SortChip(sort = state.sort, onSortChange = onSortChange)
                IconChip(
                    label = "Filter",
                    glyph = ChipGlyph.SLIDERS,
                    selected = state.selectedGenre != null,
                    onClick = { filterSheetOpen = true },
                )
                IconChip(
                    label = "Scan",
                    glyph = ChipGlyph.SCAN,
                    selected = false,
                    onClick = { onScanLibrary(state.library) },
                )
            }
        }

        // The live scan read-out, driven by the server's running job: the actual detail (what Plex
        // is doing, and on what), a percentage, and a slim bar — directly under the pills.
        if (scanActivity != null && !insideCollection) {
            LibraryScanDetails(
                activity = scanActivity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
            )
        }

        if (state.loading && state.items.isEmpty() && state.collections.isEmpty()) {
            SkeletonPosterGrid(Modifier.fillMaxSize())
            return@Column
        }

        val showCollections = !insideCollection && state.collections.isNotEmpty()

        // Warm the posters just below the fold so a fast phone scroll shows loaded art (§5, §13).
        val gridState = rememberLazyGridState()
        val prefetchUrls = remember(state.collections, state.items, server) {
            state.collections.map {
                server.urls.artwork(it.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT)
            } + state.items.map {
                server.urls.artwork(it.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT)
            }
        }
        rememberArtworkPrefetch(prefetchUrls, gridState)

        // A three-up poster wall. The grid is the source the frosted bottom bar samples, and its
        // bottom padding clears the floating tab bar so the last row is never trapped beneath it.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize().glassSource(),
            // Minimal edges and gaps so the three posters are as large as possible (#4).
            contentPadding = PaddingValues(
                start = Spacing.xxs,
                end = Spacing.xxs,
                top = Spacing.xxs,
                bottom = 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (showCollections) {
                items(state.collections.size, key = { state.collections[it].ratingKey }) { index ->
                    val collection = state.collections[index]
                    CollectionTile(
                        collection = collection,
                        artworkUrl = server.urls.artwork(
                            collection.thumbPath,
                            ArtworkSize.POSTER_WIDTH,
                            ArtworkSize.POSTER_HEIGHT,
                        ),
                        onClick = { onCollectionClick(collection) },
                        modifier = Modifier.staggeredEntrance(index, key = collection.ratingKey),
                    )
                }
            }

            val collectionsCount = if (showCollections) state.collections.size else 0
            items(state.items.size, key = { state.items[it].ratingKey }) { index ->
                val item = state.items[index]
                CompactPosterCell(
                    item = item,
                    artworkUrl = server.urls.artwork(
                        item.thumbPath,
                        ArtworkSize.POSTER_WIDTH,
                        ArtworkSize.POSTER_HEIGHT,
                    ),
                    onClick = { onItemClick(item) },
                    actions = itemActions(item),
                    modifier = Modifier.staggeredEntrance(collectionsCount + index, key = item.ratingKey),
                )
            }

            if (state.items.isEmpty() && state.collections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PlexText(
                        text = if (state.unwatchedOnly) {
                            "Everything in this library has been watched."
                        } else {
                            "This library is empty."
                        },
                        colour = colours.textSecondary,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
    }

        if (filterSheetOpen && !insideCollection) {
            LibraryFilterSheet(
                genres = state.genres,
                selectedGenre = state.selectedGenre,
                unwatchedOnly = state.unwatchedOnly,
                onGenreChange = onGenreChange,
                onUnwatchedOnlyChange = onUnwatchedOnlyChange,
                onDismiss = { filterSheetOpen = false },
            )
        }
    }
}

/**
 * The live scan read-out for a library, mirroring Plex's own: the job title (e.g. "Scanning the
 * Movies library"), the current detail underneath (the sub-step or path the server reports), a
 * percentage, and a slim amber progress bar. Shown directly under the header while a scan runs.
 * See CLAUDE.md section 5.
 */
@Composable
private fun LibraryScanDetails(
    activity: ServerActivity,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val fraction = activity.progress.coerceIn(0f, 1f)
    val percent = (fraction * 100f).toInt()
    Column(
        modifier = modifier
            .material(GlassRole.CARD, Radius.glassSmall)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlexText(
                text = activity.title.ifBlank { "Scanning" },
                style = PlexTheme.type.label,
                colour = colours.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            PlexText(
                text = "$percent%",
                style = PlexTheme.type.label,
                colour = colours.accent,
                maxLines = 1,
            )
        }
        val subtitle = activity.subtitle
        if (!subtitle.isNullOrBlank()) {
            PlexText(
                text = subtitle,
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                maxLines = 2,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(Radius.pill),
            color = colours.accent,
            trackColor = colours.surface,
        )
    }
}

/**
 * One poster cell for the phone grid: the shared [PosterTile] for the art, shadow, watched badge,
 * progress and caption. The item's action menu (Mark watched / Download / Delete etc.) opens on a
 * long-press of the poster rather than a visible "⋮" — the actions are handed straight to
 * [PosterTile], whose [ItemMenuHost] wrapper hosts the long-press (touch) and right-click (desktop)
 * gestures. A plain tap still opens the item. See CLAUDE.md sections 12 and 14.
 */
@Composable
private fun CompactPosterCell(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    PosterTile(
        item = item,
        artworkUrl = artworkUrl,
        onClick = onClick,
        actions = actions,
        modifier = modifier,
    )
}

/** The library-header pills carry a small line glyph drawn to match the app's own icon set. */
private enum class ChipGlyph { SCAN, FILTER, SORT, SLIDERS }

/**
 * A header action pill: a leading line glyph and a label on the CHIP material, turning to a solid
 * amber pill with inverted content when [selected]. The accent marks selection and nothing else.
 * See CLAUDE.md section 12.
 */
@Composable
private fun IconChip(
    label: String,
    glyph: ChipGlyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val content = when {
        selected && colours.isDark -> colours.background
        selected -> colours.surface
        else -> colours.textSecondary
    }
    Row(
        modifier = modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick)
            .clip(Radius.pill)
            .then(
                if (selected) {
                    Modifier
                        .background(colours.accent, Radius.pill)
                        .border(1.dp, colours.accent, Radius.pill)
                } else {
                    Modifier.material(GlassRole.CHIP, shape = Radius.pill)
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ChipGlyphIcon(glyph = glyph, tint = content, size = 16.dp)
        PlexText(text = label, style = PlexTheme.type.label, colour = content)
    }
}

/** One selectable library order: the label shown to the owner and the Plex sort string it maps to. */
private data class LibrarySort(val label: String, val plex: String)

/**
 * The supported library orders. Title ascending is the default and matches the long-standing fixed
 * sort; the owner has authorised the remaining orders. Each carries the exact Plex sort string the
 * server endpoint expects (see CLAUDE.md section 5, `sort=`).
 */
private val LIBRARY_SORTS = listOf(
    LibrarySort("Title A–Z", "titleSort:asc"),
    LibrarySort("Title Z–A", "titleSort:desc"),
    LibrarySort("Recently Added", "addedAt:desc"),
    LibrarySort("Year", "year:desc"),
    LibrarySort("Rating", "rating:desc"),
)

/**
 * The sort control: a CHIP-material pill carrying a sort glyph and the current order's label, which
 * opens a dropdown of the supported orders. The current order is drawn in the accent; choosing one
 * calls [onSortChange] with its Plex sort string. See CLAUDE.md sections 12 and 14.
 */
@Composable
private fun SortChip(
    sort: String,
    onSortChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    var expanded by remember { mutableStateOf(false) }
    val current = LIBRARY_SORTS.firstOrNull { it.plex == sort } ?: LIBRARY_SORTS.first()

    Box(modifier) {
        Row(
            modifier = Modifier
                .plexFocusable(shape = Radius.pill, onClick = { expanded = true }, scaleOnFocus = false)
                .clip(Radius.pill)
                .material(GlassRole.CHIP, shape = Radius.pill)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ChipGlyphIcon(glyph = ChipGlyph.SORT, tint = colours.textSecondary, size = 16.dp)
            PlexText(text = current.label, style = PlexTheme.type.label, colour = colours.textSecondary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.material(GlassRole.SHEET, Radius.glassSmall),
        ) {
            LIBRARY_SORTS.forEach { option ->
                val selected = option.plex == current.plex
                DropdownMenuItem(
                    text = {
                        PlexText(
                            text = option.label,
                            style = PlexTheme.type.label,
                            colour = if (selected) colours.accent else colours.textPrimary,
                            maxLines = 1,
                        )
                    },
                    onClick = { expanded = false; onSortChange(option.plex) },
                )
            }
        }
    }
}

/**
 * The filter sheet, dressed as a sheet of liquid glass at the bottom of the screen (sixteen radius,
 * per section 12). It carries the Unwatched-only toggle and — when the library exposes genres — a
 * genre chooser led by an "All genres" row that clears the selection. Tapping the scrim dismisses.
 * Kept deliberately modest: genre plus the existing Unwatched toggle, nothing more. See CLAUDE.md
 * sections 12 and 14.
 */
@Composable
private fun LibraryFilterSheet(
    genres: List<String>,
    selectedGenre: String?,
    unwatchedOnly: Boolean,
    onGenreChange: (String?) -> Unit,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colours.scrim)
            .plexFocusable(shape = Radius.sheet, onClick = onDismiss, scaleOnFocus = false),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .material(GlassRole.SHEET, shape = Radius.sheet)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlexText(
                    text = "Filter",
                    style = PlexTheme.type.title,
                    colour = colours.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                // A clear affordance, shown only while something is filtered. It resets both the
                // genre and the Unwatched toggle in one tap.
                if (selectedGenre != null || unwatchedOnly) {
                    Row(
                        modifier = Modifier
                            .plexFocusable(
                                shape = Radius.pill,
                                onClick = { onGenreChange(null); onUnwatchedOnlyChange(false) },
                            )
                            .clip(Radius.pill)
                            .material(GlassRole.CHIP, shape = Radius.pill)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    ) {
                        PlexText(text = "Clear", style = PlexTheme.type.label, colour = colours.textSecondary)
                    }
                }
            }

            // The existing quick filter, mirrored inside the sheet against the same callback.
            FilterToggleRow(
                label = "Unwatched only",
                selected = unwatchedOnly,
                onClick = { onUnwatchedOnlyChange(!unwatchedOnly) },
            )

            // The genre chooser, only when the library exposes genres. It can be long, so it scrolls
            // within a capped height rather than growing the sheet past the screen.
            if (genres.isNotEmpty()) {
                PlexText(
                    text = "Genre",
                    style = PlexTheme.type.label,
                    colour = colours.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    FilterToggleRow(
                        label = "All genres",
                        selected = selectedGenre == null,
                        onClick = { onGenreChange(null) },
                    )
                    genres.forEach { genre ->
                        FilterToggleRow(
                            label = genre,
                            selected = genre == selectedGenre,
                            onClick = { onGenreChange(genre) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One selectable row inside the filter sheet: the label on a full-width tap target that turns to a
 * solid amber card when selected, with inverted content. The accent marks selection and nothing
 * else. See CLAUDE.md section 12.
 */
@Composable
private fun FilterToggleRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.card)
            .then(if (selected) Modifier.background(colours.accent, Radius.card) else Modifier)
            .padding(Spacing.sm),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = when {
                selected && colours.isDark -> colours.background
                selected -> colours.surface
                else -> colours.textPrimary
            },
        )
    }
}

/** Draws the small line glyph for a [ChipGlyph] on a Canvas, so it takes the chip's content tint. */
@Composable
private fun ChipGlyphIcon(glyph: ChipGlyph, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.10f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            ChipGlyph.SCAN -> drawScanGlyph(tint, stroke)
            ChipGlyph.FILTER -> drawFilterGlyph(tint, stroke)
            ChipGlyph.SORT -> drawSortGlyph(tint, stroke)
            ChipGlyph.SLIDERS -> drawSlidersGlyph(tint, stroke)
        }
    }
}

/** A scanner viewfinder: four corner brackets with a line sweeping across, reading as "scan". */
private fun DrawScope.drawScanGlyph(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val o = 0.22f // the outer corner, in from each edge
    val a = 0.42f // how far each bracket arm reaches
    // top-left
    drawLine(tint, Offset(w * o, h * o), Offset(w * o, h * a), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * o, h * o), Offset(w * a, h * o), stroke.width, stroke.cap)
    // top-right
    drawLine(tint, Offset(w * (1 - o), h * o), Offset(w * (1 - o), h * a), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * (1 - o), h * o), Offset(w * (1 - a), h * o), stroke.width, stroke.cap)
    // bottom-left
    drawLine(tint, Offset(w * o, h * (1 - o)), Offset(w * o, h * (1 - a)), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * o, h * (1 - o)), Offset(w * a, h * (1 - o)), stroke.width, stroke.cap)
    // bottom-right
    drawLine(tint, Offset(w * (1 - o), h * (1 - o)), Offset(w * (1 - o), h * (1 - a)), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * (1 - o), h * (1 - o)), Offset(w * (1 - a), h * (1 - o)), stroke.width, stroke.cap)
    // the sweeping scan line
    drawLine(tint, Offset(w * 0.14f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), stroke.width, stroke.cap)
}

/** A funnel: the conventional filter glyph. */
private fun DrawScope.drawFilterGlyph(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val funnel = Path().apply {
        moveTo(w * 0.20f, h * 0.26f)
        lineTo(w * 0.80f, h * 0.26f)
        lineTo(w * 0.56f, h * 0.54f)
        lineTo(w * 0.56f, h * 0.78f)
        lineTo(w * 0.44f, h * 0.70f)
        lineTo(w * 0.44f, h * 0.54f)
        close()
    }
    drawPath(funnel, tint, style = stroke)
}

/** Three lines of decreasing length: the conventional "sort" glyph. */
private fun DrawScope.drawSortGlyph(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.22f, h * 0.30f), Offset(w * 0.80f, h * 0.30f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.22f, h * 0.70f), Offset(w * 0.44f, h * 0.70f), stroke.width, stroke.cap)
}

/** Two rails, each with a knob, on opposite sides: the conventional "filter/adjust" glyph. */
private fun DrawScope.drawSlidersGlyph(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val knob = w * 0.10f
    drawLine(tint, Offset(w * 0.18f, h * 0.36f), Offset(w * 0.82f, h * 0.36f), stroke.width, stroke.cap)
    drawCircle(tint, radius = knob, center = Offset(w * 0.64f, h * 0.36f))
    drawLine(tint, Offset(w * 0.18f, h * 0.64f), Offset(w * 0.82f, h * 0.64f), stroke.width, stroke.cap)
    drawCircle(tint, radius = knob, center = Offset(w * 0.36f, h * 0.64f))
}

/** A pill. The accent marks selection and nothing else. See CLAUDE.md section 12. */
@Composable
internal fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .clip(Radius.pill)
            // Selected is a solid amber pill; unselected takes the calibrated CHIP glass, so the chip
            // reads as chrome without competing with the poster art around it. See CLAUDE.md §12.
            .then(
                if (selected) {
                    Modifier
                        .background(colours.accent, Radius.pill)
                        .border(1.dp, colours.accent, Radius.pill)
                } else {
                    Modifier.material(GlassRole.CHIP, shape = Radius.pill)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = when {
                selected && colours.isDark -> colours.background
                selected -> colours.surface
                else -> colours.textSecondary
            },
        )
    }
}
