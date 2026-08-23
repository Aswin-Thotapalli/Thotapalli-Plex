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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CollectionTile
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.LibraryState
import com.thotapalli.plex.ui.shared.PosterGrid
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.SkeletonPosterGrid
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

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
    onScanLibrary: (Library) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    scanProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val insideCollection = state.openCollection != null
    // On a television the first tile in the grid takes focus on entry, so the remote lands
    // somewhere. Collections are emitted first, so the index-0 collection is the true first
    // tile; where no collections show, the index-0 poster is. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = PlexTheme.sizeClass.isTelevision)
    val collectionsShown = !insideCollection && state.collections.isNotEmpty()

    // The screen root lays the GROUND veil over the app's ambient backdrop: content stays readable
    // while the featured art still bleeds through, so the library shares the one glass world as the
    // navigation instead of sitting on an opaque panel. See CLAUDE.md section 12.
    Column(modifier = modifier.fillMaxSize().material(GlassRole.GROUND)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // A per-library scan, so the owner can pull in newly added files without
                    // leaving the app. See CLAUDE.md section 5.
                    TextChip(
                        label = "Scan",
                        selected = false,
                        onClick = { onScanLibrary(state.library) },
                    )
                    TextChip(
                        label = "Unwatched only",
                        selected = state.unwatchedOnly,
                        onClick = { onUnwatchedOnlyChange(!state.unwatchedOnly) },
                    )
                }
            }
        }

        // A live scan of the library's files, driven by the server's running jobs. A slim glass
        // strip with the accent fill tracking the server's progress, mirroring Plex's own scan
        // read-out. See CLAUDE.md section 5.
        if (scanProgress != null && !insideCollection) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlexTheme.sizeClass.screenPadding, vertical = Spacing.xxs)
                    .material(GlassRole.CHIP, shape = Radius.pill)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PlexText(
                    text = "Scanning…",
                    style = PlexTheme.type.label,
                    colour = PlexTheme.colours.textPrimary,
                    maxLines = 1,
                )
                LinearProgressIndicator(
                    progress = { scanProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).clip(Radius.pill),
                    color = PlexTheme.colours.accent,
                    trackColor = PlexTheme.colours.surface,
                )
            }
        }

        if (state.loading && state.items.isEmpty() && state.collections.isEmpty()) {
            SkeletonPosterGrid(Modifier.fillMaxSize())
            return@Column
        }

        // The scrolling grid is the Haze source the frosted navigation samples and blurs.
        PosterGrid(modifier = Modifier.glassSource()) {
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
                        modifier = Modifier
                            .staggeredEntrance(index, key = collection.ratingKey)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            // The stacked collection plate is a card-like surface, so it floats on the
                            // shared glass rather than reading as a bare poster. See CLAUDE.md §12.
                            .material(GlassRole.CARD, shape = Radius.poster),
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
                    actions = itemActions(item),
                    modifier = Modifier
                        .staggeredEntrance(index, key = item.ratingKey)
                        .then(
                            if (!collectionsShown && index == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        ),
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
            .clip(Radius.pill)
            // Selected is a solid amber pill; unselected takes the calibrated CHIP glass, so the chip
            // reads as chrome without competing with the poster art around it. See CLAUDE.md §12.
            .then(
                if (selected) {
                    Modifier
                        .background(colours.accent, Radius.pill)
                        .border(1.dp, colours.accent, Radius.pill)
                } else {
                    Modifier.material(GlassRole.CHIP, shape = Radius.pill)
                },
            )
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
