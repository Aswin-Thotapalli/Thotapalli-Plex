package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.HomeHero
import com.thotapalli.plex.ui.shared.LibraryCard
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.WideProgressTile
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Home: a featured hero for the top Continue Watching title, the rest of that row, then one
 * card per library. Nothing else. An empty Continue Watching row is hidden and the library
 * cards move up. See CLAUDE.md section 14.
 */
@Composable
fun HomeScreen(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    onItemClick: (MediaItem) -> Unit,
    onLibraryClick: (Library) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val tileWidth = if (sizeClass.isTelevision) 320.dp else 260.dp
    val pad = sizeClass.screenPadding

    val featured = continueWatching.firstOrNull()
    val rest = continueWatching.drop(1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // The hero is full-bleed; everything below it is inset to the screen padding.
        if (featured != null) {
            item {
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

        if (rest.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = pad)) {
                    SectionHeader("Continue Watching")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        itemsIndexed(rest, key = { _, it -> it.ratingKey }) { index, item ->
                            WideProgressTile(
                                item = item,
                                artworkUrl = server.urls.artwork(
                                    item.artPath ?: item.thumbPath,
                                    ArtworkSize.WIDE_WIDTH,
                                    ArtworkSize.WIDE_HEIGHT,
                                ),
                                onClick = { onItemClick(item) },
                                modifier = Modifier
                                    .width(tileWidth)
                                    .staggeredEntrance(index, key = item.ratingKey),
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Your Libraries", Modifier.padding(horizontal = pad))
        }

        itemsIndexed(libraries, key = { _, it -> it.key }) { index, library ->
            LibraryCard(
                title = library.title,
                subtitle = when (library.kind) {
                    LibraryKind.MOVIE -> "Films"
                    LibraryKind.SHOW -> "Series"
                    LibraryKind.UNSUPPORTED -> ""
                },
                artworkUrl = null,
                onClick = { onLibraryClick(library) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pad)
                    .staggeredEntrance(index, key = library.key),
            )
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
