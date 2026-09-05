package com.thotapalli.plex.ui.shared.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.formatDuration
import com.thotapalli.plex.ui.shared.remainingLabel
import kotlinx.coroutines.delay

/**
 * The television home: a cinematic hero for the featured title, then horizontal rails — Continue
 * Watching, then one per library ending in an "All" tile. Focusing any card re-features it in the
 * hero. Everything below the hero scrolls as one column, so DOWN walks the whole page.
 *
 * Focus: the screen is one zone ("home") that outlives navigation; each rail is a nested zone. On
 * a fresh open the hero's Play takes focus; on any return, the card the viewer left from does.
 * A held select on a card opens its menu — the television's stand-in for a long press.
 * See CLAUDE.md sections 13 and 14.
 */
@Composable
internal fun TvHome(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onItemClick: (MediaItem) -> Unit,
    onOpenLibrary: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    onItemMenu: (MediaItem, fromContinueWatching: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = rememberTvZone("home")
    val firstFeatured = continueWatching.firstOrNull()
        ?: libraries.firstNotNullOfOrNull { libraryPreviews[it.key]?.firstOrNull() }
    var featuredOverride by remember(firstFeatured?.ratingKey) { mutableStateOf<MediaItem?>(null) }
    val target = featuredOverride ?: firstFeatured

    // The hero follows focus with a short debounce, so holding RIGHT across a row does not flash
    // the backdrop through every card in between.
    var featured by remember { mutableStateOf(target) }
    LaunchedEffect(target?.ratingKey) {
        delay(HERO_DEBOUNCE_MS)
        featured = target
    }

    val hasContent = continueWatching.isNotEmpty() || libraryPreviews.values.any { it.isNotEmpty() }
    TvFirstFocus(zone, key = hasContent)

    TvZone(zone) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(TvPalette.ground)
                .tvZone(zone),
        ) {
            val heroHeight = maxHeight * 0.56f
            Column(Modifier.fillMaxSize()) {
                TvHomeHero(
                    server = server,
                    item = featured,
                    height = heroHeight,
                    onPlay = onPlay,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(Spacing.lg))
                    if (continueWatching.isNotEmpty()) {
                        TvRail(title = "Continue Watching", zone = rememberTvZone("home-continue")) {
                            items(continueWatching, key = { "cw-" + it.ratingKey }) { item ->
                                TvWideCard(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.artPath ?: item.thumbPath,
                                        ArtworkSize.WIDE_WIDTH,
                                        ArtworkSize.WIDE_HEIGHT,
                                    ),
                                    subLine = cardSubLine(item),
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onItemMenu(item, true) },
                                    onFocused = { featuredOverride = item },
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.lg))
                    }
                    libraries.forEach { library ->
                        val previews = libraryPreviews[library.key].orEmpty()
                        if (previews.isEmpty()) return@forEach
                        TvRail(title = library.title, zone = rememberTvZone("home-lib-" + library.key)) {
                            items(previews, key = { library.key + "-" + it.ratingKey }) { item ->
                                TvPosterCard(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.thumbPath,
                                        ArtworkSize.POSTER_WIDTH,
                                        ArtworkSize.POSTER_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onItemMenu(item, false) },
                                    onFocused = { featuredOverride = item },
                                )
                            }
                            item(key = library.key + "-all") {
                                TvTileCard(
                                    label = "All ›",
                                    caption = "See all",
                                    key = library.key + "-all",
                                    onClick = { onOpenLibrary(library) },
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.lg))
                    }
                    if (!hasContent) {
                        TvEmpty(
                            title = "Nothing here yet",
                            body = "Your libraries are loading, or the server has no movie or show libraries shared with this account.",
                            modifier = Modifier.height(heroHeight),
                        )
                    }
                    Spacer(Modifier.height(TvDims.overscanY + Spacing.xxl))
                }
            }
        }
    }
}

private const val HERO_DEBOUNCE_MS = 130L

/**
 * The fixed hero: the featured title's backdrop bleeding full width behind the rail, fading to
 * the ground, with the name (or clear logo), a fact line, a short synopsis and the one primary
 * action. The backdrop crossfades when focus re-features a title.
 */
@Composable
private fun TvHomeHero(
    server: ActiveServer,
    item: MediaItem?,
    height: Dp,
    onPlay: (MediaItem, Long) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Crossfade(
            targetState = item?.let { it.artPath ?: it.thumbPath },
            animationSpec = tween(320),
            label = "tv-hero",
        ) { path ->
            Artwork(
                url = server.urls.artwork(path, ArtworkSize.BACKDROP_WIDTH, ArtworkSize.BACKDROP_HEIGHT),
                contentDescription = item?.title ?: "",
                fallbackTitle = item?.title ?: "",
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
            )
        }
        TvHeroScrims()
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.6f)
                    .padding(start = TvDims.contentStart, end = Spacing.xl, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TvHeroCaption(server = server, item = item)
                Spacer(Modifier.height(Spacing.xs))
                val resuming = item.progress > 0f
                TvButton(
                    label = if (resuming) "Resume" else "Play",
                    key = "hero-play",
                    glyph = TvGlyph.PLAY,
                    primary = true,
                    onClick = { onPlay(item, item.viewOffsetMs) },
                )
            }
        }
    }
}

// --- shared hero pieces (home and detail) -------------------------------------------------

/** The two scrims that seat a backdrop: a left bed for the caption, a foot fade into the ground. */
@Composable
internal fun TvHeroScrims() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0.0f to Color(0xF20A0D14),
                0.5f to Color(0x800A0D14),
                1.0f to Color.Transparent,
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.65f to Color(0x660A0D14),
                1.0f to TvPalette.ground,
            ),
        ),
    )
}

/** Name (or clear logo), a quiet fact line, the episode title for an episode, and a synopsis. */
@Composable
internal fun TvHeroCaption(server: ActiveServer, item: MediaItem, synopsisLines: Int = 2) {
    TvHeroTitle(server = server, item = item)
    tvHeroMeta(item)?.let {
        PlexText(text = it, style = PlexTheme.type.label, colour = TvPalette.textDim, maxLines = 1)
    }
    if (item is Episode && item.title.isNotBlank()) {
        PlexText(
            text = item.title,
            style = PlexTheme.type.title,
            colour = TvPalette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (item.summary.isNotBlank()) {
        PlexText(
            text = item.summary,
            style = PlexTheme.type.body,
            colour = TvPalette.textDim,
            maxLines = synopsisLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The hero headline: the server's clear logo when it has one, else the name as text. */
@Composable
internal fun TvHeroTitle(server: ActiveServer, item: MediaItem, modifier: Modifier = Modifier) {
    val name = if (item is Episode) item.showTitle.ifBlank { item.title } else item.title
    val logoUrl = item.logoPath?.takeIf { it.isNotBlank() }?.let { server.urls.withToken(it) }
    if (logoUrl == null) {
        PlexText(text = name, style = PlexTheme.type.display, colour = Color.White, maxLines = 2, modifier = modifier)
        return
    }
    val painter = rememberAsyncImagePainter(model = logoUrl, contentScale = ContentScale.Fit)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Error) {
        PlexText(text = name, style = PlexTheme.type.display, colour = Color.White, maxLines = 2, modifier = modifier)
    } else {
        Image(
            painter = painter,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            modifier = modifier.heightIn(min = 44.dp, max = 96.dp).widthIn(max = 480.dp),
        )
    }
}

/** "S15 E01 • 42m left", "2011 • 1h 46m", "22 seasons • 177 episodes". */
internal fun tvHeroMeta(item: MediaItem): String? {
    val bits = buildList {
        when (item) {
            is Episode -> add("S${item.seasonIndex} E${item.episodeIndex}")
            is Movie -> item.year?.let { add(it.toString()) }
            is Show -> {
                item.year?.let { add(it.toString()) }
                if (item.childCount > 0) add("${item.childCount} seasons")
                if (item.leafCount > 0) add("${item.leafCount} episodes")
            }
            else -> Unit
        }
        if (item.viewOffsetMs > 0L && item.durationMs > 0L) add(remainingLabel(item))
        else if (item.durationMs > 0L) add(formatDuration(item.durationMs))
    }
    return bits.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
}

internal fun cardSubLine(item: MediaItem): String? = when (item) {
    is Episode -> "S${item.seasonIndex} E${item.episodeIndex}"
    is Movie -> item.year?.toString()
    is Show -> if (item.leafCount > 0) "${item.leafCount} episodes" else null
    else -> null
}
