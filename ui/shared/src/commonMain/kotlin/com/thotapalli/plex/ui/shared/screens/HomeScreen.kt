package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.HomeHero
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.ViewAllButton
import com.thotapalli.plex.ui.shared.WideProgressTile
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Home: a spotlight hero for the single title most worth resuming, one horizontal poster rail per
 * library beneath it, and a Continue Watching rail of wide progress tiles at the foot. Home reads
 * as a lit shelf of content rather than a list of folders. See CLAUDE.md section 14.
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
    val posterWidth = if (sizeClass.isTelevision) 160.dp else 140.dp
    val wideWidth = if (sizeClass.isTelevision) 340.dp else 280.dp

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
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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

            // Continue Watching — the in-progress titles beyond the featured one, as wide tiles.
            if (continueRail.isNotEmpty()) {
                item(key = "cw") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SectionHeader(
                            title = "Continue Watching",
                            modifier = Modifier.padding(horizontal = pad),
                        )
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
