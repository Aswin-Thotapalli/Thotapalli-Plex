package com.thotapalli.plex.ui.shared.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.thotapalli.plex.ui.design.Motion
import kotlinx.coroutines.delay

/**
 * The vertical distance a tile travels as it rises into place. Small on purpose: the eye should
 * read arrival, not a slide.
 */
private val ENTRANCE_RISE = 14.dp

/** Where the subtle scale begins. Close enough to 1 that it reads as depth, not a zoom. */
private const val ENTRANCE_START_SCALE = 0.96f

/**
 * A little longer than the standard 150 ms enter so the rise and fade have room to be felt as one
 * gesture, still inside the standard-easing family.
 */
private const val ENTRANCE_DURATION_MS = Motion.ENTER_MS + 130

/**
 * The most an item ever waits before its entrance begins. The cascade reads on the first rows;
 * beyond it every later item starts together, so scrolling fast to the bottom of a long library
 * shows a quick fade instead of a second of blank while a per-index delay counts up.
 */
private const val MAX_STAGGER_MS = 220L

/**
 * Fades a tile in while it rises a few dp and settles from a barely-there [ENTRANCE_START_SCALE],
 * staggered by [index] so a grid or a row cascades in rather than snapping as a block.
 *
 * Each item waits `index * baseDelayMs` before it begins, so the first tile leads and the rest
 * follow in reading order. The whole effect runs on a single graphics layer, so it never lays out
 * or draws anything extra per frame.
 *
 * @param index position of this item in its grid or row; drives the stagger delay.
 * @param visible when false the item stays hidden; flipping it back to true replays the entrance.
 * @param baseDelayMs delay added per [index]. Keep it small; the cascade adds up fast across a row.
 * @param key re-runs the entrance when it changes. Defaults to [index], so reusing a slot for a new
 *   item replays the animation. Pass a stable item identity to keep a tile settled across scrolls.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    visible: Boolean = true,
    baseDelayMs: Int = 40,
    key: Any? = index,
): Modifier = composed {
    val risePx = with(LocalDensity.current) { ENTRANCE_RISE.toPx() }

    // Held outside the animation so a key change restarts from hidden without a visible flash.
    var appeared by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, visible) {
        if (visible) {
            appeared = false
            // The delay cascades with position but is capped, so an item scrolled far down the
            // grid (index 300 -> 12s at the raw rate) never waits behind a blank screen: past the
            // cap every item begins together, a quick fade rather than a long hold. See the
            // library fast-scroll case in CLAUDE.md section 12.
            val stagger = (index.toLong() * baseDelayMs).coerceIn(0L, MAX_STAGGER_MS)
            if (stagger > 0L) delay(stagger)
            appeared = true
        } else {
            appeared = false
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = ENTRANCE_DURATION_MS, easing = Motion.standard),
        label = "staggered-entrance",
    )

    graphicsLayer {
        alpha = progress
        val scale = lerp(ENTRANCE_START_SCALE, 1f, progress)
        scaleX = scale
        scaleY = scale
        translationY = (1f - progress) * risePx
    }
}
