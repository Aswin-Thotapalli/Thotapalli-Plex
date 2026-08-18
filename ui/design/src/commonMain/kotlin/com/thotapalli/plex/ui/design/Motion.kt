package com.thotapalli.plex.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The motion specification from CLAUDE.md section 12.
 *
 * Enter is 150 ms standard, exit is 100 ms accelerate. Exit is faster than enter because a
 * thing leaving should get out of the way, while a thing arriving should be followed.
 */
object Motion {
    const val ENTER_MS = 150
    const val EXIT_MS = 100

    /** Player controls fade in and out over 200 ms, after 3000 ms of no input. */
    const val PLAYER_FADE_MS = 200
    const val PLAYER_IDLE_TIMEOUT_MS = 3_000L

    /** Television focus grows over 120 ms. */
    const val FOCUS_MS = 120

    /** The next episode countdown, cancellable by any input. */
    const val AUTO_PLAY_COUNTDOWN_MS = 10_000L

    /** The transcoding chip fades after this and never requires dismissal. */
    const val TRANSCODE_CHIP_MS = 4_000L

    /** Search waits this long after the last keystroke before asking the server. */
    const val SEARCH_DEBOUNCE_MS = 300L

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(ENTER_MS, easing = standard)

    fun <T> exit(): FiniteAnimationSpec<T> = tween(EXIT_MS, easing = accelerate)

    fun <T> focus(): FiniteAnimationSpec<T> = tween(FOCUS_MS, easing = standard)

    fun <T> playerFade(): FiniteAnimationSpec<T> = tween(PLAYER_FADE_MS, easing = standard)
}
