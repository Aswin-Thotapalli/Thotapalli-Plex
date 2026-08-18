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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                .clip(Radius.poster),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = item.title,
                fallbackTitle = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            if (item.watched) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
            }

            if (item.progress > 0f && !item.watched) {
                ProgressBar(
                    progress = item.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
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
                .clip(Radius.card),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = item.title,
                fallbackTitle = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            // A bottom scrim so white title text stays legible over bright artwork.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to colours.scrim,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.sm)
                    .fillMaxWidth(),
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
                        colour = Color(0xFFA8AEB8),
                        maxLines = 1,
                    )
                }
            }

            ProgressBar(
                progress = item.progress,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
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
                .clip(Radius.poster),
        ) {
            Artwork(
                url = thumbnailUrl,
                contentDescription = episode.title,
                fallbackTitle = episode.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (episode.progress > 0f && !episode.watched) {
                ProgressBar(
                    progress = episode.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
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
    Box(modifier = modifier.height(3.dp).background(colours.border)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(colours.accent),
        )
    }
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
