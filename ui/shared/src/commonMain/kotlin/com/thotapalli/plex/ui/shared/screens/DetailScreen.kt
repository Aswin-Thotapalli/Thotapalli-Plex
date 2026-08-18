package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.partiallyWatched
import com.thotapalli.plex.core.model.watched
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.DetailState
import com.thotapalli.plex.ui.shared.EpisodeRow
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.formatDuration
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * Movie detail and show detail, which share a header and differ only below it.
 * See CLAUDE.md section 14 items 4 and 5.
 */
@Composable
fun DetailScreen(
    server: ActiveServer,
    state: DetailState,
    onPlay: (MediaItem, resumeFromMs: Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val item = state.item

    ContentWidthCap(modifier) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Backdrop(
                    url = server.urls.artwork(
                        item.artPath ?: item.thumbPath,
                        ArtworkSize.BACKDROP_WIDTH,
                        ArtworkSize.BACKDROP_HEIGHT,
                    ),
                    title = item.title,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(sizeClass.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Box(
                        Modifier
                            .width(if (sizeClass.twoPaneDetail) 200.dp else 132.dp)
                            .aspectRatio(Layout.POSTER_ASPECT_RATIO)
                            .clip(Radius.poster),
                    ) {
                        Artwork(
                            url = server.urls.artwork(
                                item.thumbPath,
                                ArtworkSize.POSTER_WIDTH,
                                ArtworkSize.POSTER_HEIGHT,
                            ),
                            contentDescription = item.title,
                            fallbackTitle = item.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        PlexText(item.title, style = PlexTheme.type.display, maxLines = 3)
                        PlexText(
                            text = metadataLine(item),
                            style = PlexTheme.type.caption,
                            colour = PlexTheme.colours.textSecondary,
                        )

                        Spacer(Modifier.height(Spacing.xs))

                        DetailActions(
                            state = state,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onToggleWatched = onToggleWatched,
                        )

                        if (item.summary.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.xs))
                            PlexText(
                                text = item.summary,
                                colour = PlexTheme.colours.textSecondary,
                                maxLines = 8,
                            )
                        }
                    }
                }
            }

            // Audio and subtitle tracks, listed for reference. See CLAUDE.md section 14.
            state.detail?.primaryPart?.let { part ->
                item {
                    Column(Modifier.padding(horizontal = sizeClass.screenPadding)) {
                        if (part.audioStreams.isNotEmpty()) {
                            SectionHeader("Audio")
                            part.audioStreams.forEach { stream ->
                                PlexText(
                                    text = listOfNotNull(
                                        stream.title ?: stream.language,
                                        stream.codec.uppercase(),
                                        "${stream.channels}ch".takeIf { stream.channels > 0 },
                                    ).joinToString("  "),
                                    style = PlexTheme.type.caption,
                                    colour = PlexTheme.colours.textSecondary,
                                )
                            }
                        }
                        if (part.subtitleStreams.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.sm))
                            SectionHeader("Subtitles")
                            part.subtitleStreams.forEach { stream ->
                                PlexText(
                                    text = listOfNotNull(
                                        stream.title ?: stream.language,
                                        stream.codec.uppercase(),
                                        "forced".takeIf { stream.forced },
                                    ).joinToString("  "),
                                    style = PlexTheme.type.caption,
                                    colour = PlexTheme.colours.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            if (item is Show) {
                item {
                    Column(Modifier.padding(horizontal = sizeClass.screenPadding)) {
                        Spacer(Modifier.height(Spacing.md))
                        SectionHeader("Episodes")
                        SeasonSelector(state, onSeasonSelected)
                    }
                }

                items(state.episodesInSelectedSeason, key = { it.ratingKey }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        thumbnailUrl = server.urls.artwork(
                            episode.thumbPath,
                            ArtworkSize.THUMB_WIDTH,
                            ArtworkSize.THUMB_HEIGHT,
                        ),
                        onClick = { onPlay(episode, episode.viewOffsetMs) },
                        modifier = Modifier.padding(horizontal = sizeClass.screenPadding),
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
private fun DetailActions(
    state: DetailState,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
) {
    val item = state.item
    // A show plays its next unwatched episode. See CLAUDE.md section 14 item 5.
    val playTarget: MediaItem = state.nextUnwatched ?: item
    val resumeFrom = playTarget.viewOffsetMs

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PrimaryAction(
            label = when {
                item is Show && state.nextUnwatched != null ->
                    "Play S${pad(state.nextUnwatched.seasonIndex)}E${pad(state.nextUnwatched.episodeIndex)}"
                // Resume immediately, no prompt. See CLAUDE.md section 2.
                playTarget.partiallyWatched -> "Resume"
                else -> "Play"
            },
            onClick = { onPlay(playTarget, resumeFrom) },
        )
        SecondaryAction("Download", onClick = { onDownload(item) })
        SecondaryAction(
            label = if (item.watched) "Mark as unwatched" else "Mark as watched",
            onClick = { onToggleWatched(item) },
        )
    }
}

@Composable
private fun SeasonSelector(state: DetailState, onSeasonSelected: (Season) -> Unit) {
    if (state.seasons.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(vertical = Spacing.xs),
    ) {
        items(state.seasons, key = { it.ratingKey }) { season ->
            TextChip(
                label = season.title,
                selected = season.ratingKey == state.selectedSeason?.ratingKey,
                onClick = { onSeasonSelected(season) },
            )
        }
    }
}

@Composable
private fun Backdrop(url: String?, title: String) {
    val colours = PlexTheme.colours
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (PlexTheme.sizeClass.twoPaneDetail) 320.dp else 200.dp),
    ) {
        Artwork(
            url = url,
            contentDescription = null,
            fallbackTitle = title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to colours.scrim, 1f to colours.background),
            ),
        )
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(colours.accent, Radius.pill)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = if (colours.isDark) colours.background else colours.surface,
        )
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(colours.surface, Radius.pill)
            .border(1.dp, colours.border, Radius.pill)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        PlexText(text = label, style = PlexTheme.type.label, colour = colours.textSecondary)
    }
}

private fun metadataLine(item: MediaItem): String = when (item) {
    is Movie -> listOfNotNull(item.year?.toString(), formatDuration(item.durationMs))
        .joinToString("  ")
    is Show -> listOfNotNull(
        item.year?.toString(),
        "${item.childCount} seasons".takeIf { item.childCount > 0 },
        "${item.leafCount} episodes".takeIf { item.leafCount > 0 },
    ).joinToString("  ")
    is Episode -> "S${pad(item.seasonIndex)}E${pad(item.episodeIndex)}  ${formatDuration(item.durationMs)}"
    else -> formatDuration(item.durationMs)
}

private fun pad(value: Int) = value.toString().padStart(2, '0')
