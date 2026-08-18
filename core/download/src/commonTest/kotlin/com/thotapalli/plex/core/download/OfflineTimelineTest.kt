package com.thotapalli.plex.core.download

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CLAUDE.md section 11, offline watch state. */
class OfflineTimelineTest {

    private val store = FakePendingTimelineStore()
    private var now = 1_000L
    private val queue = OfflineTimelineQueue(store) { now }

    @Test
    fun onlyTheNewestRowPerItemIsSent() = runTest {
        // Plex stores a position rather than a history, so replaying every tick would move
        // the position backwards and forwards for no benefit.
        now = 1_000; queue.record("a", 10_000, 100_000, "playing")
        now = 2_000; queue.record("a", 20_000, 100_000, "playing")
        now = 3_000; queue.record("a", 30_000, 100_000, "paused")
        now = 4_000; queue.record("b", 5_000, 60_000, "playing")

        val sent = mutableListOf<Pair<String, Long>>()
        val report = queue.replay { row ->
            sent += row.ratingKey to row.positionMs
            ReplayOutcome.ACCEPTED
        }

        assertEquals(listOf("a" to 30_000L, "b" to 5_000L), sent)
        assertEquals(2, report.collapsed)
        assertEquals(2, report.sent)
    }

    @Test
    fun replayIsOldestFirst() = runTest {
        now = 5_000; queue.record("later", 1, 10, "playing")
        now = 1_000; queue.record("earlier", 1, 10, "playing")

        val order = mutableListOf<String>()
        queue.replay { row -> order += row.ratingKey; ReplayOutcome.ACCEPTED }

        assertEquals(listOf("earlier", "later"), order)
    }

    @Test
    fun rowsAreDeletedOnlyAfterTheServerAcceptsThem() = runTest {
        now = 1_000; queue.record("a", 10_000, 100_000, "playing")
        now = 2_000; queue.record("b", 20_000, 100_000, "playing")

        queue.replay { row -> if (row.ratingKey == "a") ReplayOutcome.ACCEPTED else ReplayOutcome.FAILED }

        // A replay interrupted halfway leaves the rest for next time rather than losing it.
        assertEquals(listOf("b"), store.all().map { it.ratingKey })
        assertEquals(1L, queue.pendingCount())
    }

    @Test
    fun acceptingARowClearsEveryOlderRowForThatItem() = runTest {
        now = 1_000; queue.record("a", 10_000, 100_000, "playing")
        now = 2_000; queue.record("a", 20_000, 100_000, "playing")
        now = 3_000; queue.record("a", 30_000, 100_000, "playing")

        queue.replay { ReplayOutcome.ACCEPTED }

        assertEquals(0L, queue.pendingCount())
    }

    @Test
    fun aRowTheServerHasAlreadyBeatenIsDiscarded() = runTest {
        now = 1_000; queue.record("a", 10_000, 100_000, "playing")

        val report = queue.replay { ReplayOutcome.SERVER_IS_NEWER }

        assertEquals(1, report.skipped)
        assertEquals(0, report.sent)
        assertEquals(0L, queue.pendingCount())
    }

    @Test
    fun conflictsResolveByRecency() {
        // The server reports lastViewedAt in seconds; everything here is milliseconds.
        assertTrue(queue.localWins(recordedAtMs = 2_000_000, serverLastViewedAtSeconds = 1_000))
        assertFalse(queue.localWins(recordedAtMs = 500_000, serverLastViewedAtSeconds = 1_000))
    }

    @Test
    fun anItemTheServerHasNeverSeenAlwaysTakesTheLocalPosition() {
        assertTrue(queue.localWins(recordedAtMs = 1, serverLastViewedAtSeconds = null))
    }

    @Test
    fun anEmptyQueueReplaysNothing() = runTest {
        val report = queue.replay { ReplayOutcome.ACCEPTED }

        assertEquals(0, report.collapsed)
        assertEquals(0, report.sent)
    }
}
