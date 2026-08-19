package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.core.model.watched
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing

/**
 * A poster tile: 2:3 artwork, title beneath, and a progress bar only when the item is part
 * way through. See CLAUDE.md section 12.
 */
@Composable
fun PosterTile(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.poster, onClick = onClick)
            .padding(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Layout.POSTER_ASPECT_RATIO)
                // A soft drop shadow lifts the poster off the near-black ground so the wall
                // of tiles reads as physical cards rather than a flat collage.
                .shadow(
                    elevation = 10.dp,
                    shape = Radius.poster,
                    ambientColor = colours.elevationShadow,
                    spotColor = colours.elevationShadow,
                )
                .clip(Radius.poster),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = item.title,
                fallbackTitle = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            // A quiet bottom gradient so a badge or a progress bar keeps contrast over a
            // bright poster without dimming the art itself.
            PosterFooterScrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())

            if (item.watched) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
            }

            if (item.progress > 0f && !item.watched) {
                ProgressBar(
                    progress = item.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.xs),
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        PlexText(text = item.title, style = PlexTheme.type.label, maxLines = 2)

        secondaryLine(item)?.let {
            PlexText(text = it, style = PlexTheme.type.caption, colour = colours.textSecondary, maxLines = 1)
        }
    }
}

/**
 * A collection poster, with the stacked treatment that distinguishes it from a single
 * title at a glance. See CLAUDE.md section 14.
 */
@Composable
fun CollectionTile(
    collection: MediaCollection,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.poster, onClick = onClick)
            .padding(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Layout.POSTER_ASPECT_RATIO),
        ) {
            // Two offset plates behind the artwork read as a stack of posters.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = Spacing.xs, top = 0.dp, end = 0.dp, bottom = Spacing.xs)
                    .offset(x = Spacing.xs, y = (-6).dp)
                    .background(colours.surfaceElevated, Radius.poster)
                    .border(1.dp, colours.border, Radius.poster),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = Spacing.xxs, bottom = Spacing.xxs)
                    .offset(x = Spacing.xxs, y = (-3).dp)
                    .background(colours.surface, Radius.poster)
                    .border(1.dp, colours.border, Radius.poster),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(end = Spacing.xs, top = Spacing.xxs)
                    .clip(Radius.poster),
            ) {
                Artwork(
                    url = artworkUrl,
                    contentDescription = collection.title,
                    fallbackTitle = collection.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        PlexText(text = collection.title, style = PlexTheme.type.label, maxLines = 2)
        PlexText(
            text = "${collection.childCount} titles",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
        )
    }
}

/**
 * The wide progress tile used by the Continue Watching row: 16:9 artwork, a scrim, the
 * title over it and the resume position beneath. See CLAUDE.md section 14.
 */
@Composable
fun WideProgressTile(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.card, onClick = onClick)
            .padding(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Layout.WIDE_ASPECT_RATIO)
                .shadow(
                    elevation = 12.dp,
                    shape = Radius.card,
                    ambientColor = colours.elevationShadow,
                    spotColor = colours.elevationShadow,
                )
                .clip(Radius.card),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = item.title,
                fallbackTitle = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            // A stronger, multi-stop bottom scrim so the title and its metadata sit on a
            // legible bed of shadow over any artwork, the way a streaming rail treats its
            // featured tiles. Always dark tones here, since the text over it is always light.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            0.72f to Color(0x99000000),
                            1f to Color(0xE6000000),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.sm, top = Spacing.sm)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PlexText(
                    text = primaryLine(item),
                    style = PlexTheme.type.label,
                    colour = Color(0xFFF2F3F5),
                    maxLines = 1,
                )
                secondaryLine(item)?.let {
                    PlexText(
                        text = it,
                        style = PlexTheme.type.caption,
                        colour = Color(0xFFC8CDD6),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(Spacing.xxs))
                ProgressBar(
                    progress = item.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        PlexText(
            text = remainingLabel(item),
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
        )
    }
}

/** A library card on the Home screen. */
@Composable
fun LibraryCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Box(
        modifier = modifier
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.card)
            .background(colours.surface)
            .border(1.dp, colours.border, Radius.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(84.dp)
                    .clip(Radius.poster),
            ) {
                Artwork(
                    url = artworkUrl,
                    contentDescription = null,
                    fallbackTitle = title,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(Spacing.md))

            Column {
                PlexText(text = title, style = PlexTheme.type.title, maxLines = 1)
                PlexText(
                    text = subtitle,
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** An episode row: thumbnail, number, title, duration, and progress when part watched. */
@Composable
fun EpisodeRow(
    episode: Episode,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Row(
        modifier = modifier
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .fillMaxWidth()
            .clip(Radius.card)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(142.dp)
                .aspectRatio(Layout.WIDE_ASPECT_RATIO)
                .shadow(
                    elevation = 6.dp,
                    shape = Radius.poster,
                    ambientColor = colours.elevationShadow,
                    spotColor = colours.elevationShadow,
                )
                .clip(Radius.poster),
        ) {
            Artwork(
                url = thumbnailUrl,
                contentDescription = episode.title,
                fallbackTitle = episode.title,
                modifier = Modifier.fillMaxSize(),
            )

            PosterFooterScrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())

            // A quiet play token, so a thumbnail reads as a launch point rather than a still.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .background(Color(0x66000000), Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                PlexIcon(PlexIconKind.PLAY, size = 18.dp, tint = Color.White)
            }

            if (episode.progress > 0f && !episode.watched) {
                ProgressBar(
                    progress = episode.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.xxs),
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlexText(
                    text = "${episode.episodeIndex}.",
                    style = PlexTheme.type.label,
                    colour = colours.textSecondary,
                )
                Spacer(Modifier.width(Spacing.xs))
                PlexText(text = episode.title, style = PlexTheme.type.label, maxLines = 1)
            }

            PlexText(
                text = formatDuration(episode.durationMs),
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
            )

            if (episode.summary.isNotBlank()) {
                PlexText(
                    text = episode.summary,
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 2,
                )
            }
        }

        if (episode.watched) {
            Spacer(Modifier.width(Spacing.xs))
            WatchedBadge()
        }
    }
}

/** A section header. One accent rule, the title, and nothing else. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(PlexTheme.colours.accent, Radius.pill),
            )
            Spacer(Modifier.width(Spacing.xs))
            PlexText(text = title, style = PlexTheme.type.title)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    // A rounded track with a rounded accent fill. Slightly taller than a hairline so it
    // registers as a deliberate element, clipped to a pill so the ends read as caps.
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(Radius.pill)
            .background(Color(0x59FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(Radius.pill)
                .background(colours.accent),
        )
    }
}

/**
 * The faint dark wash along the bottom of a piece of artwork. Just enough to hold a badge,
 * a progress bar, or a number against a bright still without touching the art above it.
 */
@Composable
internal fun PosterFooterScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight(0.4f)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0x99000000),
                ),
            ),
    )
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .background(colours.accent, Radius.pill)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    ) {
        PlexText(
            text = "Watched",
            style = PlexTheme.type.caption,
            colour = if (colours.isDark) colours.background else Color.White,
        )
    }
}

// --- labels ------------------------------------------------------------------------------

internal fun primaryLine(item: MediaItem): String = when (item) {
    is Episode -> item.showTitle.ifBlank { item.title }
    else -> item.title
}

internal fun secondaryLine(item: MediaItem): String? = when (item) {
    is Episode -> "S${pad(item.seasonIndex)}E${pad(item.episodeIndex)}  ${item.title}"
    is Movie -> item.year?.toString()
    is Show -> if (item.leafCount > 0) "${item.leafCount} episodes" else null
    is MediaCollection -> "${item.childCount} titles"
    else -> null
}

internal fun remainingLabel(item: MediaItem): String {
    val remaining = (item.durationMs - item.viewOffsetMs).coerceAtLeast(0)
    return if (item.viewOffsetMs <= 0L) formatDuration(item.durationMs)
    else "${formatDuration(remaining)} left"
}

internal fun pad(value: Int): String = value.toString().padStart(2, '0')

/** Durations read as "1 h 47 m" or "42 m", never as a raw millisecond count. */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/** A clock position for the player: 1:47:12 or 42:07. */
internal fun formatPosition(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${pad(minutes.toInt())}:${pad(seconds.toInt())}"
    else "$minutes:${pad(seconds.toInt())}"
}
