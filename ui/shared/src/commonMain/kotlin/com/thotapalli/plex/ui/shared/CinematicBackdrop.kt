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
 * The detail-screen hero: a full-bleed backdrop that bleeds to every edge and dissolves into the
 * deep-indigo ground beneath the page. The art is the ground for a detail screen, so nothing is
 * inset above it — the shell floats a glass back button over the top-left — and everything the
 * caller passes as [content] (the title and its key facts) rides the darkest foot of the fade.
 *
 * The image sits under a stack of thin, deliberate washes rather than one flat scrim:
 *
 * 1. a slow ken-burns pan and gyro parallax, so a still backdrop breathes like a title sequence;
 * 2. a faint film grain and vignette, so it reads as cinema rather than a photograph;
 * 3. a **cinematic vertical fade** — clear across the top, then a firm multi-stop ramp onto the
 *    theme background token, so the picture and the page become one continuous surface on both
 *    dark and light;
 * 4. **side and corner vignettes** that draw the eye inward and seat the title against the frame;
 * 5. a short **top scrim**, always dark, so the floating back button stays legible over a bright
 *    still regardless of theme.
 *
 * Height is either a fixed [height] or, inside a bounded parent, a [heightFraction] of it.
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
        // 1. The artwork, panning and zooming so a static still never feels static.
        Artwork(
            url = url,
            contentDescription = title,
            fallbackTitle = title,
            modifier = Modifier.fillMaxSize().kenBurns(enabled = !isDesktopPlatform()).gyroParallax(),
        )

        // 2. Grain and vignette, for the cinema texture.
        Box(Modifier.fillMaxSize().cinematicTexture(animated = !isDesktopPlatform()))

        // 3. The vertical fade into the page. Clear across the top so the art reads at full
        // strength, then a firm ramp onto the background token so the image melts into the screen.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.42f to colours.background.copy(alpha = 0.0f),
                        0.66f to colours.background.copy(alpha = 0.45f),
                        0.82f to colours.background.copy(alpha = 0.80f),
                        0.94f to colours.background.copy(alpha = 0.97f),
                        1.00f to colours.background,
                    ),
                ),
        )

        // 4. A soft horizontal vignette, darkest at the two edges, that frames the picture and
        // keeps the title from floating loose against a bright corner of the still.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to colours.background.copy(alpha = 0.34f),
                        0.24f to Color.Transparent,
                        0.76f to Color.Transparent,
                        1.00f to colours.background.copy(alpha = 0.34f),
                    ),
                ),
        )

        // 5. A short top scrim, always dark, so a back button reads over a bright backdrop
        // regardless of theme.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.30f)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x80000000),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // 6. A dark scrim behind the caption, ALWAYS dark (independent of theme), so the title and
        // its facts stay legible over any bright still — a tall, wrapped title can otherwise ride up
        // into the picture where the theme-background fade hasn't reached. The caption text is drawn
        // light to match. This is why a hero title reads on every backdrop the way it does in Plex.
        if (content != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.55f to Color(0x66000000),
                            1.00f to Color(0xE6000000),
                        ),
                    ),
            )
        }

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
