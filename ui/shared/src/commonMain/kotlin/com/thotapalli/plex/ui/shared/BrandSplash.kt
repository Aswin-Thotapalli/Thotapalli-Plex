package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.thotapalli.plex.ui.shared.resources.Res
import com.thotapalli.plex.ui.shared.resources.brand_mark
import kotlin.math.exp
import org.jetbrains.compose.resources.painterResource

/**
 * The launch animation, from CLAUDE.md section 15 — the mark powers on from its own play button.
 *
 * A live Compose animation of the one brand mark, not a baked frame sequence, so it stays crisp at
 * any resolution on phone, tablet, television and Windows alike and adds no image weight to the
 * build. On the near-black ground a warm spark flares at the play triangle, then the mark
 * materialises outward from that point — a soft radial reveal, as if the press of play brought it
 * to life — settling into place over a low amber bloom. It holds on the finished mark until the app
 * is READY, then lifts a touch and fades to reveal the app underneath.
 *
 * The reveal is a [BlendMode.DstIn] mask: the mark is drawn into an offscreen layer, then a radial
 * gradient whose radius grows keeps only the pixels inside the expanding circle. The hold runs off
 * the real frame clock so the whole intro is seen even after a slow cold start, capped so it never
 * drags; any failure hands straight over to the app. Runs once per process launch.
 */
@Composable
fun BrandSplash(ready: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    // [enter] is the entrance clock (spark → reveal → settle); [exit] fades and lifts on reveal.
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val readyState = rememberUpdatedState(ready)

    LaunchedEffect(Unit) {
        try {
            enter.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))

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

    val p = enter.value
    val e = exit.value

    // Reveal grows from the play button and eases to full; the mark settles from a touch large;
    // the flare spikes early and recedes; everything fades and lifts on exit.
    val reveal = smoothstep(0.06f, 0.62f, p)
    val settle = smoothstep(0.5f, 0.9f, p)
    val flare = exp(-(((p - 0.16f) / 0.09f) * ((p - 0.16f) / 0.09f)))
    val bloom = reveal * (1f - e)
    val alpha = (1f - e)
    val markScale = (1.05f - 0.05f * settle) * (1f + LIFT * e)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SPLASH_GROUND)
            .drawBehind {
                // The low amber bloom the mark seats into, grown with the reveal.
                if (bloom > 0.001f) {
                    val r = size.minDimension * (0.26f + 0.16f * reveal)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(BLOOM.copy(alpha = 0.34f * bloom), BLOOM.copy(alpha = 0f)),
                            center = Offset(size.width / 2f, size.height * PLAY_Y),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(size.width / 2f, size.height * PLAY_Y),
                    )
                }
                // The ignition spark at the play button: a tight warm-white core that peaks early.
                if (flare * (1f - e) > 0.01f) {
                    val r = size.minDimension * (0.05f + 0.14f * p)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(SPARK.copy(alpha = (0.9f * flare * (1f - e)).coerceIn(0f, 1f)), SPARK.copy(alpha = 0f)),
                            center = Offset(size.width / 2f, size.height * PLAY_Y),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(size.width / 2f, size.height * PLAY_Y),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
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
                    // Required so the DstIn mask below composites against this layer alone.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Keep only the pixels inside the circle expanding from the play button.
                    val maxR = size.minDimension * 1.35f
                    val r = (reveal * maxR).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.White,
                                0.82f to Color.White,
                                1f to Color.Transparent,
                            ),
                            center = Offset(size.width * PLAY_X, size.height * PLAY_Y_MARK),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(size.width * PLAY_X, size.height * PLAY_Y_MARK),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

/** The play triangle's position within the mark image (measured from the artwork). */
private const val PLAY_X = 0.50f
private const val PLAY_Y_MARK = 0.47f

/** The play triangle's vertical position within the full screen (the mark box is centred). */
private const val PLAY_Y = 0.485f

/** How far the mark lifts (grows) as it fades out on reveal. */
private const val LIFT = 0.10f

/** The mark's size as a fraction of the smaller screen dimension. */
private const val MARK_FRACTION = 0.46f

/** The longest the finished mark waits on the connection before revealing anyway. */
private const val MAX_HOLD_MS = 3000L

private val SPLASH_GROUND = Color(0xFF060608)
private val BLOOM = Color(0xFFF2A52A)
private val SPARK = Color(0xFFFFEBC3)

/** The classic smoothstep, so the reveal and settle ease rather than move linearly. */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
