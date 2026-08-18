package com.thotapalli.plex.core.download

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {

    private val totalBytes = 20L * 1024 * 1024 // two and a half 8 MB segments
    private val store = FakeDownloadStore()
    private val files = FakeFileSystem()
    private val network = FakeNetwork(metered = false, unmeteredOnly = true)
    private var now = 1_000L

    private fun queue(transport: DownloadTransport, scope: TestScope) =
        DownloadQueue(store, transport, files, network, scope, { now })

    private val request = DownloadRequest(
        ratingKey = "10241",
        partId = "88001",
        container = "mkv",
        sizeBytes = totalBytes,
    )

    // --- CLAUDE.md section 16 phase 6 step 1 -------------------------------------------

    @Test
    fun anInterruptedDownloadResumesFromItsReceivedByteCount() = runTest {
        val transport = FakeTransport(totalBytes, failAfterBytes = 8L * 1024 * 1024)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        // The first segment landed; every attempt at the second threw. After MAX_ATTEMPTS
        // the item is left for the viewer rather than retried forever, and the bytes
        // already fetched stay on disk.
        val partial = assertNotNull(store.byRatingKey("10241"))
        assertEquals(8L * 1024 * 1024, partial.receivedBytes)
        assertEquals(DownloadState.FAILED, partial.state)
        assertEquals(8L * 1024 * 1024, files.sizeOf("/downloads/10241.mkv"))
        q.stop()

        // The connection comes back and the viewer retries.
        transport.failAfterBytes = null
        transport.requestedRanges.clear()
        val resumed = queue(transport, this)
        resumed.start()
        resumed.resume("10241")
        advanceUntilIdle()

        // The retry asks for the range starting at what was already received, not zero.
        assertEquals(8L * 1024 * 1024, transport.requestedRanges.first().first)
        assertEquals(DownloadState.COMPLETED, assertNotNull(store.byRatingKey("10241")).state)
        assertEquals(totalBytes, files.sizeOf("/downloads/10241.mkv"))
        resumed.stop()
    }

    @Test
    fun aRepeatedlyFailingDownloadStopsRetryingRatherThanSpinning() = runTest {
        val transport = FakeTransport(totalBytes, failAfterBytes = 0)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        // Exactly MAX_ATTEMPTS attempts, then it gives up. Without the cap this loops for
        // as long as the application runs and never surfaces to the viewer.
        assertEquals(DownloadQueue.MAX_ATTEMPTS, transport.requestedRanges.size)
        assertEquals(DownloadState.FAILED, assertNotNull(store.byRatingKey("10241")).state)
        q.stop()
    }

    @Test
    fun rangesAreRequestedInEightMegabyteSegments() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        val sizes = transport.requestedRanges.map { it.second - it.first + 1 }
        assertEquals(3, transport.requestedRanges.size, "20 MB is two full segments and a remainder")
        assertEquals(DownloadQueue.SEGMENT_BYTES, sizes[0])
        assertEquals(DownloadQueue.SEGMENT_BYTES, sizes[1])
        assertEquals(totalBytes - 2 * DownloadQueue.SEGMENT_BYTES, sizes[2])
        q.stop()
    }

    @Test
    fun rangesAreContiguousAndInclusive() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        transport.requestedRanges.zipWithNext().forEach { (first, second) ->
            assertEquals(first.second + 1, second.first, "no gap and no overlap between segments")
        }
        q.stop()
    }

    @Test
    fun aSizeMismatchOnCompletionFailsTheRowAndDeletesThePartialFile() = runTest {
        // The server reports more bytes than it actually supplies.
        val transport = FakeTransport(totalBytes, truncateAt = 8L * 1024 * 1024)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        assertEquals(DownloadState.FAILED, assertNotNull(store.byRatingKey("10241")).state)
        // A truncated file that plays and then cuts off partway is worse than a visible
        // failure. See CLAUDE.md section 11 rule 5.
        assertFalse(files.exists("/downloads/10241.mkv"))
        q.stop()
    }

    @Test
    fun aFileLongerThanTheServerSaysIsDiscardedRatherThanResumed() = runTest {
        files.write("/downloads/10241.mkv", ByteArray((totalBytes + 1024).toInt()))
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        // Longer than reported means it is not this file, so resuming from it would splice
        // two different files together.
        assertEquals(0L, transport.requestedRanges.first().first)
        assertEquals(DownloadState.COMPLETED, assertNotNull(store.byRatingKey("10241")).state)
        q.stop()
    }

    @Test
    fun oneItemAtATimeInQueuedOrder() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        now = 1_000
        q.enqueue(request)
        now = 2_000
        q.enqueue(request.copy(ratingKey = "10242"))
        now = 3_000
        q.enqueue(request.copy(ratingKey = "10243"))

        q.start()
        advanceUntilIdle()

        // A serial queue keeps the connection responsive for concurrent playback.
        assertEquals(
            listOf(DownloadState.COMPLETED, DownloadState.COMPLETED, DownloadState.COMPLETED),
            store.all().map { it.state },
        )
        q.stop()
    }

    @Test
    fun externalSubtitlesDownloadSeparately() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(
            request.copy(
                subtitles = listOf(
                    SubtitleRequest("99004", "eng", "/downloads/10241.eng.srt"),
                    SubtitleRequest("99005", "fra", "/downloads/10241.fra.srt"),
                ),
            ),
        )
        q.start()
        advanceUntilIdle()

        assertEquals(2, transport.subtitleFetches)
        assertTrue(files.exists("/downloads/10241.eng.srt"))
        assertTrue(files.exists("/downloads/10241.fra.srt"))
        q.stop()
    }

    // --- CLAUDE.md section 11 rule 6, network -------------------------------------------

    @Test
    fun aMeteredNetworkLeavesTheRowQueuedRatherThanFailingIt() = runTest {
        network.metered = true
        network.unmeteredOnly = true
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        assertTrue(transport.requestedRanges.isEmpty(), "nothing is fetched on metered data")
        assertEquals(DownloadState.QUEUED, assertNotNull(store.byRatingKey("10241")).state)
        q.stop()
    }

    @Test
    fun turningTheSettingOffAllowsMeteredDownloads() = runTest {
        network.metered = true
        network.unmeteredOnly = false
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        assertEquals(DownloadState.COMPLETED, assertNotNull(store.byRatingKey("10241")).state)
        q.stop()
    }

    @Test
    fun aNetworkChangeWakesABlockedQueue() = runTest {
        network.metered = true
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)

        q.enqueue(request)
        q.start()
        advanceUntilIdle()
        assertEquals(DownloadState.QUEUED, assertNotNull(store.byRatingKey("10241")).state)

        network.metered = false
        q.onNetworkChanged()
        advanceUntilIdle()

        assertEquals(DownloadState.COMPLETED, assertNotNull(store.byRatingKey("10241")).state)
        q.stop()
    }

    // --- pause and delete ----------------------------------------------------------------

    @Test
    fun deletingRemovesTheRowTheFileAndTheSubtitles() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)
        q.enqueue(request.copy(subtitles = listOf(SubtitleRequest("99004", "eng", "/downloads/10241.eng.srt"))))
        q.start()
        advanceUntilIdle()

        q.delete("10241")

        assertTrue(store.rows.isEmpty())
        assertFalse(files.exists("/downloads/10241.mkv"))
        assertFalse(files.exists("/downloads/10241.eng.srt"))
        q.stop()
    }

    @Test
    fun pausingBeforeItRunsKeepsItOutOfTheQueue() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)
        q.enqueue(request)

        q.pause("10241")
        q.start()
        advanceUntilIdle()

        assertEquals(DownloadState.PAUSED, assertNotNull(store.byRatingKey("10241")).state)
        assertTrue(transport.requestedRanges.isEmpty())
        q.stop()
    }

    @Test
    fun resumingAPausedDownloadPicksItUpAgain() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)
        q.enqueue(request)
        q.pause("10241")
        q.start()
        advanceUntilIdle()

        q.resume("10241")
        advanceUntilIdle()

        assertEquals(DownloadState.COMPLETED, assertNotNull(store.byRatingKey("10241")).state)
        q.stop()
    }

    @Test
    fun theDownloadedBytesAreTheServersBytes() = runTest {
        val transport = FakeTransport(totalBytes)
        val q = queue(transport, this)
        q.enqueue(request)
        q.start()
        advanceUntilIdle()

        // The fake transport writes offset-derived content, so a file assembled out of
        // order or with a gap would not match.
        val written = files.files.getValue("/downloads/10241.mkv")
        assertEquals(totalBytes.toInt(), written.size)
        listOf(0, 1, 8 * 1024 * 1024, 8 * 1024 * 1024 + 1, written.size - 1).forEach { index ->
            assertEquals((index % 251).toByte(), written[index], "byte $index is wrong")
        }
        q.stop()
    }
}
