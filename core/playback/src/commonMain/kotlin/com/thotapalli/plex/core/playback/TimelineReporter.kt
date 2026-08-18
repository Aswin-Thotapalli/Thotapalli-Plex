package com.thotapalli.plex.core.playback

/**
 * Progress reporting, exactly as CLAUDE.md section 5 specifies.
 *
 * 1. Every 10000 ms while playing.
 * 2. Immediately on play, pause, seek completion and stop.
 * 3. state=stopped before releasing the player, blocking at most 2000 ms.
 * 4. Past 92 percent, scrobble once and stop sending timeline updates for that item.
 *
 * The rules live here rather than in each engine so Media3 and libmpv cannot report
 * differently, which would show up as a resume position that depends on which device last
 * played the item.
 */
class TimelineReporter(
    private val sink: TimelineSink,
    private val nowMs: () -> Long,
) {

    /** Null means nothing has been sent for this item yet, so the next tick reports. */
    private var lastSentAtMs: Long? = null
    private var scrobbled: Boolean = false
    private var currentRatingKey: String? = null

    /** Starting a new item clears the per-item state, including the scrobble latch. */
    fun startItem(ratingKey: String) {
        currentRatingKey = ratingKey
        lastSentAtMs = null
        scrobbled = false
    }

    /**
     * Called on every position tick.
     *
     * Returns what was actually sent, so a caller can log it and a test can assert on it
     * without reaching into the sink.
     */
    suspend fun onTick(
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
    ): TimelineAction {
        if (ratingKey != currentRatingKey) startItem(ratingKey)

        // Past 92 percent the item counts as watched. Scrobble once, then go quiet: further
        // timeline updates would drag the server's position back into the item and undo it.
        if (!scrobbled && isPastScrobbleThreshold(positionMs, durationMs)) {
            scrobbled = true
            sink.scrobble(ratingKey)
            return TimelineAction.SCROBBLED
        }
        if (scrobbled) return TimelineAction.SUPPRESSED

        val immediate = state != TimelineState.PLAYING
        val lastSent = lastSentAtMs
        // The first report of an item always goes, because playback has just started and
        // the interval has nothing to measure from.
        val due = lastSent == null || nowMs() - lastSent >= REPORT_INTERVAL_MS

        if (!immediate && !due) return TimelineAction.SKIPPED

        lastSentAtMs = nowMs()
        sink.timeline(ratingKey, state, positionMs, durationMs)
        return TimelineAction.SENT
    }

    /** Play, pause and seek completion always report, whatever the interval says. */
    suspend fun onImmediate(
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
    ): TimelineAction {
        if (ratingKey != currentRatingKey) startItem(ratingKey)
        if (scrobbled) return TimelineAction.SUPPRESSED

        lastSentAtMs = nowMs()
        sink.timeline(ratingKey, state, positionMs, durationMs)
        return TimelineAction.SENT
    }

    /**
     * The stop report, sent before the player is released.
     *
     * Sent even after a scrobble, because the server needs to know the session ended; the
     * scrobble suppression exists to stop position updates, not the end of the session.
     */
    suspend fun onStop(ratingKey: String, positionMs: Long, durationMs: Long) {
        sink.timeline(ratingKey, TimelineState.STOPPED, positionMs, durationMs)
    }

    companion object {
        const val REPORT_INTERVAL_MS = 10_000L

        /** Blocking release for longer than this is worse than losing one position. */
        const val STOP_TIMEOUT_MS = 2_000L

        const val SCROBBLE_THRESHOLD = 0.92

        fun isPastScrobbleThreshold(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs.toDouble() / durationMs >= SCROBBLE_THRESHOLD
    }
}

/** Where a report goes. The server in normal use, the offline queue when unreachable. */
interface TimelineSink {
    suspend fun timeline(ratingKey: String, state: TimelineState, positionMs: Long, durationMs: Long)
    suspend fun scrobble(ratingKey: String)
}

enum class TimelineState { PLAYING, PAUSED, STOPPED }

enum class TimelineAction {
    SENT,

    /** Inside the interval and not an immediate trigger. */
    SKIPPED,

    SCROBBLED,

    /** Already scrobbled, so no further position updates for this item. */
    SUPPRESSED,
}
