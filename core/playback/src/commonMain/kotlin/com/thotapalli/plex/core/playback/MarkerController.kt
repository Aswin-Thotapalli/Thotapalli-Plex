package com.thotapalli.plex.core.playback

import com.thotapalli.plex.core.model.Marker
import com.thotapalli.plex.core.model.MarkerType

/**
 * Markers and auto-play, from CLAUDE.md section 2.
 *
 * An intro marker shows a skip button while it is active. A credits marker skips
 * automatically into the next episode. The next episode prompt appears during the credits
 * marker or the final thirty seconds, with a ten second countdown cancelled by any input.
 */
class MarkerController(
    private val markers: List<Marker> = emptyList(),
    private val durationMs: Long = 0,
    private val hasNextEpisode: Boolean = false,
) {

    private val intro: Marker? = markers.firstOrNull { it.type == MarkerType.INTRO }
    private val credits: Marker? = markers.firstOrNull { it.type == MarkerType.CREDITS }

    /** True while an intro is on screen and the skip button should be offered. */
    fun showSkipIntro(positionMs: Long): Boolean = intro?.contains(positionMs) == true

    /** Where the skip button jumps to. */
    fun skipIntroTargetMs(): Long? = intro?.endMs

    /**
     * A credits marker skips automatically into the next episode rather than offering a
     * button, so this is true only when there is a next episode to skip into. On the last
     * episode of a show the credits play out.
     */
    fun shouldAutoSkipCredits(positionMs: Long): Boolean =
        hasNextEpisode && credits?.contains(positionMs) == true

    /**
     * The next episode prompt window: the credits marker, or the final thirty seconds when
     * the server reported no credits marker.
     */
    fun showNextEpisodePrompt(positionMs: Long): Boolean {
        if (!hasNextEpisode) return false
        credits?.let { return positionMs >= it.startMs }
        return durationMs > 0 && positionMs >= durationMs - FINAL_WINDOW_MS
    }

    companion object {
        const val FINAL_WINDOW_MS = 30_000L

        /** Cancelled by any input. See CLAUDE.md section 14 item 7. */
        const val COUNTDOWN_MS = 10_000L
    }
}

/**
 * The auto-play countdown.
 *
 * Cancellation is one way on purpose: once the viewer has said no, the prompt does not
 * come back for this item. Re-arming it would mean the countdown reappearing every time
 * they moved the mouse.
 */
class AutoPlayCountdown(private val nowMs: () -> Long) {

    private var startedAtMs: Long? = null
    private var cancelled: Boolean = false

    val isRunning: Boolean get() = startedAtMs != null && !cancelled

    fun start() {
        if (cancelled || startedAtMs != null) return
        startedAtMs = nowMs()
    }

    /** Any input cancels it. */
    fun cancel() {
        cancelled = true
        startedAtMs = null
    }

    /** Cleared when the next item begins. */
    fun reset() {
        startedAtMs = null
        cancelled = false
    }

    fun remainingMs(): Long {
        val started = startedAtMs ?: return MarkerController.COUNTDOWN_MS
        return (MarkerController.COUNTDOWN_MS - (nowMs() - started)).coerceAtLeast(0)
    }

    /** Seconds shown in the prompt, counting 10 down to 1. */
    fun remainingSeconds(): Int = ((remainingMs() + 999) / 1000).toInt()

    fun isElapsed(): Boolean = isRunning && remainingMs() <= 0
}
