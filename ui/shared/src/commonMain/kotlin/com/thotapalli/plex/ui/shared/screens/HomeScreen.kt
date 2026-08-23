package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.Elevation
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.HomeHero
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.WideProgressTile
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.plexFocusable
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Home: a spotlight hero for the single title most worth resuming, a Continue Watching rail that
 * holds the rest, and one glass card per library — a peek of its posters behind the title — so Home
 * reads as a lit shelf you step into rather than a list of folders. See CLAUDE.md section 14.
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
    val sizeClass = PlexTheme.sizeClass
    val pad = sizeClass.screenPadding
    val wideWidth = if (sizeClass.isTelevision) 340.dp else 280.dp

    val featured = continueWatching.firstOrNull()
    // The featured title is the hero; the rail holds the others, so nothing is shown twice.
    val continueRail = continueWatching.drop(1)

    // Wider windows lay the library cards two across; narrower ones stack them full width.
    val cardsPerRow = if (sizeClass == SizeClass.EXPANDED || sizeClass.isTelevision) 2 else 1
    val peekHeight = libraryPeekHeight(sizeClass)

    // The viewport height bounds the hero: on a short window the hero is capped to a fraction of
    // it so its action buttons stay clear of the bottom edge and the rails below remain in view.
    BoxWithConstraints(
        // The ground veil: a translucent skin over the ambient featured art, so Home shares the
        // nav's glass world and the art bleeds through faintly instead of an opaque wall below the hero.
        modifier = modifier
            .fillMaxSize()
            .material(GlassRole.GROUND),
    ) {
        val viewportHeight = maxHeight

        LazyColumn(
            // The scrolling content is the Haze source the frosted navigation samples and blurs.
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
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
                    )
                }
            }

            // Continue Watching — the in-progress titles beyond the featured one, so any number
            // of resumes has a home and nothing is duplicated with the hero.
            if (continueRail.isNotEmpty()) {
                item(key = "cw") {
                    Rail(title = "Continue Watching", pad = pad) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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

            // One glass card per library, laid out one or two across for the window.
            if (libraries.isNotEmpty()) {
                item(key = "lib-header") {
                    SectionHeader(title = "Libraries", modifier = Modifier.padding(horizontal = pad))
                }
                items(libraries.chunked(cardsPerRow), key = { "librow-" + it.first().key }) { rowLibraries ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        rowLibraries.forEachIndexed { i, library ->
                            LibraryPeekCard(
                                server = server,
                                library = library,
                                previews = libraryPreviews[library.key].orEmpty(),
                                peekHeight = peekHeight,
                                onClick = { onLibraryClick(library) },
                                modifier = Modifier
                                    .weight(1f)
                                    .staggeredEntrance(i, key = library.key),
                            )
                        }
                        // Keep a lone card in a two-across row at column width rather than stretched.
                        repeat(cardsPerRow - rowLibraries.size) { Spacer(Modifier.weight(1f)) }
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
 * A library card on Home: a sheet of liquid glass carrying the library title, its kind, an inward
 * chevron naming it as a way in, and a peek of the library's posters clipped along the bottom so a
 * few titles show and the rest run off the edge — the card is the whole tap target, opening the
 * library. Focus and press answer with the app's accent ring and springy bubble. See CLAUDE.md
 * section 14.
 */
@Composable
private fun LibraryPeekCard(
    server: ActiveServer,
    library: Library,
    previews: List<MediaItem>,
    peekHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .material(GlassRole.CARD, shape = Radius.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                PlexText(text = library.title, style = PlexTheme.type.title, maxLines = 1)
                PlexText(
                    text = libraryKindLabel(library.kind),
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 1,
                )
            }
            // The back chevron mirrored to point inward, so the card reads as an entrance.
            PlexIcon(
                kind = PlexIconKind.BACK,
                size = 20.dp,
                tint = colours.accent,
                modifier = Modifier.rotate(180f),
            )
        }

        if (previews.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(peekHeight)
                    // Extra posters run off the right edge, a "there is more inside" affordance.
                    .clipToBounds(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                previews.take(LIBRARY_PEEK_MAX).forEach { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(Layout.POSTER_ASPECT_RATIO)
                            .shadow(
                                elevation = Elevation.tile,
                                shape = Radius.poster,
                                ambientColor = colours.elevationShadow,
                                spotColor = colours.elevationShadow,
                            )
                            .clip(Radius.poster),
                    ) {
                        Artwork(
                            url = server.urls.artwork(
                                item.thumbPath,
                                ArtworkSize.POSTER_WIDTH,
                                ArtworkSize.POSTER_HEIGHT,
                            ),
                            contentDescription = null,
                            fallbackTitle = item.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().border(1.dp, colours.glassRim, Radius.poster))
                    }
                }
            }
        }
    }
}

/** A titled horizontal shelf. Used by the Continue Watching row. */
@Composable
private fun Rail(
    title: String,
    pad: Dp,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title = title, modifier = Modifier.padding(horizontal = pad))
        content()
    }
}

private fun libraryKindLabel(kind: LibraryKind): String = when (kind) {
    LibraryKind.MOVIE -> "Movies"
    LibraryKind.SHOW -> "Series"
    LibraryKind.UNSUPPORTED -> "Library"
}

/** The most posters a library card ever peeks; the rest clip off the edge. */
private const val LIBRARY_PEEK_MAX = 8

/** The height of a library card's poster peek, taller where the screen has more room. */
private fun libraryPeekHeight(sizeClass: SizeClass): Dp = when (sizeClass) {
    SizeClass.COMPACT -> 92.dp
    SizeClass.MEDIUM -> 104.dp
    SizeClass.EXPANDED -> 116.dp
    SizeClass.TELEVISION -> 140.dp
}
