package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.OnDarkSurface
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.HomeHero
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.LibraryChipCard
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.PrimaryButton
import com.thotapalli.plex.ui.shared.ProgressBar
import com.thotapalli.plex.ui.shared.SecondaryButton
import com.thotapalli.plex.ui.shared.formatDuration
import com.thotapalli.plex.ui.shared.plexFocusable
import com.thotapalli.plex.ui.shared.primaryLine
import com.thotapalli.plex.ui.shared.remainingLabel
import com.thotapalli.plex.ui.shared.secondaryLine
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.ViewAllButton
import com.thotapalli.plex.ui.shared.WideProgressTile
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.material.cinematicTexture
import com.thotapalli.plex.ui.shared.motion.kenBurns
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Home: a spotlight hero for the single title most worth resuming, one horizontal poster rail per
 * library beneath it, and a Continue Watching rail of wide progress tiles at the foot. Home reads
 * as a lit shelf of content rather than a list of folders.
 *
 * Compact (phone) gets its own scrolling layout matching the mobile mockups — a shallow hero card,
 * a Libraries chip rail, Continue Watching, then a poster rail per library. Every other size class
 * keeps the wide layout. See CLAUDE.md section 14.
 */
@Composable
fun HomeScreen(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onItemClick: (MediaItem) -> Unit,
    onLibraryClick: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    modifier: Modifier = Modifier,
) {
    if (PlexTheme.sizeClass == SizeClass.TELEVISION) {
        TvHome(
            server = server,
            continueWatching = continueWatching,
            libraries = libraries,
            libraryPreviews = libraryPreviews,
            onItemClick = onItemClick,
            onLibraryClick = onLibraryClick,
            onPlay = onPlay,
            itemActions = itemActions,
            modifier = modifier,
        )
        return
    }

    if (PlexTheme.sizeClass == SizeClass.COMPACT) {
        CompactHome(
            server = server,
            continueWatching = continueWatching,
            libraries = libraries,
            libraryPreviews = libraryPreviews,
            onItemClick = onItemClick,
            onLibraryClick = onLibraryClick,
            onPlay = onPlay,
            itemActions = itemActions,
            modifier = modifier,
        )
    } else {
        WideHome(
            server = server,
            continueWatching = continueWatching,
            libraries = libraries,
            libraryPreviews = libraryPreviews,
            onItemClick = onItemClick,
            onLibraryClick = onLibraryClick,
            onPlay = onPlay,
            itemActions = itemActions,
            modifier = modifier,
        )
    }
}

/**
 * The wide (tablet, desktop, television) Home: the spotlight hero, a poster rail per library, and a
 * Continue Watching rail at the foot. See CLAUDE.md section 14.
 */
@Composable
private fun WideHome(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onItemClick: (MediaItem) -> Unit,
    onLibraryClick: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val pad = sizeClass.screenPadding
    val posterWidth = if (sizeClass.isTelevision) 168.dp else 152.dp
    val wideWidth = if (sizeClass.isTelevision) 360.dp else 300.dp

    val featured = continueWatching.firstOrNull()
    // The featured title is the hero; the rail holds the others, so nothing is shown twice.
    val continueRail = continueWatching.drop(1)

    // The viewport height bounds the hero so its action buttons stay clear of the bottom edge and
    // the rails below remain in view on a short window.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .material(GlassRole.GROUND),
    ) {
        val viewportHeight = maxHeight

        LazyColumn(
            // The scrolling content is the Haze source the frosted navigation samples on desktop.
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(top = pad, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            if (featured != null) {
                item(key = "hero") {
                    HomeHero(
                        item = featured,
                        artworkUrl = server.urls.artwork(
                            featured.artPath ?: featured.thumbPath,
                            ArtworkSize.BACKDROP_WIDTH,
                            ArtworkSize.BACKDROP_HEIGHT,
                        ),
                        onPlay = { onPlay(featured, featured.viewOffsetMs) },
                        onDetails = { onItemClick(featured) },
                        viewportHeight = viewportHeight,
                        dotCount = continueWatching.size,
                        activeDot = 0,
                        modifier = Modifier.padding(horizontal = pad),
                    )
                }
            }

            // Continue Watching — the in-progress titles beyond the featured one, as wide tiles.
            // Sits directly under the hero and above the library rails; hidden when empty so the
            // library rails move up (CLAUDE.md section 14.2).
            if (continueRail.isNotEmpty()) {
                item(key = "cw") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SectionHeader(
                            title = "Continue Watching",
                            modifier = Modifier.padding(horizontal = pad),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = pad),
                        ) {
                            itemsIndexed(continueRail, key = { _, it -> "cw-" + it.ratingKey }) { i, item ->
                                WideProgressTile(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.artPath ?: item.thumbPath,
                                        ArtworkSize.WIDE_WIDTH,
                                        ArtworkSize.WIDE_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    actions = itemActions(item),
                                    isContinueWatching = true,
                                    modifier = Modifier.width(wideWidth).staggeredEntrance(i, key = item.ratingKey),
                                )
                            }
                        }
                    }
                }
            }

            // One horizontal poster rail per library — a header naming the library and a row of
            // its posters, art only, tapping through to the item's detail.
            if (libraries.isNotEmpty()) {
                items(libraries, key = { "lib-" + it.key }) { library ->
                    val previews = libraryPreviews[library.key].orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        RailHeader(
                            title = library.title,
                            subtitle = libraryKindLabel(library.kind),
                            onViewAll = { onLibraryClick(library) },
                            modifier = Modifier.padding(horizontal = pad),
                        )
                        if (previews.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                contentPadding = PaddingValues(horizontal = pad),
                            ) {
                                itemsIndexed(
                                    previews,
                                    key = { _, it -> library.key + "-" + it.ratingKey },
                                ) { i, item ->
                                    PosterTile(
                                        item = item,
                                        artworkUrl = server.urls.artwork(
                                            item.thumbPath,
                                            ArtworkSize.POSTER_WIDTH,
                                            ArtworkSize.POSTER_HEIGHT,
                                        ),
                                        onClick = { onItemClick(item) },
                                        actions = itemActions(item),
                                        showCaption = false,
                                        modifier = Modifier
                                            .width(posterWidth)
                                            .staggeredEntrance(i, key = item.ratingKey),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    PlexText(
                        text = "This server has no film or series libraries.",
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(pad),
                    )
                }
            }
        }
    }
}

/**
 * A library rail's header: the library name in title weight with its kind beneath, and a trailing
 * "View all ›" that opens the full library. The name and kind stack on the left; the whole thing is
 * a header, not a card. See CLAUDE.md section 14.
 */
@Composable
private fun RailHeader(
    title: String,
    subtitle: String,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            PlexText(text = title, style = PlexTheme.type.title, maxLines = 1)
            PlexText(
                text = subtitle,
                style = PlexTheme.type.caption,
                colour = PlexTheme.colours.textSecondary,
                maxLines = 1,
            )
        }
        ViewAllButton(onClick = onViewAll)
    }
}

private fun libraryKindLabel(kind: LibraryKind): String = when (kind) {
    LibraryKind.MOVIE -> "Movies"
    LibraryKind.SHOW -> "Series"
    LibraryKind.UNSUPPORTED -> "Library"
}

// --- compact (phone) Home ----------------------------------------------------------------

/** Compact poster and wide-tile widths, a touch smaller than the wide layout's. */
private val CompactPosterWidth = 132.dp
private val CompactWideWidth = 248.dp

/**
 * The mobile Home, top to bottom: a shallow hero card, a "Libraries" rail of chip-cards, a
 * Continue Watching rail of wide tiles, then a poster rail per library. One scrolling column, one
 * screen-edge inset. See CLAUDE.md section 14.
 */
@Composable
private fun CompactHome(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onItemClick: (MediaItem) -> Unit,
    onLibraryClick: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    modifier: Modifier = Modifier,
) {
    val pad = Spacing.md
    val featured = continueWatching.firstOrNull()
    // The featured title is the hero; the Continue Watching rail holds the rest, so nothing repeats.
    val continueRail = continueWatching.drop(1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .material(GlassRole.GROUND),
    ) {
        val viewportHeight = maxHeight

        LazyColumn(
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(top = pad, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // 1. Hero card — the top Continue Watching title, with an "Xm left" badge and the
            // queue dots beneath its actions.
            if (featured != null) {
                item(key = "hero") {
                    HomeHero(
                        item = featured,
                        artworkUrl = server.urls.artwork(
                            featured.artPath ?: featured.thumbPath,
                            ArtworkSize.BACKDROP_WIDTH,
                            ArtworkSize.BACKDROP_HEIGHT,
                        ),
                        onPlay = { onPlay(featured, featured.viewOffsetMs) },
                        onDetails = { onItemClick(featured) },
                        viewportHeight = viewportHeight,
                        dotCount = continueWatching.size,
                        activeDot = 0,
                        remainingBadge = true,
                        modifier = Modifier.padding(horizontal = pad),
                    )
                }
            }

            if (libraries.isEmpty()) {
                item(key = "empty") {
                    PlexText(
                        text = "This server has no film or series libraries.",
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(pad),
                    )
                }
            }

            // 2. Libraries — a rail of compact chip-cards, one per library.
            if (libraries.isNotEmpty()) {
                item(key = "libraries") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        CompactHeader(
                            title = "Libraries",
                            glyph = { tint -> LibrariesGlyph(tint = tint, modifier = Modifier.size(18.dp)) },
                            // No single "all libraries" screen exists, so "View all" opens the first
                            // library rather than sitting as a dead affordance. The block below only
                            // renders when libraries is non-empty, so there is always a first.
                            onViewAll = { libraries.firstOrNull()?.let(onLibraryClick) },
                            modifier = Modifier.padding(horizontal = pad),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = pad),
                        ) {
                            items(libraries, key = { "chip-" + it.key }) { library ->
                                LibraryChipCard(
                                    title = library.title,
                                    kind = library.kind,
                                    caption = libraryKindLabel(library.kind),
                                    onClick = { onLibraryClick(library) },
                                )
                            }
                        }
                    }
                }
            }

            // 3. Continue Watching — the in-progress titles beyond the featured one, as wide tiles.
            if (continueRail.isNotEmpty()) {
                item(key = "cw") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        // Continue Watching has no library to open, so no "View all" affordance is
                        // shown rather than leaving a dead no-op.
                        CompactHeader(
                            title = "Continue Watching",
                            modifier = Modifier.padding(horizontal = pad),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = pad),
                        ) {
                            itemsIndexed(continueRail, key = { _, it -> "cw-" + it.ratingKey }) { i, item ->
                                WideProgressTile(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.artPath ?: item.thumbPath,
                                        ArtworkSize.WIDE_WIDTH,
                                        ArtworkSize.WIDE_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    actions = itemActions(item),
                                    isContinueWatching = true,
                                    showOverflow = true,
                                    modifier = Modifier
                                        .width(CompactWideWidth)
                                        .staggeredEntrance(i, key = item.ratingKey),
                                )
                            }
                        }
                    }
                }
            }

            // 4. One poster rail per library, art only, tapping through to the item's detail.
            items(libraries, key = { "lib-" + it.key }) { library ->
                val previews = libraryPreviews[library.key].orEmpty()
                if (previews.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        CompactRailHeader(
                            title = library.title,
                            onOpen = { onLibraryClick(library) },
                            modifier = Modifier.padding(horizontal = pad),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = pad),
                        ) {
                            itemsIndexed(
                                previews,
                                key = { _, it -> library.key + "-" + it.ratingKey },
                            ) { i, item ->
                                PosterTile(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.thumbPath,
                                        ArtworkSize.POSTER_WIDTH,
                                        ArtworkSize.POSTER_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    actions = itemActions(item),
                                    showCaption = false,
                                    modifier = Modifier
                                        .width(CompactPosterWidth)
                                        .staggeredEntrance(i, key = item.ratingKey),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A compact section header: an optional leading [glyph] tinted to the text colour, the section
 * title in title weight, and a trailing "View all ›" when [onViewAll] is given.
 */
@Composable
private fun CompactHeader(
    title: String,
    modifier: Modifier = Modifier,
    glyph: (@Composable (Color) -> Unit)? = null,
    onViewAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (glyph != null) glyph(PlexTheme.colours.textPrimary)
            PlexText(text = title, style = PlexTheme.type.title, maxLines = 1)
        }
        if (onViewAll != null) ViewAllButton(onClick = onViewAll)
    }
}

/**
 * A per-library rail header: the library name in title weight and an inward chevron naming the row
 * as a way in. The whole header is the tap target that opens the library.
 */
@Composable
private fun CompactRailHeader(
    title: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Row(
        modifier = modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.card, onClick = onOpen, scaleOnFocus = false)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlexText(
            text = title,
            style = PlexTheme.type.title,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(Spacing.xs))
        PlexIcon(
            kind = PlexIconKind.BACK,
            size = 20.dp,
            tint = colours.textSecondary,
            modifier = Modifier.rotate(180f),
        )
    }
}

/** A 2×2 rounded-square grid, the small mark beside the "Libraries" header. */
@Composable
private fun LibrariesGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cell = Size(w * 0.38f, h * 0.38f)
        val r = CornerRadius(w * 0.08f, w * 0.08f)
        val gapX = w - cell.width * 2f
        val gapY = h - cell.height * 2f
        drawRoundRect(tint, Offset(0f, 0f), cell, r)
        drawRoundRect(tint, Offset(cell.width + gapX, 0f), cell, r)
        drawRoundRect(tint, Offset(0f, cell.height + gapY), cell, r)
        drawRoundRect(tint, Offset(cell.width + gapX, cell.height + gapY), cell, r)
    }
}

// --- television Home ----------------------------------------------------------------------

/** Wide (16:9) continue-watching tile width and 2:3 poster width for the ten-foot layout. */
private val TvWideWidth = 360.dp
private val TvPosterWidth = 184.dp

/** The immersive hero occupies this share of the viewport before the rails begin beneath it. */
private const val TV_HERO_HEIGHT_FRACTION = 0.55f

/**
 * The television Home, built for a ten-foot immersive read rather than the tablet's contained hero
 * card. A single full-bleed backdrop of the featured title fills the top of the screen under a
 * left-and-bottom cinematic scrim, with the title, a metadata line, a resume bar and large
 * Resume/Play + Details actions gathered in the lower-left. Beneath it, horizontal rails scroll: a
 * Continue Watching rail of wide progress tiles, then one poster rail per library.
 *
 * The hero follows focus. As the directional pad moves across the Continue Watching rail the
 * backdrop and caption swap to the focused title — the Compose "immersive list" pattern — so the
 * whole screen answers navigation. Browsing the library rails below leaves the hero on the last
 * Continue Watching selection (or the first entry), since discovery is out of scope and the hero is
 * only ever a title worth resuming. First focus lands on the hero's Resume button; pressing down
 * enters Continue Watching, and down again the first library rail, with per-rail focus memory left
 * to the lazy rows. See CLAUDE.md sections 12, 13 and 14.
 *
 * The caller places this inside a content area already inset from the collapsed navigation rail on
 * the left; the five percent television overscan is applied here to the caption and the rails, while
 * the backdrop bleeds to every edge of that area.
 */
@Composable
private fun TvHome(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onItemClick: (MediaItem) -> Unit,
    onLibraryClick: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    modifier: Modifier = Modifier,
) {
    // The featured title drives the backdrop and caption. It defaults to the first Continue Watching
    // entry and, when that rail is empty, to the first poster in the first library so the hero is
    // never blank; focus on a Continue Watching tile swaps it live.
    val firstContinue = continueWatching.firstOrNull()
    val fallbackFeatured = firstContinue
        ?: libraries.firstNotNullOfOrNull { libraryPreviews[it.key]?.firstOrNull() }
    var focusedItem by remember(fallbackFeatured?.ratingKey) { mutableStateOf<MediaItem?>(null) }
    val featured = focusedItem ?: fallbackFeatured

    val resumeFocus = rememberFirstFocus()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .material(GlassRole.GROUND),
    ) {
        val overscanH = maxWidth * Layout.TELEVISION_OVERSCAN_FRACTION
        val overscanV = maxHeight * Layout.TELEVISION_OVERSCAN_FRACTION
        val heroHeight = maxHeight * TV_HERO_HEIGHT_FRACTION

        // A fixed, full-bleed backdrop behind everything. It swaps with the featured title and
        // breathes under a slow ken-burns pan, so a still reads as a title sequence rather than a
        // photo. The rails scroll over it; the scrims below keep both the caption and the rails
        // legible against any art.
        if (featured != null) {
            Artwork(
                url = server.urls.artwork(
                    featured.artPath ?: featured.thumbPath,
                    ArtworkSize.BACKDROP_WIDTH,
                    ArtworkSize.BACKDROP_HEIGHT,
                ),
                contentDescription = primaryLine(featured),
                fallbackTitle = primaryLine(featured),
                modifier = Modifier.fillMaxSize().kenBurns(),
                alignment = Alignment.TopCenter,
            )
            Box(Modifier.fillMaxSize().cinematicTexture())
            // Left bed: dark where the caption sits, clearing toward the right so the art reads.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xE6000000),
                        0.5f to Color(0x66000000),
                        1.0f to Color.Transparent,
                    ),
                ),
            )
            // Foot bed: clears the upper backdrop but sinks the lower half to near-black so the
            // rails ride a dark ground rather than a busy still.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color(0x66000000),
                        0.75f to Color(0xE6000000),
                        1.0f to Color(0xF2000000),
                    ),
                ),
            )
        }

        LazyColumn(
            // The scrolling content is the Haze source the frosted navigation samples.
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(bottom = overscanV + Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // The hero caption occupies the top region, its content gathered in the lower-left.
            item(key = "tv-hero") {
                TvHeroCaption(
                    item = featured,
                    resumeFocus = resumeFocus,
                    heroHeight = heroHeight,
                    overscanH = overscanH,
                    overscanV = overscanV,
                    onPlay = { featured?.let { onPlay(it, it.viewOffsetMs) } },
                    onDetails = { featured?.let(onItemClick) },
                )
            }

            // Continue Watching — wide progress tiles with an always-on resume bar. Focusing a tile
            // swaps the hero above it to that title.
            if (continueWatching.isNotEmpty()) {
                item(key = "tv-cw") {
                    TvRow(title = "Continue Watching", overscanH = overscanH) {
                        itemsIndexed(
                            continueWatching,
                            key = { _, it -> "tv-cw-" + it.ratingKey },
                        ) { i, item ->
                            Box(
                                Modifier.onFocusChanged { if (it.hasFocus) focusedItem = item },
                            ) {
                                WideProgressTile(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.artPath ?: item.thumbPath,
                                        ArtworkSize.WIDE_WIDTH,
                                        ArtworkSize.WIDE_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    actions = itemActions(item),
                                    isContinueWatching = true,
                                    modifier = Modifier
                                        .width(TvWideWidth)
                                        .staggeredEntrance(i, key = item.ratingKey),
                                )
                            }
                        }
                    }
                }
            }

            // One poster rail per library — 2:3 art, tapping through to the item's detail.
            if (libraries.isNotEmpty()) {
                items(libraries, key = { "tv-lib-" + it.key }) { library ->
                    val previews = libraryPreviews[library.key].orEmpty()
                    TvRow(
                        title = library.title,
                        subtitle = libraryKindLabel(library.kind),
                        overscanH = overscanH,
                        onViewAll = { onLibraryClick(library) },
                    ) {
                        itemsIndexed(
                            previews,
                            key = { _, it -> library.key + "-" + it.ratingKey },
                        ) { i, item ->
                            PosterTile(
                                item = item,
                                artworkUrl = server.urls.artwork(
                                    item.thumbPath,
                                    ArtworkSize.POSTER_WIDTH,
                                    ArtworkSize.POSTER_HEIGHT,
                                ),
                                onClick = { onItemClick(item) },
                                actions = itemActions(item),
                                showCaption = false,
                                modifier = Modifier
                                    .width(TvPosterWidth)
                                    .staggeredEntrance(i, key = item.ratingKey),
                            )
                        }
                    }
                }
            } else {
                item(key = "tv-empty") {
                    PlexText(
                        text = "This server has no film or series libraries.",
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(horizontal = overscanH),
                    )
                }
            }
        }
    }
}

/**
 * The immersive hero's caption: the featured title in display type over a metadata line, an optional
 * resume bar, and the large Resume/Play + Details actions, all gathered in the lower-left of the
 * hero region. Rendered on the dark palette regardless of theme, since it always sits over dark
 * backdrop art. The first focus target — the Resume button — carries [resumeFocus].
 */
@Composable
private fun TvHeroCaption(
    item: MediaItem?,
    resumeFocus: androidx.compose.ui.focus.FocusRequester,
    heroHeight: Dp,
    overscanH: Dp,
    overscanV: Dp,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .padding(start = overscanH, end = overscanH, top = overscanV, bottom = Spacing.xs),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (item == null) return@Box
        val resuming = item.viewOffsetMs > 0L
        OnDarkSurface {
            Column(
                modifier = Modifier.fillMaxWidth(0.55f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlexText(
                    text = primaryLine(item),
                    style = PlexTheme.type.display,
                    colour = Color(0xFFF6F7F9),
                    maxLines = 2,
                )
                tvHeroMetadata(item)?.let {
                    PlexText(
                        text = it,
                        style = PlexTheme.type.label,
                        colour = Color(0xFFC8CDD6),
                        maxLines = 1,
                    )
                }
                if (item.progress > 0f) {
                    ProgressBar(
                        progress = item.progress,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        label = if (resuming) "Resume" else "Play",
                        leadingIcon = PlexIconKind.PLAY,
                        onClick = onPlay,
                        modifier = Modifier.focusRequester(resumeFocus),
                    )
                    SecondaryButton(
                        label = "Details",
                        leadingIcon = PlexIconKind.INFO,
                        onClick = onDetails,
                    )
                }
            }
        }
    }
}

/**
 * A television rail: a left-aligned header in title weight (with an optional kind subtitle and a
 * trailing "View all ›"), and a lazy row of tiles beneath it. The row's fixed-width tiles overrun
 * the right edge so the last card peeks, and focus stays put while the row scrolls under it.
 */
@Composable
private fun TvRow(
    title: String,
    overscanH: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onViewAll: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (subtitle == null) {
            SectionHeader(
                title = title,
                onViewAll = onViewAll,
                modifier = Modifier.padding(horizontal = overscanH),
            )
        } else {
            RailHeader(
                title = title,
                subtitle = subtitle,
                onViewAll = onViewAll ?: {},
                modifier = Modifier.padding(horizontal = overscanH),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            // Left overscan insets the first card; the right runs to the screen edge so the last
            // tile is cut and the rail reads as continuing off-screen.
            contentPadding = PaddingValues(start = overscanH, end = Spacing.xl),
            content = content,
        )
    }
}

/** The hero metadata line: kind/place, then either how much is left or the full runtime. */
private fun tvHeroMetadata(item: MediaItem): String? {
    val parts = buildList {
        secondaryLine(item)?.let { add(it) }
        if (item.viewOffsetMs > 0L) add(remainingLabel(item))
        else if (item.durationMs > 0L) add(formatDuration(item.durationMs))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
}
