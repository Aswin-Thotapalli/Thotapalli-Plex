package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.shared.resources.Res
import com.thotapalli.plex.ui.shared.resources.brand_mark
import org.jetbrains.compose.resources.painterResource

/**
 * The launch animation, in the spirit of a streaming service's intro: on a cold start the brand
 * mark rises out of the dark ground under a warm amber bloom, springs to size with a hair of
 * overshoot, holds for a beat, then lifts and fades to reveal the app loading underneath.
 *
 * It runs once per process launch (the host stops composing it after [onFinished]). The mark is
 * the `brand/Files` artwork bundled as a Compose resource, so the splash is pixel-identical to the
 * app icon.
 */
@Composable
fun BrandSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val fade = remember { Animatable(1f) }
    var reveal by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        // Measure elapsed time from the FIRST rendered frame, not from composition. On a cold
        // start the window can take a second or two to paint; a duration-based animation would
        // burn through that invisible warmup and be over before anything is on screen.
        val total = 920f
        var start = -1L
        while (true) {
            val now = withFrameMillis { it }
            if (start < 0L) start = now
            val elapsed = (now - start).toFloat()
            reveal = (elapsed / total).coerceIn(0f, 1f)
            if (elapsed >= total) break
        }
        reveal = 1f
        fade.animateTo(0f, tween(durationMillis = 320, delayMillis = 240))
        onFinished()
    }

    val markAlpha = easeOutCubic(sub(reveal, 0f, 0.5f))
    val markScale = 0.86f + 0.14f * backOut(sub(reveal, 0f, 0.72f))
    val glow = easeOutCubic(sub(reveal, 0.1f, 0.7f))
    val exitLift = 1f + (1f - fade.value) * 0.05f

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(fade.value)
            // Their mark is designed on a dark ground; the app background token is that dark.
            .background(PlexTheme.colours.background),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints {
            val side = minOf(maxWidth, maxHeight)
            val markSize = (side * 0.5f).coerceIn(160.dp, 360.dp)
            Box(contentAlignment = Alignment.Center) {
                // Warm bloom behind the mark.
                Box(
                    Modifier
                        .size(markSize * 1.6f)
                        .alpha(glow * 0.55f)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFF2A52A), Color.Transparent),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.minDimension / 2f,
                                ),
                            )
                        },
                )
                Image(
                    painter = painterResource(Res.drawable.brand_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(markSize)
                        .graphicsLayer {
                            val sc = markScale * exitLift
                            scaleX = sc
                            scaleY = sc
                            alpha = markAlpha
                        },
                )
            }
        }
    }
}

/** The fraction of [value] within [start]..[end], clamped 0..1. */
private fun sub(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u
}

/** An ease that overshoots past 1 and settles back — the springy pop for the mark. */
private fun backOut(t: Float): Float {
    val s = 1.70158f
    val u = t - 1f
    return 1f + (s + 1f) * u * u * u + s * u * u
}
