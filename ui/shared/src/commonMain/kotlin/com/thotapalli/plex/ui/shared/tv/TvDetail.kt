package com.thotapalli.plex.ui.shared.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.DetailState
import com.thotapalli.plex.ui.shared.ProgressBar
import com.thotapalli.plex.ui.shared.formatDuration

/**
 * The detail for a movie, a show, or an episode: a cinematic hero, the actions, then for a show
 * the seasons rail and the episode list. One column that scrolls as a whole, every row composed,
 * so DOWN walks from Play to the last episode with nothing off-composition to fall through.
 *
 * Focus: first focus is the primary action. Moving along the seasons rail selects the season, so
 * the list beneath follows the remote without a second press. Select on an episode plays it; a
 * held select opens its menu. See CLAUDE.md section 14 items 4 and 5.
 */
@Composable
internal fun TvDetail(
    server: ActiveServer,
    state: DetailState,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onSetContainerWatched: (String, Boolean) -> Unit,
    onItemMenu: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.item
    val zone = rememberTvZone("detail-" + item.ratingKey)
    TvFirstFocus(zone, key = item.ratingKey)

    val backdropUrl = server.urls.artwork(
        item.artPath ?: item.thumbPath,
        ArtworkSize.BACKDROP_WIDTH,
        ArtworkSize.BACKDROP_HEIGHT,
    )

    TvZone(zone) {
        BoxWithConstraints(modifier.fillMaxSize().background(TvPalette.ground).tvZone(zone)) {
            val heroHeight = (maxHeight * 0.52f).coerceIn(320.dp, 540.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    Artwork(
                        url = backdropUrl,
                        contentDescription = item.title,
                        fallbackTitle = item.title,
                        modifier = Modifier.fillMaxSize(),
                        alignment = Alignment.TopCenter,
                    )
                    TvHeroScrims()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.62f)
                            .padding(start = TvDims.contentStart, end = Spacing.xl, bottom = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        TvHeroCaption(server = server, item = item, synopsisLines = 3)
                    }
                }

                TvDetailActions(
                    state = state,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onToggleWatched = onToggleWatched,
                    modifier = Modifier.padding(horizontal = TvDims.contentStart, vertical = Spacing.md),
                )

                if (item is Show || item is Episode) {
                    TvSeasons(server, state, onSeasonSelected, onSetContainerWatched)
                    Spacer(Modifier.height(Spacing.md))
                    Column(
                        modifier = Modifier.padding(start = TvDims.contentStart, end = TvDims.overscanX),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        state.episodesInSelectedSeason.forEach { episode ->
                            TvEpisodeRow(
                                episode = episode,
                                thumbnailUrl = server.urls.artwork(episode.thumbPath, ArtworkSize.THUMB_WIDTH, ArtworkSize.THUMB_HEIGHT),
                                selected = episode.ratingKey == state.selectedEpisode?.ratingKey,
                                onPlay = { onPlay(episode, episode.viewOffsetMs) },
                                onFocused = { onSelectEpisode(episode) },
                                onMenu = { onItemMenu(episode) },
                            )
                        }
                        if (state.episodesInSelectedSeason.isEmpty()) {
                            PlexText(
                                text = if (state.loading) "Loading episodes…" else "No episodes in this season.",
                                style = PlexTheme.type.body,
                                colour = TvPalette.textDim,
                            )
                        }
                    }
                } else {
                    TvMediaInfo(state, Modifier.padding(horizontal = TvDims.contentStart))
                }
                Spacer(Modifier.height(TvDims.overscanY + Spacing.xxl))
            }
        }
    }
}

/** The action cluster: gold primary, quiet secondaries. First focus lands on the primary. */
@Composable
private fun TvDetailActions(
    state: DetailState,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.item
    val playTarget: MediaItem = when {
        item is Episode -> state.selectedEpisode ?: item
        else -> state.nextUnwatched
            ?: state.episodesInSelectedSeason.firstOrNull()
            ?: state.episodes.firstOrNull()
            ?: item
    }
    val resumable = playTarget.viewOffsetMs > 0L && playTarget.durationMs > 0L && playTarget.viewCount == 0
    val watched = item.viewCount > 0 || (item is Show && item.leafCount > 0 && item.viewedLeafCount >= item.leafCount)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TvButton(
            label = when {
                item is Show && state.nextUnwatched != null ->
                    "Play S${two(state.nextUnwatched.seasonIndex)}E${two(state.nextUnwatched.episodeIndex)}"
                resumable -> "Resume"
                else -> "Play"
            },
            key = "play",
            glyph = TvGlyph.PLAY,
            primary = true,
            onClick = { onPlay(playTarget, playTarget.viewOffsetMs) },
        )
        if (resumable) {
            TvButton(label = "From start", key = "from-start", onClick = { onPlay(playTarget, 0L) })
        }
        TvButton(label = "Download", key = "download", onClick = { onDownload(playTarget) })
        TvButton(
            label = if (watched) "Mark unwatched" else "Mark watched",
            key = "watched",
            glyph = if (watched) TvGlyph.CHECK else null,
            onClick = { onToggleWatched(item) },
        )
    }
}

/** The seasons rail. Focus selects; the episode list beneath follows. */
@Composable
private fun TvSeasons(
    server: ActiveServer,
    state: DetailState,
    onSeasonSelected: (Season) -> Unit,
    onSetContainerWatched: (String, Boolean) -> Unit,
) {
    if (state.seasons.isEmpty()) return
    val showKey = (state.item as? Show)?.ratingKey ?: (state.item as? Episode)?.showRatingKey ?: state.item.ratingKey
    TvRail(
        title = if (state.seasons.size == 1) "1 season" else "${state.seasons.size} seasons",
        zone = rememberTvZone("detail-seasons-$showKey"),
    ) {
        items(state.seasons, key = { it.ratingKey }) { season ->
            val selected = season.ratingKey == state.selectedSeason?.ratingKey
            val complete = season.leafCount > 0 && season.viewedLeafCount >= season.leafCount
            TvPosterCard(
                item = season,
                artworkUrl = server.urls.artwork(season.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT),
                width = 132.dp,
                badge = if (complete) "Watched" else null,
                onClick = { onSeasonSelected(season) },
                onFocused = { if (!selected) onSeasonSelected(season) },
                onLongClick = { onSetContainerWatched(season.ratingKey, !complete) },
            )
        }
    }
}

/**
 * One episode: thumbnail with a resume bar, number, title, duration, and the synopsis on the
 * selected row. A solid light fill under focus. Select plays, a held select opens the menu.
 */
@Composable
private fun TvEpisodeRow(
    episode: Episode,
    thumbnailUrl: String?,
    selected: Boolean,
    onPlay: () -> Unit,
    onFocused: () -> Unit,
    onMenu: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, label = "tv-episode")
    val watched = episode.viewCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(TvShape.card)
            .background(
                when {
                    focused -> TvPalette.chipFocus
                    selected -> TvPalette.surfaceRaised
                    else -> TvPalette.surface
                },
            )
            .tvInteractive(interaction, key = episode.ratingKey, onClick = onPlay, onLongClick = onMenu, onFocused = onFocused)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier
                .width(THUMB_WIDTH)
                .aspectRatio(16f / 9f)
                .clip(TvShape.poster)
                .then(if (focused) Modifier.border(2.dp, TvPalette.gold, TvShape.poster) else Modifier),
        ) {
            Artwork(url = thumbnailUrl, contentDescription = episode.title, fallbackTitle = episode.title, modifier = Modifier.fillMaxSize())
            if (episode.progress > 0f && !watched) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Spacing.xxs)) {
                    ProgressBar(progress = episode.progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val ink = if (focused) TvPalette.ink else TvPalette.text
        val dim = if (focused) TvPalette.ink.copy(alpha = 0.7f) else TvPalette.textDim
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PlexText(text = two(episode.episodeIndex), style = PlexTheme.type.label, colour = if (focused) TvPalette.ink else TvPalette.gold, maxLines = 1)
                PlexText(text = episode.title, style = PlexTheme.type.body, colour = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (watched) TvGlyphIcon(TvGlyph.CHECK, tint = if (focused) TvPalette.ink else TvPalette.gold, size = 20.dp)
            }
            val facts = buildList {
                if (episode.durationMs > 0L) add(formatDuration(episode.durationMs))
                if (episode.progress > 0f && !watched) add("${(episode.progress * 100).toInt()}% watched")
            }
            if (facts.isNotEmpty()) {
                PlexText(text = facts.joinToString("  •  "), style = PlexTheme.type.caption, colour = dim, maxLines = 1)
            }
            if ((selected || focused) && episode.summary.isNotBlank()) {
                PlexText(text = episode.summary, style = PlexTheme.type.caption, colour = dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The audio and subtitle tracks listed for reference on a movie. See CLAUDE.md section 14 item 4. */
@Composable
private fun TvMediaInfo(state: DetailState, modifier: Modifier = Modifier) {
    val part = state.detail?.primaryPart ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        TvCaption("Audio")
        PlexText(
            text = part.audioStreams.joinToString("  •  ") { it.title ?: it.codec.uppercase() }.ifBlank { "—" },
            style = PlexTheme.type.body, colour = TvPalette.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        TvCaption("Subtitles")
        PlexText(
            text = part.subtitleStreams.joinToString("  •  ") { it.title ?: it.codec.uppercase() }.ifBlank { "None" },
            style = PlexTheme.type.body, colour = TvPalette.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val THUMB_WIDTH: Dp = 200.dp

private fun two(n: Int): String = if (n < 10) "0$n" else n.toString()
