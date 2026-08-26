package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val featured = featuredOverride ?: firstFeatured
    val playFocus = rememberFirstFocus(enabled = true)

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
                playFocus = playFocus,
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
                                onFocused = { featuredOverride = item },
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
                                    onFocused = { featuredOverride = item },
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
    playFocus: androidx.compose.ui.focus.FocusRequester,
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
                    .fillMaxWidth(0.62f)
                    .padding(start = TvContentStart, end = Spacing.xl, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlexText(
                    text = primaryLine(item),
                    style = PlexTheme.type.display,
                    colour = Color.White,
                    maxLines = 2,
                )
                tvMetaLine(item)?.let {
                    PlexText(text = it, style = PlexTheme.type.label, colour = Color(0xFFD2D7DF), maxLines = 1)
                }
                if (item.summary.isNotBlank()) {
                    PlexText(
                        text = item.summary,
                        style = PlexTheme.type.body,
                        colour = Color(0xFFC2C8D2),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Spacing.xxs))
                val resuming = item.progress > 0f
                TvActionButton(
                    label = if (resuming) "Resume" else "Play",
                    icon = PlexIconKind.PLAY,
                    primary = true,
                    onClick = { onPlay(item, item.viewOffsetMs) },
                    modifier = Modifier.focusRequester(playFocus),
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
    val firstFocus = rememberFirstFocus(enabled = true)
    BoxWithConstraints(modifier.fillMaxSize().background(PlexTheme.colours.background)) {
        val overscanV = maxHeight * Layout.TELEVISION_OVERSCAN_FRACTION
        // Collections lead, then titles — both are MediaItems for the card.
        val all: List<MediaItem> = collections + items
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
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
                    modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(firstFocus)
                    else Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --- Rails & cards ----------------------------------------------------------------------------

/** A titled horizontal rail. The header sits inset past the rail; the row overruns to the right. */
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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = TvContentStart, end = Spacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

/** A 16:9 continue-watching card: art, a resume bar, the title and "S..E.." beneath. */
@Composable
private fun TvWideCard(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-wide-scale")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
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
        PlexText(text = primaryLine(item), style = PlexTheme.type.label, colour = PlexTheme.colours.textPrimary, maxLines = 1)
        tvMetaLine(item)?.let {
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
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tv-poster-scale")
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
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tv-all-scale")
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

/** One fact line for the TV cards/hero: episode code + title, or year/episode count, plus runtime. */
private fun tvMetaLine(item: MediaItem): String? {
    val bits = buildList {
        when (item) {
            is Episode -> add("S${pad(item.seasonIndex)}E${pad(item.episodeIndex)}  ${item.title}")
            is Movie -> item.year?.let { add(it.toString()) }
            is Show -> if (item.leafCount > 0) add("${item.leafCount} episodes")
            is Season -> if (item.leafCount > 0) add("${item.leafCount} episodes")
            is MediaCollection -> add("${item.childCount} titles")
            else -> {}
        }
    }
    return bits.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
}
