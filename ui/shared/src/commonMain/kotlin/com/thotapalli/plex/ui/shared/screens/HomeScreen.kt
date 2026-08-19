package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.HomeHero
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.WideProgressTile
import com.thotapalli.plex.ui.shared.plexFocusable
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Home: a spotlight hero for the single title most worth resuming, a Continue Watching rail
 * that holds the rest, and a poster rail for each library — so Home browses like a shelf of
 * content, not a list of folders. See CLAUDE.md section 14.
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
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val pad = sizeClass.screenPadding
    val posterWidth = posterRailWidth(sizeClass)
    val wideWidth = if (sizeClass.isTelevision) 340.dp else 280.dp

    val featured = continueWatching.firstOrNull()
    // The featured title is the hero; the rail holds the others, so nothing is shown twice.
    val continueRail = continueWatching.drop(1)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                                modifier = Modifier.width(wideWidth).staggeredEntrance(i, key = item.ratingKey),
                            )
                        }
                    }
                }
            }
        }

        // One poster rail per library.
        items(libraries, key = { "lib-" + it.key }) { library ->
            val preview = libraryPreviews[library.key].orEmpty()
            Rail(
                title = library.title,
                pad = pad,
                onSeeAll = { onLibraryClick(library) },
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = PaddingValues(horizontal = pad),
                ) {
                    itemsIndexed(preview, key = { _, it -> library.key + "-" + it.ratingKey }) { i, item ->
                        PosterTile(
                            item = item,
                            artworkUrl = server.urls.artwork(
                                item.thumbPath,
                                ArtworkSize.POSTER_WIDTH,
                                ArtworkSize.POSTER_HEIGHT,
                            ),
                            onClick = { onItemClick(item) },
                            modifier = Modifier.width(posterWidth).staggeredEntrance(i, key = item.ratingKey),
                        )
                    }
                }
            }
        }

        if (libraries.isEmpty()) {
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

/** A titled horizontal shelf, with an optional "See all" that opens the full library. */
@Composable
private fun Rail(
    title: String,
    pad: androidx.compose.ui.unit.Dp,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = pad),
            trailing = onSeeAll?.let {
                {
                    PlexText(
                        text = "See all",
                        style = PlexTheme.type.label,
                        colour = PlexTheme.colours.accent,
                        modifier = Modifier
                            .plexFocusable(shape = Radius.pill, onClick = it, scaleOnFocus = false)
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    )
                }
            },
        )
        content()
    }
}

private fun posterRailWidth(sizeClass: SizeClass) = when (sizeClass) {
    SizeClass.COMPACT -> 124.dp
    SizeClass.MEDIUM -> 140.dp
    SizeClass.EXPANDED -> 152.dp
    SizeClass.TELEVISION -> 184.dp
}
