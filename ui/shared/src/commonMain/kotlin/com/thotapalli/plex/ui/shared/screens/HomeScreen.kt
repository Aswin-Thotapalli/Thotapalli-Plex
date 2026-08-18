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
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.LibraryCard
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.WideProgressTile

/**
 * Home: Continue Watching as a horizontal row of wide progress tiles, then one card per
 * library. Nothing else. An empty Continue Watching row is hidden and the library cards
 * move up. See CLAUDE.md section 14.
 */
@Composable
fun HomeScreen(
    server: ActiveServer,
    continueWatching: List<MediaItem>,
    libraries: List<Library>,
    onItemClick: (MediaItem) -> Unit,
    onLibraryClick: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val tileWidth = if (sizeClass.isTelevision) 320.dp else 260.dp

    ContentWidthCap(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(sizeClass.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Hidden entirely when empty, so the library cards move up rather than sitting
            // below a gap. See CLAUDE.md section 14.
            if (continueWatching.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader("Continue Watching")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(vertical = Spacing.xs),
                        ) {
                            items(continueWatching, key = { it.ratingKey }) { item ->
                                WideProgressTile(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.artPath ?: item.thumbPath,
                                        ArtworkSize.WIDE_WIDTH,
                                        ArtworkSize.WIDE_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier.width(tileWidth),
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Libraries") }

            items(libraries, key = { it.key }) { library ->
                LibraryCard(
                    title = library.title,
                    subtitle = when (library.kind) {
                        LibraryKind.MOVIE -> "Films"
                        LibraryKind.SHOW -> "Series"
                        LibraryKind.UNSUPPORTED -> ""
                    },
                    artworkUrl = null,
                    onClick = { onLibraryClick(library) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                )
            }

            if (libraries.isEmpty()) {
                item {
                    PlexText(
                        text = "This server has no film or series libraries.",
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        }
    }
}