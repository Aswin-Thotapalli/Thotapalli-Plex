package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.Motion
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing

/**
 * A loading shimmer: a resting skeleton wash with a highlight band sweeping across it on a
 * loop. Applied as a modifier so any shape — a poster, a card, a text line — can wear it.
 * The clip is part of the modifier, so callers pass the shape rather than clipping twice.
 */
fun Modifier.shimmer(shape: RoundedCornerShape = Radius.card): Modifier = composed {
    val colours = PlexTheme.colours
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.SHIMMER_MS, easing = LinearEasing),
        ),
        label = "shimmer-sweep",
    )

    this
        .clip(shape)
        .drawWithCache {
            val w = size.width
            val band = w * 1.4f
            val start = -band + progress * (w + band)
            val brush = Brush.linearGradient(
                colors = listOf(colours.skeletonBase, colours.skeletonSheen, colours.skeletonBase),
                start = Offset(start, 0f),
                end = Offset(start + band, 0f),
            )
            onDrawBehind { drawRect(brush) }
        }
}

/** A single shimmering block, for a bespoke placeholder layout. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: RoundedCornerShape = Radius.card) {
    Box(modifier.shimmer(shape))
}

/**
 * A grid of poster-shaped placeholders, matched to the real library grid's adaptive columns
 * so the switch from loading to loaded does not reflow. See CLAUDE.md section 13.
 */
@Composable
fun SkeletonPosterGrid(
    modifier: Modifier = Modifier,
    count: Int = 12,
) {
    val sizeClass = PlexTheme.sizeClass
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = sizeClass.posterMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(sizeClass.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Layout.gridHorizontalGap),
        verticalArrangement = Arrangement.spacedBy(Layout.gridVerticalGap),
        userScrollEnabled = false,
    ) {
        items(count) {
            Column {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(Layout.POSTER_ASPECT_RATIO),
                    shape = Radius.poster,
                )
                Box(Modifier.height(Spacing.xs))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                    shape = Radius.pill,
                )
            }
        }
    }
}

/**
 * The branded busy state: an accent spinner over an optional label, centred. Replaces bare
 * "Loading" text so a wait reads as the app working rather than as a stall.
 */
@Composable
fun LoadingIndicator(
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(
            color = colours.accent,
            trackColor = colours.border,
        )
        if (label != null) {
            PlexText(
                text = label,
                style = PlexTheme.type.label,
                colour = colours.textSecondary,
            )
        }
    }
}
