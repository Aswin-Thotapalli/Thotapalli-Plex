package com.thotapalli.plex.ui.shared.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.thotapalli.plex.ui.design.PlexTheme

/**
 * A full-screen ambient wash drawn from the content's own artwork.
 *
 * The picture is rendered once, heavily blurred and dimmed, then a colour glow pulled from the
 * same artwork by [rememberDominantColor] is laid over the top so the whole surface takes on the
 * poster's mood. Everything settles into [baseColor] toward the bottom, which keeps foreground
 * text — a detail summary, the player overlay — legible against a known dark ground rather than
 * whatever happened to be in the corner of the image. This is the quiet, colour-carrying backdrop
 * described in CLAUDE.md section 12: the art supplies the colour, the interface stays calm.
 *
 * Meant to sit behind scrollable content: it fills its box, never scrolls, and never draws a hard
 * edge. With a null or blank [url] it is simply a clean [baseColor] field.
 *
 * Blur is available on both real targets here — Android (minSdk 31) and Compose Desktop — so the
 * blurred image always renders. The colour glow and gradients are layered on regardless, so even
 * if a platform ever dropped the blur the result would still darken and tint sensibly rather than
 * show a sharp photo.
 */
@Composable
fun AmbientBackground(
    url: String?,
    modifier: Modifier = Modifier,
    baseColor: Color = PlexTheme.colours.background,
) {
    val ambient by rememberDominantColor(url, fallback = baseColor)

    Box(modifier = modifier.fillMaxSize().background(baseColor)) {
        if (!url.isNullOrBlank()) {
            // The artwork itself, blurred to a colour field and dimmed so nothing in it reads as
            // detail. It only exists to give the wash organic variation.
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(56.dp)
                    .alpha(0.40f),
            )
        }

        // A soft glow of the extracted colour, brightest toward the top where headers and posters
        // sit, fading out before it reaches the content below.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(ambient.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(GLOW_CENTER_X, GLOW_CENTER_Y),
                    radius = GLOW_RADIUS,
                ),
            ),
        )

        // Anchor the picture to the base colour: transparent at the top, solid [baseColor] at the
        // bottom, so anything laid over the lower half stays readable.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.55f to baseColor.copy(alpha = 0.35f),
                    1.0f to baseColor,
                ),
            ),
        )

        // A gentle overall scrim. Keeps the wash from ever competing with foreground text and
        // guarantees a legible ground even when the artwork or glow is bright.
        Box(modifier = Modifier.fillMaxSize().background(baseColor.copy(alpha = 0.30f)))
    }
}

// Glow placed high and slightly left of centre, sized generously so it reads as an ambient
// bloom rather than a spotlight. Values are in pixels within the drawn area, which is acceptable
// for a decorative gradient centre; every layout dimension elsewhere stays in dp.
private const val GLOW_CENTER_X = 320f
private const val GLOW_CENTER_Y = 240f
private const val GLOW_RADIUS = 900f
