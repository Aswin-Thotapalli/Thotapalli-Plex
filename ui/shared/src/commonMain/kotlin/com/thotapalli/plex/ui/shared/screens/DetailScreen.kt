package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
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
import com.thotapalli.plex.ui.design.backgroundBrush
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.design.liquidGlass
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CinematicBackdrop
import com.thotapalli.plex.ui.shared.ambient.AmbientBackground
import com.thotapalli.plex.ui.shared.DetailState
import com.thotapalli.plex.ui.shared.EpisodeRow
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.ItemOverflowButton
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PrimaryButton
import com.thotapalli.plex.ui.shared.SecondaryButton
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.formatDuration
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * Movie detail and show detail, which share a cinematic header and differ only below it.
 * See CLAUDE.md section 14 items 4 and 5.
 *
 * The screen is built as layers of the redesign: a full-bleed [CinematicBackdrop] bleeds art to
 * the top edge, an [AmbientBackground] carries the artwork's colour down the whole screen and is
 * marked as the glass source, and every cluster of chrome below — the action bar, the track
 * reference, the episode list — floats as a sheet of frosted [liquidGlass] that frosts that
 * colour. Compact and Medium scroll one column; Expanded and Television split into two panes.
 */
@Composable
fun DetailScreen(
    server: ActiveServer,
    state: DetailState,
    onPlay: (MediaItem, resumeFromMs: Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    actions: ItemActions? = null,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val item = state.item
    // On a television the primary Play/Resume action takes first focus on entry, so the
    // remote lands on the one action that matters. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = sizeClass.isTelevision)
    val heroHeight = if (sizeClass.twoPaneDetail) 460.dp else 320.dp
    // Content sits in a readable measure and never stretches to a television's full width.
    val contentPadding = sizeClass.screenPadding
    val backdropUrl = server.urls.artwork(
        item.artPath ?: item.thumbPath,
        ArtworkSize.BACKDROP_WIDTH,
        ArtworkSize.BACKDROP_HEIGHT,
    )

    Box(
        modifier
            .fillMaxSize()
            // The deep-indigo ground under everything, so the art dissolves onto the page.
            .background(PlexTheme.colours.backgroundBrush()),
    ) {
        // The whole screen takes on the colour of the content's own artwork, and that wash is the
        // glass source: every frosted panel below samples and blurs it.
        AmbientBackground(
            url = backdropUrl,
            modifier = Modifier.fillMaxSize().glassSource(),
        )

        if (sizeClass.twoPaneDetail) {
            TwoPaneDetail(
                server = server,
                state = state,
                heroHeight = heroHeight,
                contentPadding = contentPadding,
                backdropUrl = backdropUrl,
                firstFocus = firstFocus,
                onPlay = onPlay,
                onDownload = onDownload,
                onToggleWatched = onToggleWatched,
                onSeasonSelected = onSeasonSelected,
                onSelectEpisode = onSelectEpisode,
                actions = actions,
            )
        } else {
            SingleColumnDetail(
                server = server,
                state = state,
                heroHeight = heroHeight,
                contentPadding = contentPadding,
                backdropUrl = backdropUrl,
                firstFocus = firstFocus,
                onPlay = onPlay,
                onDownload = onDownload,
                onToggleWatched = onToggleWatched,
                onSeasonSelected = onSeasonSelected,
                onSelectEpisode = onSelectEpisode,
                actions = actions,
            )
        }
    }
}

// --- single column, Compact and Medium ---------------------------------------------------

@Composable
private fun SingleColumnDetail(
    server: ActiveServer,
    state: DetailState,
    heroHeight: Dp,
    contentPadding: Dp,
    backdropUrl: String?,
    firstFocus: FocusRequester?,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    actions: ItemActions?,
) {
    val item = state.item

    LazyColumn(Modifier.fillMaxSize()) {
        // The hero: a full-bleed backdrop that bleeds to the top edge and fades into the page,
        // with the title and the key facts over the bottom of the image.
        item {
            CinematicBackdrop(url = backdropUrl, title = item.title, height = heroHeight) {
                HeroCaption(item, Modifier.align(Alignment.BottomStart).padding(horizontal = contentPadding, vertical = Spacing.lg))
            }
        }

        // The action cluster: a frosted panel, pulled up to read as attached to the hero, that
        // groups the primary and secondary actions and the summary.
        item {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding)
                    .padding(top = Spacing.md)
                    .staggeredEntrance(index = 0),
            ) {
                DetailActions(
                    state = state,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onToggleWatched = onToggleWatched,
                    actions = actions,
                    firstFocus = firstFocus,
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

        // Audio and subtitle tracks, listed for reference on a quiet glass panel. See §14.
        val part = state.detail?.primaryPart
        if (part != null && (part.audioStreams.isNotEmpty() || part.subtitleStreams.isNotEmpty())) {
            item {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = Spacing.md)
                        .staggeredEntrance(index = 1),
                ) {
                    AudioSubtitleReference(state)
                }
            }
        }

        if (item is Show) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = Spacing.md),
                ) {
                    SectionHeader("Episodes")
                    SeasonSelector(state, onSeasonSelected)
                }
            }

            item {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = Spacing.xs),
                    contentPadding = Spacing.xs,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    state.episodesInSelectedSeason.forEachIndexed { index, episode ->
                        EpisodeRow(
                            episode = episode,
                            thumbnailUrl = server.urls.artwork(
                                episode.thumbPath,
                                ArtworkSize.THUMB_WIDTH,
                                ArtworkSize.THUMB_HEIGHT,
                            ),
                            onPlay = { onPlay(episode, episode.viewOffsetMs) },
                            onSelect = { onSelectEpisode(episode) },
                            selected = episode.ratingKey == state.selectedEpisode?.ratingKey,
                            actions = actions,
                            modifier = Modifier.staggeredEntrance(index = index, key = episode.ratingKey),
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

// --- two pane, Expanded and Television ---------------------------------------------------

@Composable
private fun TwoPaneDetail(
    server: ActiveServer,
    state: DetailState,
    heroHeight: Dp,
    contentPadding: Dp,
    backdropUrl: String?,
    firstFocus: FocusRequester?,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    actions: ItemActions?,
) {
    val item = state.item

    Column(Modifier.fillMaxSize()) {
        // The hero stays full width, above the split, bleeding to the top edge.
        CinematicBackdrop(url = backdropUrl, title = item.title, height = heroHeight) {
            HeroCaption(item, Modifier.align(Alignment.BottomStart).padding(horizontal = contentPadding, vertical = Spacing.lg))
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
                GlassPanel(modifier = Modifier.fillMaxWidth().staggeredEntrance(index = 0)) {
                    DetailActions(
                        state = state,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onToggleWatched = onToggleWatched,
                        actions = actions,
                        firstFocus = firstFocus,
                    )
                    // A show keeps its summary on the left; a movie hands it to the right pane so
                    // the left column is not carrying the whole screen alone.
                    if (item !is Movie && item.summary.isNotBlank()) {
                        PlexText(text = item.summary, colour = PlexTheme.colours.textSecondary)
                    }
                }

                val part = state.detail?.primaryPart
                if (part != null && (part.audioStreams.isNotEmpty() || part.subtitleStreams.isNotEmpty())) {
                    GlassPanel(modifier = Modifier.fillMaxWidth().staggeredEntrance(index = 1)) {
                        AudioSubtitleReference(state)
                    }
                }
            }

            // Right pane: the episode list for a show, the overview for a movie, on one tall
            // frosted panel filling the remainder.
            if (item is Show) {
                GlassPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = Spacing.md, bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SectionHeader("Episodes")
                    SeasonSelector(state, onSeasonSelected)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        itemsIndexed(
                            state.episodesInSelectedSeason,
                            key = { _, ep -> ep.ratingKey },
                        ) { index, episode ->
                            EpisodeRow(
                                episode = episode,
                                thumbnailUrl = server.urls.artwork(
                                    episode.thumbPath,
                                    ArtworkSize.THUMB_WIDTH,
                                    ArtworkSize.THUMB_HEIGHT,
                                ),
                                onPlay = { onPlay(episode, episode.viewOffsetMs) },
                                onSelect = { onSelectEpisode(episode) },
                                selected = episode.ratingKey == state.selectedEpisode?.ratingKey,
                                actions = actions,
                                modifier = Modifier.staggeredEntrance(index = index, key = episode.ratingKey),
                            )
                        }
                        item { Spacer(Modifier.height(Spacing.lg)) }
                    }
                }
            } else {
                GlassPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = Spacing.md, bottom = Spacing.xxl)
                        .staggeredEntrance(index = 2),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SectionHeader("Overview")
                    if (item.summary.isNotBlank()) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            PlexText(text = item.summary, colour = PlexTheme.colours.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

// --- shared pieces -----------------------------------------------------------------------

/** The title and one line of key facts, over the darkest foot of the hero fade. */
@Composable
private fun HeroCaption(item: MediaItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
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

/**
 * A sheet of frosted liquid glass hosting a stack of detail content. Every chrome cluster on the
 * screen — the actions, the track reference, the episode list — floats as one of these, so it
 * frosts the ambient artwork behind and reads as part of one bubbly glass system.
 */
@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.glass,
    contentPadding: Dp = Spacing.md,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.sm),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.liquidGlass(shape = shape).padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(
    state: DetailState,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    actions: ItemActions? = null,
    firstFocus: FocusRequester? = null,
) {
    val item = state.item
    // A show plays its next unwatched episode; if every episode is watched it plays the
    // first of the shown season rather than the show itself, which has no file to play and
    // would otherwise stall at 0:00. A movie has no episodes, so it stays the item.
    // See CLAUDE.md section 14 item 5.
    val playTarget: MediaItem = state.nextUnwatched
        ?: state.episodesInSelectedSeason.firstOrNull()
        ?: state.episodes.firstOrNull()
        ?: item
    val resumeFrom = playTarget.viewOffsetMs
    val resumable = playTarget.partiallyWatched

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
            modifier = if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
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
        // The overflow carries the rest of the server actions (refresh, delete) so the header
        // stays to its primary Play and Download while everything else lives one tap away.
        if (actions != null) {
            ItemOverflowButton(item = item, actions = actions)
        }
    }
}

/**
 * The audio and subtitle track listing, shown for reference. See CLAUDE.md section 14.
 * Rendered inside a quiet glass panel by both layouts.
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

/**
 * The season selector: a scrolling row of glass segmented pills. The chosen season lights amber
 * like every other selection in the app; the rest are quiet frosted glass.
 */
@Composable
private fun SeasonSelector(state: DetailState, onSeasonSelected: (Season) -> Unit) {
    if (state.seasons.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(vertical = Spacing.xs),
    ) {
        items(state.seasons, key = { it.ratingKey }) { season ->
            SeasonPill(
                label = season.title,
                selected = season.ratingKey == state.selectedSeason?.ratingKey,
                onClick = { onSeasonSelected(season) },
            )
        }
    }
}

/**
 * One segmented pill. Selected is a lit amber lozenge with the same warm accent every selection
 * in the app carries; unselected is a sheet of frosted glass that frosts the backdrop behind it.
 */
@Composable
private fun SeasonPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    val onAccent = if (colours.isDark) colours.background else Color.White
    val base = if (selected) {
        Modifier.background(colours.accent, Radius.pill)
    } else {
        Modifier.liquidGlass(shape = Radius.pill)
    }
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick)
            .then(base)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = if (selected) onAccent else colours.textSecondary,
            maxLines = 1,
        )
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
