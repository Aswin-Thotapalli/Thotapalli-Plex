package com.thotapalli.plex.core.download

/**
 * Offline watch state, from CLAUDE.md section 11.
 *
 * 1. Collapse the queue: keep the newest row per rating key, discard the rest.
 * 2. Replay oldest first as ordinary timeline requests.
 * 3. Resolve conflicts by recency, comparing recorded_at against the server's lastViewedAt.
 * 4. Delete rows only after the server accepts them.
 */
class OfflineTimelineQueue(
    private val store: PendingTimelineStore,
    private val nowMs: () -> Long,
) {

    /** Records a position while the server is unreachable. */
    suspend fun record(ratingKey: String, positionMs: Long, durationMs: Long, state: String) {
        store.insert(
            PendingTimelineRow(
                id = 0,
                ratingKey = ratingKey,
                positionMs = positionMs,
                durationMs = durationMs,
                state = state,
                recordedAtMs = nowMs(),
            ),
        )
    }

    /**
     * Replays the queue on reconnection.
     *
     * Only the newest row per rating key is sent, because Plex stores a position rather
     * than a history: sending the older ones would move the position backwards and then
     * forwards again for no benefit.
     *
     * A row is deleted only once the server has accepted it, so a replay interrupted
     * halfway leaves the rest to be sent next time rather than losing them.
     */
    suspend fun replay(send: suspend (PendingTimelineRow) -> ReplayOutcome): ReplayReport {
        val collapsed = store.collapsed()
        var sent = 0
        var skipped = 0
        var failed = 0

        for (row in collapsed) {
            when (send(row)) {
                ReplayOutcome.ACCEPTED -> {
                    // Everything for this key up to and including this row is now
                    // represented on the server.
                    store.deleteUpTo(row.ratingKey, row.recordedAtMs)
                    sent++
                }

                ReplayOutcome.SERVER_IS_NEWER -> {
                    // The server saw this item more recently than we did offline, so the
                    // local position is stale and dropping it is correct.
                    store.deleteUpTo(row.ratingKey, row.recordedAtMs)
                    skipped++
                }

                ReplayOutcome.FAILED -> {
                    // Left in place for the next reconnection.
                    failed++
                }
            }
        }

        return ReplayReport(collapsed = collapsed.size, sent = sent, skipped = skipped, failed = failed)
    }

    /**
     * Conflict resolution by recency.
     *
     * @param serverLastViewedAtSeconds the server's lastViewedAt, which Plex reports in
     *   seconds while everything here is in milliseconds.
     */
    fun localWins(recordedAtMs: Long, serverLastViewedAtSeconds: Long?): Boolean {
        if (serverLastViewedAtSeconds == null) return true
        return recordedAtMs > serverLastViewedAtSeconds * 1000
    }

    suspend fun pendingCount(): Long = store.count()
}

data class PendingTimelineRow(
    val id: Long,
    val ratingKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val state: String,
    val recordedAtMs: Long,
)

enum class ReplayOutcome {
    ACCEPTED,

    /** The server's own position is more recent, so the offline one is discarded. */
    SERVER_IS_NEWER,

    /** Still unreachable. The row stays for the next attempt. */
    FAILED,
}

data class ReplayReport(
    val collapsed: Int,
    val sent: Int,
    val skipped: Int,
    val failed: Int,
)

interface PendingTimelineStore {
    suspend fun insert(row: PendingTimelineRow)

    /** The newest row per rating key, oldest first. */
    suspend fun collapsed(): List<PendingTimelineRow>

    suspend fun all(): List<PendingTimelineRow>
    suspend fun count(): Long
    suspend fun deleteUpTo(ratingKey: String, recordedAtMs: Long)
    suspend fun deleteAll()
}
