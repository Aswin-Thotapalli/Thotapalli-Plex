package com.thotapalli.plex.ui.shared.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared spring specifications for the choreographed motion toolkit.
 *
 * The [Motion][com.thotapalli.plex.ui.design.Motion] object in ui/design owns the tween-based
 * timings from CLAUDE.md section 12. Those are the right tool for a fade or a hard-timed
 * transition. Springs are the right tool for anything a finger or a remote pushes on, where the
 * settle should feel like mass rather than a stopwatch. This object holds the three springs the
 * app reuses so tactile motion is consistent everywhere.
 */
object Springs {
    /** How far a pressed, tappable surface shrinks. A little goes a long way. */
    const val PRESSED_SCALE = 0.96f

    /**
     * A quick, slightly springy settle for a tap. Enough bounce to feel physical, damped enough
     * not to wobble. Used by [pressScale] and [rememberPressScale].
     */
    val press: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * A snappy, near-critically damped settle for television focus and pointer selection. Arrives
     * fast and holds, so moving focus across a grid never feels loose.
     */
    val focus: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * A soft, unhurried settle for entrances and content that rises into place. No overshoot, so a
     * cascade of tiles reads as calm rather than bouncy.
     */
    val gentle: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessLow,
    )
}

/**
 * Shrinks a surface slightly while [pressed] is true, settling on the [Springs.press] spring.
 *
 * Drive it from an interaction source the caller already owns, for a button or tile that wants a
 * tactile push without adopting the whole interaction wiring. Scales through a graphics layer so
 * the press never triggers a relayout of anything around it.
 */
fun Modifier.pressScale(pressed: Boolean): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (pressed) Springs.PRESSED_SCALE else 1f,
        animationSpec = Springs.press,
        label = "press-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The reusable press-scale value, so a caller can apply the shrink itself (for instance combining
 * it with another transform in one graphics layer) rather than through [pressScale].
 *
 * Returns 1f at rest and eases toward [Springs.PRESSED_SCALE] while the source reports a press.
 */
@Composable
fun rememberPressScale(interactionSource: InteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Springs.PRESSED_SCALE else 1f,
        animationSpec = Springs.press,
        label = "remember-press-scale",
    )
    return scale
}
