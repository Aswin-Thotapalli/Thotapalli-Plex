package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SearchState
import com.thotapalli.plex.ui.shared.SectionHeader

/**
 * Search: one field, results grouped under Movies, Shows and Episodes.
 *
 * The 300 ms debounce and the two character minimum live in the view model, because they
 * govern when a request is made rather than how the field looks.
 * See CLAUDE.md section 14.
 */
@Composable
fun SearchScreen(
    server: ActiveServer,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
) {
    val colours = PlexTheme.colours
    val sizeClass = PlexTheme.sizeClass

    ContentWidthCap(modifier) {
        Column(Modifier.fillMaxSize().padding(sizeClass.screenPadding)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colours.surface, Radius.pill)
                    .border(1.dp, colours.border, Radius.pill)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                if (state.query.isEmpty()) {
                    PlexText(
                        text = "Search",
                        style = PlexTheme.type.body,
                        colour = colours.textSecondary,
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(PlexTheme.type.body)
                        .copy(color = colours.textPrimary),
                    cursorBrush = SolidColor(colours.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }

            val results = state.results

            when {
                state.query.length < 2 -> Hint("Type at least two characters.")
                state.searching && results == null -> Hint("Searching")
                results == null || results.isEmpty -> Hint("No results for \"${state.query}\".")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = PaddingValues(vertical = Spacing.md),
                ) {
                    resultGroup("Movies", results.movies, server, onItemClick)
                    resultGroup("Shows", results.shows, server, onItemClick)
                    resultGroup("Episodes", results.episodes, server, onItemClick)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultGroup(
    title: String,
    items: List<MediaItem>,
    server: ActiveServer,
    onItemClick: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    item { SectionHeader(title) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(items, key = { it.ratingKey }) { entry ->
                PosterTile(
                    item = entry,
                    artworkUrl = server.urls.artwork(
                        entry.thumbPath,
                        ArtworkSize.POSTER_WIDTH,
                        ArtworkSize.POSTER_HEIGHT,
                    ),
                    onClick = { onItemClick(entry) },
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    PlexText(
        text = text,
        colour = PlexTheme.colours.textSecondary,
        modifier = Modifier.padding(Spacing.lg),
    )
}
