package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thotapalli.plex.ui.shared.motion.gyroParallax
import com.thotapalli.plex.ui.shared.motion.kenBurns
import com.thotapalli.plex.ui.shared.material.cinematicTexture
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing

/**
 * The detail-screen hero: a full-bleed backdrop under a strong vertical gradient that fades
 * into the screen background, with a lighter top scrim so a floating back button stays
 * legible. Anything the caller passes as [content] is overlaid along the bottom, sitting on
 * the darkest part of the fade.
 *
 * The fade ends on the theme background rather than on black, so the image dissolves into the
 * page beneath it on both dark and light — the seamless "poster bleeding into the page" look
 * a streaming detail screen has. Height is either a fixed [height] or, inside a bounded
 * parent, a [heightFraction] of it.
 */
@Composable
fun CinematicBackdrop(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = defaultBackdropHeight(PlexTheme.sizeClass),
    heightFraction: Float? = null,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colours = PlexTheme.colours

    val sized = if (heightFraction != null) {
        modifier.fillMaxWidth().fillMaxHeight(heightFraction)
    } else {
        modifier.fillMaxWidth().height(height)
    }

    Box(sized) {
        // A slow, endless pan and zoom, so a still backdrop breathes like a title sequence.
        Artwork(
            url = url,
            contentDescription = title,
            fallbackTitle = title,
            modifier = Modifier.fillMaxSize().kenBurns().gyroParallax(),
        )

        // A faint film grain and vignette, so the image reads as cinema rather than a photo.
        Box(Modifier.fillMaxSize().cinematicTexture())

        // Bottom fade into the page. Transparent across the top half, then a firm ramp onto
        // the background token so the image and the screen become one surface.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to colours.background.copy(alpha = 0.0f),
                        0.72f to colours.background.copy(alpha = 0.65f),
                        0.90f to colours.background.copy(alpha = 0.94f),
                        1.0f to colours.background,
                    ),
                ),
        )

        // A short top scrim, always dark, so a back button reads over a bright backdrop
        // regardless of theme.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x73000000),
                        1f to Color.Transparent,
                    ),
                ),
        )

        if (content != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.lg,
                        top = Spacing.xl,
                    ),
                content = content,
            )
        }
    }
}

private fun defaultBackdropHeight(sizeClass: SizeClass): Dp = when (sizeClass) {
    SizeClass.COMPACT -> 340.dp
    SizeClass.MEDIUM -> 400.dp
    SizeClass.EXPANDED -> 460.dp
    SizeClass.TELEVISION -> 520.dp
}
