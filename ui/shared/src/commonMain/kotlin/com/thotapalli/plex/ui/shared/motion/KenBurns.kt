package com.thotapalli.plex.ui.shared.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.thotapalli.plex.ui.design.Motion

/**
 * A slow, endless pan-and-zoom for hero and backdrop imagery — the "Ken Burns" effect.
 *
 * The image drifts diagonally while it eases between its natural size and [maxScale], reversing at
 * each end so it never jumps. The pan is derived from the live layer size and kept strictly inside
 * the overflow the zoom creates, so the image can never reveal an empty edge no matter its aspect.
 * Everything runs on one graphics layer, so a full-bleed backdrop animates without redrawing or
 * relaying out the content layered above it.
 *
 * @param enabled when false this is a no-op, returning the receiver untouched so a still backdrop
 *   pays no animation cost at all.
 * @param durationMs length of one zoom sweep before it reverses. Long by default; this should be
 *   felt only at the edge of attention.
 * @param maxScale how far the image zooms at the far end of the sweep. Also bounds the pan.
 */
fun Modifier.kenBurns(
    enabled: Boolean = true,
    durationMs: Int = 20_000,
    maxScale: Float = 1.08f,
): Modifier {
    if (!enabled) return this
    return composed {
        val transition = rememberInfiniteTransition(label = "ken-burns")

        val zoom by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = Motion.standard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ken-burns-zoom",
        )

        // A slower, offset drift on a different period so the pan never tracks the zoom exactly,
        // which is what keeps the motion from reading as mechanical.
        val drift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((durationMs * 1.4f).toInt(), easing = Motion.standard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ken-burns-drift",
        )

        graphicsLayer {
            val scale = lerp(1f, maxScale, zoom)
            scaleX = scale
            scaleY = scale

            // The zoom overflows the layer by this much on each edge. Staying within it, with a
            // little margin, guarantees no edge is ever exposed.
            val marginFactor = 0.85f
            val maxShiftX = (scale - 1f) * size.width / 2f * marginFactor
            val maxShiftY = (scale - 1f) * size.height / 2f * marginFactor

            translationX = (drift - 0.5f) * 2f * maxShiftX
            translationY = (zoom - 0.5f) * 2f * maxShiftY
        }
    }
}
