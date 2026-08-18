package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CollectionTile
import com.thotapalli.plex.ui.shared.LibraryState
import com.thotapalli.plex.ui.shared.PosterGrid
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SectionHeader

/**
 * Library: a poster grid sorted alphabetically. Collections first with a stacked poster
 * treatment, then individual titles. One filter, "Unwatched only". The sort is fixed and
 * has no control. See CLAUDE.md section 14.
 */
@Composable
fun LibraryScreen(
    server: ActiveServer,
    state: LibraryState,
    onItemClick: (MediaItem) -> Unit,
    onCollectionClick: (MediaCollection) -> Unit,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onCloseCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insideCollection = state.openCollection != null

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PlexTheme.sizeClass.screenPadding,
                    vertical = Spacing.xs,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                title = state.openCollection?.title ?: state.library.title,
                modifier = Modifier.weight(1f),
            )

            if (insideCollection) {
                TextChip(label = "Back to library", selected = false, onClick = onCloseCollection)
            } else {
                TextChip(
                    label = "Unwatched only",
                    selected = state.unwatchedOnly,
                    onClick = { onUnwatchedOnlyChange(!state.unwatchedOnly) },
                )
            }
        }

        if (state.loading && state.items.isEmpty() && state.collections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlexText("Loading", colour = PlexTheme.colours.textSecondary)
            }
            return@Column
        }

        PosterGrid {
            if (!insideCollection && state.collections.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Collections", Modifier.padding(top = Spacing.xs))
                }
                items(state.collections.size, key = { state.collections[it].ratingKey }) { index ->
                    val collection = state.collections[index]
                    CollectionTile(
                        collection = collection,
                        artworkUrl = server.urls.artwork(
                            collection.thumbPath,
                            ArtworkSize.POSTER_WIDTH,
                            ArtworkSize.POSTER_HEIGHT,
                        ),
                        onClick = { onCollectionClick(collection) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(state.library.title, Modifier.padding(top = Spacing.md))
                }
            }

            items(state.items.size, key = { state.items[it].ratingKey }) { index ->
                val item = state.items[index]
                PosterTile(
                    item = item,
                    artworkUrl = server.urls.artwork(
                        item.thumbPath,
                        ArtworkSize.POSTER_WIDTH,
                        ArtworkSize.POSTER_HEIGHT,
                    ),
                    onClick = { onItemClick(item) },
                )
            }

            if (state.items.isEmpty() && state.collections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PlexText(
                        text = if (state.unwatchedOnly) {
                            "Everything in this library has been watched."
                        } else {
                            "This library is empty."
                        },
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
    }
}

/** A pill. The accent marks selection and nothing else. See CLAUDE.md section 12. */
@Composable
internal fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .background(if (selected) colours.accent else colours.surface, Radius.pill)
            .border(1.dp, if (selected) colours.accent else colours.border, Radius.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = when {
                selected && colours.isDark -> colours.background
                selected -> colours.surface
                else -> colours.textSecondary
            },
        )
    }
}
