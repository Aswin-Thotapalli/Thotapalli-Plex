package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing

/**
 * The television home and library, authored from the ground up for the ten-foot experience — they
 * borrow nothing from the phone/tablet/desktop screens. The language is: a solid dark ground, a
 * confined cinematic hero at the top that scrolls away, horizontal card rails, and a focus model of
 * a firm scale + a bright ring. No glass, no blur, no per-frame grain or ken-burns, no entrance
 * cascades — all of which the shared touch screens carry and which stutter at ten feet.
 *
 * The left [nav rail][TvShell] floats over the left edge; every screen insets its own content past
 * [TvContentStart] so nothing hides behind the collapsed rail while the hero art still bleeds behind
 * it. A D-pad LEFT from the leftmost card always reaches the rail, and RIGHT returns to content.
 */

/** How far a TV screen holds its interactive content clear of the floating collapsed nav rail. */
internal val TvContentStart = TvRailCollapsedWidth + Spacing.lg

// --- Home -------------------------------------------------------------------------------------

/**
 * The television home: a confined cinematic hero for the featured title, then horizontal rails —
 * Continue Watching, then one per library, each ending in a reachable "All" card. Focusing a
 * Continue Watching card re-features it in the hero. Everything scrolls as one column so the D-pad
 * travels the whole page with the focused card scrolled into view.
 */
@Composable
fun TvHomeScreen(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onItemClick: (MediaItem) -> Unit,
    onOpenLibrary: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFeatured = continueWatching.firstOrNull()
        ?: libraries.firstNotNullOfOrNull { libraryPreviews[it.key]?.firstOrNull() }
    var featuredOverride by remember(firstFeatured?.ratingKey) { mutableStateOf<MediaItem?>(null) }
    val target = featuredOverride ?: firstFeatured
    // Debounce the hero: navigation responds instantly, but wait ~130ms before committing the
    // artwork/title change so rapidly pressing Right doesn't flash the backdrop through every card
    // in between (reference §7). The whole hero — art, title, metadata, description — updates as one.
    var featured by remember { mutableStateOf(target) }
    LaunchedEffect(target?.ratingKey) {
        kotlinx.coroutines.delay(130)
        featured = target
    }
    // Focus restoration for the home rows (reference §17): remember which card the viewer last
    // focused (saveable, kept alive by the screen's SaveableStateProvider). On a fresh open the key
    // is null and the hero's primary action takes focus; after opening a detail and pressing Back the
    // matching card — whichever row it's in — is re-focused.
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { restoreFocus.requestFocus() } }

    BoxWithConstraints(modifier.fillMaxSize().background(PlexTheme.colours.background)) {
        val overscanV = maxHeight * Layout.TELEVISION_OVERSCAN_FRACTION
        val heroHeight = maxHeight * 0.56f

        // Fixed hero on top (the featured item's full backdrop + info + Resume), a scrollable rail
        // region beneath on the solid ground. The hero stays put so the focused item can drive the
        // backdrop the way the reference asks, while the rows scroll independently and never let the
        // art overpower them. See CLAUDE.md section 13.
        Column(Modifier.fillMaxSize()) {
            TvHomeHero(
                server = server,
                item = featured,
                height = heroHeight,
                onPlay = onPlay,
                // On a fresh open (no remembered card) the hero action takes first focus; after a
                // Back the remembered card claims it instead.
                playFocus = restoreFocus.takeIf { lastFocusedKey == null },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PlexTheme.colours.background)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(Spacing.lg))
                if (continueWatching.isNotEmpty()) {
                    TvRail(title = "Continue Watching") {
                        items(continueWatching, key = { "cw-" + it.ratingKey }) { item ->
                            TvWideCard(
                                item = item,
                                artworkUrl = server.urls.artwork(
                                    item.artPath ?: item.thumbPath,
                                    ArtworkSize.WIDE_WIDTH,
                                    ArtworkSize.WIDE_HEIGHT,
                                ),
                                onClick = { onItemClick(item) },
                                onFocused = { featuredOverride = item; lastFocusedKey = item.ratingKey },
                                modifier = if (item.ratingKey == lastFocusedKey) Modifier.focusRequester(restoreFocus) else Modifier,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
                libraries.forEach { library ->
                    val previews = libraryPreviews[library.key].orEmpty()
                    if (previews.isNotEmpty()) {
                        TvRail(title = library.title) {
                            items(previews, key = { library.key + "-" + it.ratingKey }) { item ->
                                TvPosterCard(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.thumbPath,
                                        ArtworkSize.POSTER_WIDTH,
                                        ArtworkSize.POSTER_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    onFocused = { featuredOverride = item; lastFocusedKey = item.ratingKey },
                                    modifier = if (item.ratingKey == lastFocusedKey) Modifier.focusRequester(restoreFocus) else Modifier,
                                )
                            }
                            item(key = library.key + "-all") {
                                TvSeeAllCard(onClick = { onOpenLibrary(library) })
                            }
                        }
                        Spacer(Modifier.height(Spacing.lg))
                    }
                }
                Spacer(Modifier.height(overscanV + Spacing.xxl))
            }
        }
    }
}

/**
 * The fixed hero: the featured title's backdrop bleeds full width (behind the floating rail), fading
 * to the ground, with the name, a fact line, a short synopsis and the one primary action (Resume /
 * Play). The backdrop crossfades when the focused card re-features a title, per the reference.
 */
@Composable
private fun TvHomeHero(
    server: ActiveServer,
    item: MediaItem?,
    height: Dp,
    onPlay: (MediaItem, Long) -> Unit,
    playFocus: FocusRequester?,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        // The backdrop crossfades on a change of featured title (250–400ms per the reference).
        androidx.compose.animation.Crossfade(
            targetState = item?.let { it.artPath ?: it.thumbPath },
            animationSpec = androidx.compose.animation.core.tween(320),
            label = "tv-hero-backdrop",
        ) { path ->
            Artwork(
                url = server.urls.artwork(path, ArtworkSize.BACKDROP_WIDTH, ArtworkSize.BACKDROP_HEIGHT),
                contentDescription = item?.let(::primaryLine) ?: "",
                fallbackTitle = item?.let(::primaryLine) ?: "",
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
            )
        }
        // Left bed for the caption, clearing to the right so the art reads (reference: gradient left).
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color(0xF20A0D14),
                    0.5f to Color(0x800A0D14),
                    1.0f to Color.Transparent,
                ),
            ),
        )
        // Foot bed onto the ground so the hero seats into the rails below.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.65f to Color(0x660A0D14),
                    1.0f to PlexTheme.colours.background,
                ),
            ),
        )
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.6f)
                    .padding(start = TvContentStart, end = Spacing.xl, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Hierarchy (reference §3): the show/movie name (or its clear-logo) leads big, then a
                // quiet fact line, then the episode title (episodes only), then a short synopsis —
                // none competing at the same weight.
                HeroTitle(server = server, item = item)
                heroMeta(item)?.let {
                    PlexText(text = it, style = PlexTheme.type.label, colour = Color(0xFFB9C0CC), maxLines = 1)
                }
                if (item is Episode && item.title.isNotBlank()) {
                    PlexText(
                        text = item.title,
                        style = PlexTheme.type.title,
                        colour = Color(0xFFF2F4F7),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.summary.isNotBlank()) {
                    PlexText(
                        text = item.summary,
                        style = PlexTheme.type.body,
                        colour = Color(0xFFB9C0CC),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                val resuming = item.progress > 0f
                TvActionButton(
                    label = if (resuming) "Resume" else "Play",
                    icon = PlexIconKind.PLAY,
                    primary = true,
                    onClick = { onPlay(item, item.viewOffsetMs) },
                    modifier = if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier,
                )
            }
        }
    }
}

// --- Library ----------------------------------------------------------------------------------

/**
 * The television library: a title, then a poster grid on the solid ground. LEFT from the first
 * column reaches the nav rail; OK on a poster opens its detail. No filters chrome yet — the ten-foot
 * grid stays clean; sort/unwatched can return as a focusable header row later.
 */
@Composable
fun TvLibraryScreen(
    server: ActiveServer,
    title: String,
    items: List<MediaItem>,
    collections: List<MediaCollection>,
    onItemClick: (MediaItem) -> Unit,
    onCollectionClick: (MediaCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Exact focus + scroll restoration (reference §17). The last-focused poster's index and the grid
    // scroll offset both live in saveable state, which the screen's SaveableStateProvider keeps alive
    // while a detail is open — so pressing Back re-focuses the very poster the viewer left from, at
    // the same scroll position, rather than jumping to the top. On a fresh open the saved index is 0,
    // so the first poster takes focus.
    var lastFocusedIndex by rememberSaveable { mutableStateOf(0) }
    val targetFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) { runCatching { targetFocus.requestFocus() } }
    BoxWithConstraints(modifier.fillMaxSize().background(PlexTheme.colours.background)) {
        val overscanV = maxHeight * Layout.TELEVISION_OVERSCAN_FRACTION
        // Collections lead, then titles — both are MediaItems for the card.
        val all: List<MediaItem> = collections + items
        LazyVerticalGrid(
            // Slightly denser than before — about five complete posters across at 1080p — while
            // staying readable from the sofa (reference §12).
            columns = GridCells.Adaptive(minSize = 144.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = TvContentStart,
                end = Spacing.xxl,
                top = overscanV + Spacing.md,
                bottom = overscanV + Spacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = Spacing.xs)) {
                    PlexText(text = title, style = PlexTheme.type.display, colour = PlexTheme.colours.textPrimary, maxLines = 1)
                    PlexText(
                        text = "${all.size} titles",
                        style = PlexTheme.type.label,
                        colour = PlexTheme.colours.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            itemsIndexed(all, key = { _, it -> it.ratingKey }) { index, entry ->
                TvPosterCard(
                    item = entry,
                    artworkUrl = server.urls.artwork(entry.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT),
                    onClick = {
                        if (entry is MediaCollection) onCollectionClick(entry) else onItemClick(entry)
                    },
                    onFocused = { lastFocusedIndex = index },
                    modifier = if (index == lastFocusedIndex) Modifier.fillMaxWidth().focusRequester(targetFocus)
                    else Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --- Rails & cards ----------------------------------------------------------------------------

/**
 * Pivot scrolling (reference §15): instead of the default "just barely into view", hold the focused
 * card at a fixed column ~18% from the left once the row starts scrolling, so upcoming content is
 * always previewed on the right. The first cards sit at their natural place (the list clamps at the
 * start); past the pivot the card stays put and the row slides under it.
 */
@OptIn(ExperimentalFoundationApi::class)
private val TvPivotBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - containerSize * 0.18f
}

/** A titled horizontal rail. The header sits inset past the rail; the row overruns to the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvRail(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PlexText(
            text = title,
            style = PlexTheme.type.title,
            colour = PlexTheme.colours.textPrimary,
            maxLines = 1,
            modifier = Modifier.padding(start = TvContentStart, end = Spacing.xl, bottom = Spacing.sm),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivotBringIntoView) {
            LazyRow(
                // Restore focus to the last-focused card when the remote comes back to this row
                // (row-to-row moves and returning from a detail) — reference §16–17.
                modifier = Modifier.fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(start = TvContentStart, end = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

/** A 16:9 continue-watching card: art, a resume bar, the title and "S..E.." beneath. */
@Composable
private fun TvWideCard(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-wide-scale")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(300.dp)
            .scale(scale)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .then(if (focused) Modifier.border(3.dp, TvGold, shape) else Modifier),
        ) {
            Artwork(url = artworkUrl, contentDescription = primaryLine(item), fallbackTitle = primaryLine(item), modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)),
                ),
            )
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Spacing.xs)) {
                    ProgressBar(progress = item.progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        PlexText(
            text = primaryLine(item),
            style = PlexTheme.type.label,
            colour = if (focused) Color.White else PlexTheme.colours.textPrimary,
            maxLines = 1,
        )
        cardSubLine(item)?.let {
            PlexText(text = it, style = PlexTheme.type.caption, colour = PlexTheme.colours.textSecondary, maxLines = 1)
        }
    }
}

/** A 2:3 poster card: art with a focus ring, the title beneath. */
@Composable
private fun TvPosterCard(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-poster-scale")
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .width(160.dp)
            .scale(scale)
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .then(if (focused) Modifier.border(3.dp, TvGold, shape) else Modifier),
        ) {
            Artwork(url = artworkUrl, contentDescription = primaryLine(item), fallbackTitle = primaryLine(item), modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(Spacing.xs))
        PlexText(
            text = primaryLine(item),
            style = PlexTheme.type.label,
            colour = if (focused) PlexTheme.colours.textPrimary else PlexTheme.colours.textSecondary,
            maxLines = 1,
        )
    }
}

/** The trailing "All" card that opens the full library grid — the reachable "see all". */
@Composable
private fun TvSeeAllCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-all-scale")
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(160.dp).scale(scale)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(if (focused) TvGold else Color(0x1FFFFFFF))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlexText(
                text = "All ›",
                style = PlexTheme.type.title,
                colour = if (focused) TvInk else PlexTheme.colours.textPrimary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        PlexText(text = "See all", style = PlexTheme.type.label, colour = PlexTheme.colours.textSecondary, maxLines = 1)
    }
}

// --- helpers ----------------------------------------------------------------------------------

/** The hero's big line: the show name for an episode, the title otherwise (reference §3). */
internal fun heroTitle(item: MediaItem): String = when (item) {
    is Episode -> item.showTitle.ifBlank { item.title }
    else -> item.title
}

/**
 * The hero headline: Plex's transparent clear-logo when the server has one, otherwise the name as
 * text (reference §4). The logo is size-capped so it never dominates, and any load error falls back
 * to text. Uses the non-transcoded image URL so the logo's transparency is preserved.
 */
@Composable
internal fun HeroTitle(server: ActiveServer, item: MediaItem, modifier: Modifier = Modifier) {
    val logoUrl = item.logoPath?.takeIf { it.isNotBlank() }?.let { server.urls.withToken(it) }
    if (logoUrl == null) {
        PlexText(text = heroTitle(item), style = PlexTheme.type.display, colour = Color.White, maxLines = 2, modifier = modifier)
        return
    }
    val painter = rememberAsyncImagePainter(model = logoUrl, contentScale = ContentScale.Fit)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Error) {
        PlexText(text = heroTitle(item), style = PlexTheme.type.display, colour = Color.White, maxLines = 2, modifier = modifier)
    } else {
        Image(
            painter = painter,
            contentDescription = heroTitle(item),
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            modifier = modifier.heightIn(min = 44.dp, max = 96.dp).widthIn(max = 480.dp),
        )
    }
}

/** The hero's quiet fact line: "S15 E01 • 1h 03m", "2011 • 1h 46m", "22 seasons", etc. */
internal fun heroMeta(item: MediaItem): String? {
    val bits = buildList {
        when (item) {
            is Episode -> add("S${item.seasonIndex} E${item.episodeIndex}")
            is Movie -> item.year?.let { add(it.toString()) }
            is Show -> {
                item.year?.let { add(it.toString()) }
                if (item.leafCount > 0) add("${item.leafCount} episodes")
            }
            else -> {}
        }
        if (item.viewOffsetMs > 0L && item.durationMs > 0L) add(remainingLabel(item))
        else if (item.durationMs > 0L) add(formatDuration(item.durationMs))
    }
    return bits.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
}

/** A concise sub-line for a card: "S15 E01" for an episode, the year for a movie. */
private fun cardSubLine(item: MediaItem): String? = when (item) {
    is Episode -> "S${item.seasonIndex} E${item.episodeIndex}"
    is Movie -> item.year?.toString()
    is Show -> if (item.leafCount > 0) "${item.leafCount} episodes" else null
    else -> null
}

