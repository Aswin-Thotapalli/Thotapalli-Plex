package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.LibraryState

/**
 * A library: its title, the one filter the brief allows ("Unwatched only"), and the poster grid —
 * collections first with their stacked look, then titles alphabetically. Sort is fixed and has no
 * control. LEFT from the first column reaches the rail through the shell's declared exit; the grid
 * remembers the poster the viewer left from and scrolls back to it. See CLAUDE.md section 14 item 3.
 */
@Composable
internal fun TvLibrary(
    server: ActiveServer,
    state: LibraryState,
    onItemClick: (MediaItem) -> Unit,
    onCollectionClick: (MediaCollection) -> Unit,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onCloseCollection: () -> Unit,
    onItemMenu: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collection = state.openCollection
    val screenKey = "library-${state.library.key}-${collection?.ratingKey ?: "root"}"
    val zone = rememberTvZone(screenKey)
    val gridZone = rememberTvZone("$screenKey-grid")
    val gridState = rememberLazyGridState()

    // Collections lead, then titles. Inside a collection only its members show.
    val entries: List<MediaItem> = if (collection != null) state.items else state.collections + state.items

    // The poster the viewer was on, so Back from a detail scrolls the grid to it before focus
    // returns there. The index survives the screen leaving composition.
    var lastIndex by rememberSaveable(screenKey) { mutableIntStateOf(0) }
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) gridState.scrollToItem(lastIndex.coerceIn(0, entries.lastIndex) + 1)
    }
    TvFirstFocus(zone, key = entries.isNotEmpty() || !state.loading)

    TvZone(zone) {
        Column(modifier.fillMaxSize().background(TvPalette.ground).tvZone(zone)) {
            TvZone(gridZone) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 144.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().tvZone(gridZone),
                    contentPadding = PaddingValues(
                        start = TvDims.contentStart,
                        end = TvDims.overscanX,
                        top = TvDims.overscanY,
                        bottom = TvDims.overscanY + Spacing.xxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // The header is the grid's first full-width row, so UP from the top posters
                    // reaches the filter and DOWN from the filter reaches the posters.
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                        LibraryHeader(
                            title = collection?.title ?: state.library.title,
                            count = entries.size,
                            unwatchedOnly = state.unwatchedOnly,
                            inCollection = collection != null,
                            loading = state.loading,
                            onUnwatchedOnlyChange = onUnwatchedOnlyChange,
                            onCloseCollection = onCloseCollection,
                        )
                    }
                    itemsIndexed(entries, key = { _, entry -> entry.ratingKey }) { index, entry ->
                        TvPosterCard(
                            item = entry,
                            artworkUrl = server.urls.artwork(entry.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT),
                            badge = if (entry is MediaCollection) "Collection" else null,
                            onClick = {
                                if (entry is MediaCollection) onCollectionClick(entry) else onItemClick(entry)
                            },
                            onLongClick = { if (entry !is MediaCollection) onItemMenu(entry) },
                            onFocused = { lastIndex = index },
                            modifier = Modifier.fillMaxWidth(),
                            width = 160.dp,
                        )
                    }
                    if (entries.isEmpty() && !state.loading) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            PlexText(
                                text = if (state.unwatchedOnly) "Everything here has been watched." else "This library is empty.",
                                style = PlexTheme.type.body,
                                colour = TvPalette.textDim,
                                modifier = Modifier.padding(top = Spacing.lg),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    title: String,
    count: Int,
    unwatchedOnly: Boolean,
    inCollection: Boolean,
    loading: Boolean,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onCloseCollection: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PlexText(text = title, style = PlexTheme.type.display, colour = TvPalette.text, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlexText(
                text = when {
                    loading && count == 0 -> "Loading…"
                    count == 1 -> "1 title"
                    else -> "$count titles"
                },
                style = PlexTheme.type.label,
                colour = TvPalette.textDim,
                maxLines = 1,
            )
            Spacer(Modifier.width(Spacing.xs))
            if (inCollection) {
                TvButton(label = "Back to library", key = "close-collection", glyph = TvGlyph.BACK, onClick = onCloseCollection)
            } else {
                TvButton(
                    label = "Unwatched only",
                    key = "unwatched-only",
                    glyph = if (unwatchedOnly) TvGlyph.CHECK else null,
                    onClick = { onUnwatchedOnlyChange(!unwatchedOnly) },
                )
            }
        }
    }
}

/**
 * The libraries chooser — the Library tab with nothing open: one tile per library the server
 * shares with this account, alphabetical, each opening its grid.
 */
@Composable
internal fun TvLibraries(
    libraries: List<Library>,
    onOpenLibrary: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = rememberTvZone("libraries")
    TvFirstFocus(zone, key = libraries.isNotEmpty())
    TvZone(zone) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = modifier.fillMaxSize().background(TvPalette.ground).tvZone(zone),
            contentPadding = PaddingValues(
                start = TvDims.contentStart,
                end = TvDims.overscanX,
                top = TvDims.overscanY,
                bottom = TvDims.overscanY + Spacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = Spacing.xs)) {
                    PlexText(text = "Libraries", style = PlexTheme.type.display, colour = TvPalette.text, maxLines = 1)
                    PlexText(
                        text = if (libraries.isEmpty()) "No movie or show libraries are shared with this account."
                        else if (libraries.size == 1) "1 library" else "${libraries.size} libraries",
                        style = PlexTheme.type.label,
                        colour = TvPalette.textDim,
                        maxLines = 1,
                    )
                }
            }
            itemsIndexed(libraries, key = { _, it -> it.key }) { _, library ->
                TvTileCard(
                    label = library.title,
                    key = library.key,
                    caption = when (library.kind) {
                        LibraryKind.MOVIE -> "Movies"
                        LibraryKind.SHOW -> "Series"
                        LibraryKind.UNSUPPORTED -> "Unsupported"
                    },
                    aspect = 16f / 9f,
                    width = 240.dp,
                    glyph = TvGlyph.GRID,
                    onClick = { onOpenLibrary(library) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
