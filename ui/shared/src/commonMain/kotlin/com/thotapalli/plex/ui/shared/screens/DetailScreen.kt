package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CinematicBackdrop
import com.thotapalli.plex.ui.shared.ambient.AmbientBackground
import com.thotapalli.plex.ui.shared.DetailState
import com.thotapalli.plex.ui.shared.EpisodeRow
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PrimaryButton
import com.thotapalli.plex.ui.shared.SecondaryButton
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.formatDuration

/**
 * Movie detail and show detail, which share a cinematic header and differ only below it.
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
    val heroHeight = if (sizeClass.twoPaneDetail) 460.dp else 320.dp
    // Content sits in a readable measure and never stretches to a television's full width.
    val contentPadding = sizeClass.screenPadding
    val backdropUrl = server.urls.artwork(
        item.artPath ?: item.thumbPath,
        ArtworkSize.BACKDROP_WIDTH,
        ArtworkSize.BACKDROP_HEIGHT,
    )

    Box(modifier.fillMaxSize()) {
        // The whole screen takes on the colour of the content's own artwork.
        AmbientBackground(url = backdropUrl, modifier = Modifier.fillMaxSize())

        if (sizeClass.twoPaneDetail) {
            // Expanded and Television split below the hero into a fixed left pane and a
            // remainder-filling right pane. See CLAUDE.md section 13.
            Column(Modifier.fillMaxSize()) {
                // The hero stays full width, above the split.
                CinematicBackdrop(
                    url = backdropUrl,
                    title = item.title,
                    height = heroHeight,
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = contentPadding, vertical = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        PlexText(item.title, style = PlexTheme.type.display, maxLines = 3)
                        PlexText(
                            text = metadataLine(item),
                            style = PlexTheme.type.label,
                            colour = PlexTheme.colours.textSecondary,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // Left pane: fixed 380dp, scrolling on its own if the content runs long.
                    Column(
                        modifier = Modifier
                            .width(Layout.detailPaneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = Spacing.md, bottom = Spacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        DetailActions(
                            state = state,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onToggleWatched = onToggleWatched,
                        )

                        // A show keeps its summary on the left; a movie hands it to the right
                        // pane so the left column is not carrying the whole screen alone.
                        if (item !is Movie && item.summary.isNotBlank()) {
                            PlexText(
                                text = item.summary,
                                colour = PlexTheme.colours.textSecondary,
                            )
                        }

                        AudioSubtitleReference(state)
                    }

                    // Right pane: the episode list for a show, the overview for a movie.
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (item is Show) {
                            SectionHeader("Episodes")
                            SeasonSelector(state, onSeasonSelected)
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                items(state.episodesInSelectedSeason, key = { it.ratingKey }) { episode ->
                                    EpisodeRow(
                                        episode = episode,
                                        thumbnailUrl = server.urls.artwork(
                                            episode.thumbPath,
                                            ArtworkSize.THUMB_WIDTH,
                                            ArtworkSize.THUMB_HEIGHT,
                                        ),
                                        onClick = { onPlay(episode, episode.viewOffsetMs) },
                                    )
                                }
                                item { Spacer(Modifier.height(Spacing.xxl)) }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = Spacing.md, bottom = Spacing.xxl),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                SectionHeader("Overview")
                                if (item.summary.isNotBlank()) {
                                    PlexText(
                                        text = item.summary,
                                        colour = PlexTheme.colours.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return@Box
        }

        LazyColumn(Modifier.fillMaxSize()) {
        // The hero: a full-bleed backdrop that fades into the page, with the title and the
        // key facts sitting over the bottom of the image.
        item {
            CinematicBackdrop(
                url = backdropUrl,
                title = item.title,
                height = heroHeight,
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PlexText(item.title, style = PlexTheme.type.display, maxLines = 3)
                    PlexText(
                        text = metadataLine(item),
                        style = PlexTheme.type.label,
                        colour = PlexTheme.colours.textSecondary,
                    )
                }
            }
        }

        // Actions, pulled up slightly so they read as attached to the hero.
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding)
                    .padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                DetailActions(
                    state = state,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onToggleWatched = onToggleWatched,
                )

                if (item.summary.isNotBlank()) {
                    PlexText(
                        text = item.summary,
                        colour = PlexTheme.colours.textSecondary,
                        maxLines = 6,
                    )
                }
            }
        }

        // Audio and subtitle tracks, listed for reference. See CLAUDE.md section 14.
        state.detail?.primaryPart?.let { part ->
            item {
                Column(Modifier.padding(horizontal = contentPadding, vertical = Spacing.md)) {
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
                Column(Modifier.padding(horizontal = contentPadding)) {
                    Spacer(Modifier.height(Spacing.xs))
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
                    modifier = Modifier.padding(horizontal = contentPadding),
                )
            }
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    val resumable = playTarget.partiallyWatched

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PrimaryButton(
            label = when {
                item is Show && state.nextUnwatched != null ->
                    "Play S${pad(state.nextUnwatched.seasonIndex)}E${pad(state.nextUnwatched.episodeIndex)}"
                // Resume immediately, no prompt. See CLAUDE.md section 2.
                resumable -> "Resume"
                else -> "Play"
            },
            onClick = { onPlay(playTarget, resumeFrom) },
            leadingIcon = PlexIconKind.PLAY,
        )
        // Resume leaves a way back to the beginning, which resume-immediately otherwise hides.
        if (resumable) {
            SecondaryButton(label = "From start", onClick = { onPlay(playTarget, 0L) })
        }
        SecondaryButton(
            label = "Download",
            onClick = { onDownload(item) },
            leadingIcon = PlexIconKind.DOWNLOADS,
        )
        SecondaryButton(
            label = if (item.watched) "Watched" else "Mark watched",
            onClick = { onToggleWatched(item) },
            leadingIcon = if (item.watched) PlexIconKind.CHECK else null,
        )
    }
}

/**
 * The audio and subtitle track listing, shown for reference. See CLAUDE.md section 14.
 * Matches the single-column block; used by the two-pane left pane.
 */
@Composable
private fun AudioSubtitleReference(state: DetailState, modifier: Modifier = Modifier) {
    val part = state.detail?.primaryPart ?: return
    if (part.audioStreams.isEmpty() && part.subtitleStreams.isEmpty()) return

    Column(modifier) {
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
