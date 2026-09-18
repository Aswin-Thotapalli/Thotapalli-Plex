package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.thotapalli.plex.ui.shared.resources.Res
import com.thotapalli.plex.ui.shared.resources.brand_mark
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The launch animation, from CLAUDE.md section 15.
 *
 * A live Compose animation of the one brand mark — not a baked frame sequence — so it stays crisp
 * at any resolution on phone, tablet, television and Windows alike, and adds no image weight to the
 * build. A warm amber bloom grows on the near-black ground while the mark springs up from small
 * with a hair of overshoot and fades in; it holds on the finished mark until the app is READY, then
 * lifts a touch and fades to reveal the app underneath.
 *
 * The hold is driven off the real frame clock so the whole intro is seen even after a slow cold
 * start rather than being burned through during the invisible JVM warmup, and it is capped so it
 * never drags. Runs once per process launch — the host stops composing it after [onFinished] — and
 * any failure simply hands over to the app.
 */
@Composable
fun BrandSplash(ready: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    // Entrance: [appear] drives the fade and the bloom; [scale] is the spring the mark rides up on.
    // Exit: [exit] fades the whole thing out and lifts the mark a hair as it goes.
    val appear = remember { Animatable(0f) }
    val scale = remember { Animatable(ENTER_SCALE) }
    val exit = remember { Animatable(0f) }
    val readyState = rememberUpdatedState(ready)

    LaunchedEffect(Unit) {
        try {
            // The fade/bloom and the spring run together, so the mark is already lifting as it appears.
            launch { appear.animateTo(1f, tween(durationMillis = 300, easing = LinearOutSlowInEasing)) }
            scale.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow))

            // Hold on the finished mark until the app is ready, revealing the instant it is so the
            // launch feels immediate — capped so a slow connection never leaves it sitting there.
            var holdStart = -1L
            while (!readyState.value) {
                val now = withFrameMillis { it }
                if (holdStart < 0L) holdStart = now
                if (now - holdStart >= MAX_HOLD_MS) break
            }

            exit.animateTo(1f, tween(durationMillis = 360, easing = FastOutLinearInEasing))
        } catch (_: Throwable) {
            // Fall through to reveal the app regardless.
        } finally {
            onFinished()
        }
    }

    val alpha = appear.value * (1f - exit.value)
    val markScale = scale.value * (1f + LIFT * exit.value)

    Box(
        modifier = modifier.fillMaxSize().background(SPLASH_GROUND),
        contentAlignment = Alignment.Center,
    ) {
        // The warm bloom, grown from the entrance and faded with everything else. Drawn behind the
        // mark so its light spills past the glyph's edges.
        Canvas(Modifier.fillMaxSize()) {
            if (alpha <= 0f) return@Canvas
            val radius = size.minDimension * (0.30f + 0.16f * appear.value)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BLOOM.copy(alpha = 0.42f * alpha),
                        BLOOM.copy(alpha = 0f),
                    ),
                    center = Offset(size.width / 2f, size.height * 0.47f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width / 2f, size.height * 0.47f),
            )
        }

        Image(
            painter = painterResource(Res.drawable.brand_mark),
            contentDescription = "Thotapalli Plex",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(MARK_FRACTION)
                .graphicsLayer {
                    scaleX = markScale
                    scaleY = markScale
                    this.alpha = alpha
                },
        )
    }
}

/** The mark starts a little small and springs to full size. */
private const val ENTER_SCALE = 0.72f

/** How far the mark lifts (grows) as it fades out on reveal. */
private const val LIFT = 0.10f

/** The mark's size as a fraction of the smaller screen dimension. */
private const val MARK_FRACTION = 0.46f

/** The longest the finished mark waits on the connection before revealing anyway. */
private const val MAX_HOLD_MS = 3000L

private val SPLASH_GROUND = Color(0xFF060608)
private val BLOOM = Color(0xFFF2A52A)
