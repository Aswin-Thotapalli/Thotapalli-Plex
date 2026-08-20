package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.ui.shared.motion.gyroParallax
import com.thotapalli.plex.ui.shared.motion.kenBurns
import com.thotapalli.plex.ui.shared.material.cinematicTexture
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing

/**
 * The featured hero at the top of Home: the single item most worth resuming, shown large.
 *
 * A big backdrop under a firm gradient, the title in display type, a metadata line, the
 * resume progress, and the two actions — a filled Play (or Resume) and an outlined Details.
 * The card itself is not a click target; its two buttons are the focusable elements, which
 * keeps television navigation unambiguous. Nothing here is discovery: it is only ever the
 * top Continue Watching entry the caller hands it. See CLAUDE.md section 14.
 */
@Composable
fun HomeHero(
    item: MediaItem,
    artworkUrl: String?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
) {
    val colours = PlexTheme.colours
    val resuming = item.viewOffsetMs > 0L

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight(PlexTheme.sizeClass, viewportHeight))
            .shadow(
                elevation = 16.dp,
                shape = Radius.card,
                ambientColor = colours.elevationShadow,
                spotColor = colours.elevationShadow,
            )
            .clip(Radius.card),
    ) {
        Artwork(
            url = artworkUrl,
            contentDescription = primaryLine(item),
            fallbackTitle = primaryLine(item),
            modifier = Modifier.fillMaxSize(),
            // Bias the crop toward the top so a wide 16:9 backdrop keeps the subjects' faces
            // in frame rather than cropping their heads off at the top edge.
            alignment = Alignment.TopCenter,
        )

        // A cinematic bed: dark from the left and up from the bottom, so display-size white
        // text and two buttons sit on a legible ground over any backdrop.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.20f to Color.Transparent,
                        0.62f to Color(0x8C000000),
                        1.0f to Color(0xF2000000),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PlexText(
                text = primaryLine(item),
                style = PlexTheme.type.display,
                colour = Color(0xFFF6F7F9),
                maxLines = 2,
            )

            heroMetadata(item)?.let {
                PlexText(
                    text = it,
                    style = PlexTheme.type.label,
                    colour = Color(0xFFC8CDD6),
                    maxLines = 1,
                )
            }

            if (item.progress > 0f) {
                Spacer(Modifier.height(Spacing.xxs))
                ProgressBar(
                    progress = item.progress,
                    modifier = Modifier.fillMaxWidth(heroProgressWidthFraction(PlexTheme.sizeClass)),
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton(
                    label = if (resuming) "Resume" else "Play",
                    leadingIcon = PlexIconKind.PLAY,
                    onClick = onPlay,
                )
                SecondaryButton(
                    label = "Details",
                    leadingIcon = PlexIconKind.INFO,
                    onClick = onDetails,
                )
            }
        }
    }
}

/** Year, runtime and, for an episode, its place in the show, joined by a middle dot. */
private fun heroMetadata(item: MediaItem): String? {
    val parts = buildList {
        secondaryLine(item)?.let { add(it) }
        if (item.viewOffsetMs > 0L) add(remainingLabel(item))
        else if (item.durationMs > 0L) add(formatDuration(item.durationMs))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
}

/**
 * The hero's designed height per size class, capped so it never eats the whole viewport.
 *
 * On a short window — a normal-height desktop window is only a few hundred dp tall — the fixed
 * designed height can exceed the visible area and push the action buttons off the bottom edge,
 * where the taskbar occludes them. When the viewport height is known, the hero is held to a
 * fraction of it so the buttons always sit clear of the bottom and the rails below the hero peek
 * into view without scrolling. On tall windows the cap never bites and the designed height stands.
 */
private fun heroHeight(sizeClass: SizeClass, viewportHeight: Dp?): Dp {
    val designed = when (sizeClass) {
        SizeClass.COMPACT -> 380.dp
        SizeClass.MEDIUM -> 420.dp
        SizeClass.EXPANDED -> 460.dp
        SizeClass.TELEVISION -> 560.dp
    }
    if (viewportHeight == null || viewportHeight <= 0.dp) return designed
    return minOf(designed, viewportHeight * HERO_MAX_VIEWPORT_FRACTION)
}

/** The hero may occupy at most this share of the viewport, leaving the rails below it in view. */
private const val HERO_MAX_VIEWPORT_FRACTION = 0.6f

/** On wide screens the resume bar need not run the whole width to read. */
private fun heroProgressWidthFraction(sizeClass: SizeClass): Float =
    if (sizeClass == SizeClass.EXPANDED || sizeClass == SizeClass.TELEVISION) 0.5f else 1f
