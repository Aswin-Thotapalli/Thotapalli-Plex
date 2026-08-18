package com.thotapalli.plex.core.download

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CLAUDE.md section 16 phase 6 step 2, offline playback resolution. */
class OfflineResolverTest {

    private val store = FakeDownloadStore()
    private val files = FakeFileSystem()
    private val resolver = OfflineResolver(store, files)

    private suspend fun completed(ratingKey: String, bytes: Int = 1024): DownloadRow {
        val path = files.pathFor(ratingKey, "mkv")
        val row = DownloadRow(
            ratingKey = ratingKey,
            partId = "p-$ratingKey",
            localPath = path,
            totalBytes = bytes.toLong(),
            receivedBytes = bytes.toLong(),
            state = DownloadState.COMPLETED,
            queuedAtMs = 1,
        )
        store.insert(row)
        files.write(path, ByteArray(bytes))
        return row
    }

    @Test
    fun aCompletedDownloadPlaysFromDisk() = runTest {
        completed("10241")

        val source = assertNotNull(resolver.localSource("10241"))

        assertEquals("/downloads/10241.mkv", source.path)
        assertEquals(1024L, source.sizeBytes)
        assertTrue(resolver.isPlayableOffline("10241"))
    }

    @Test
    fun anItemThatWasNeverDownloadedHasNoLocalSource() = runTest {
        assertNull(resolver.localSource("10241"))
        assertFalse(resolver.isPlayableOffline("10241"))
    }

    @Test
    fun anIncompleteDownloadIsNotPlayedFromDisk() = runTest {
        store.insert(
            DownloadRow(
                ratingKey = "10241",
                partId = "p",
                localPath = "/downloads/10241.mkv",
                totalBytes = 1024,
                receivedBytes = 512,
                state = DownloadState.RUNNING,
                queuedAtMs = 1,
            ),
        )
        files.write("/downloads/10241.mkv", ByteArray(512))

        // Half a file plays and then cuts off partway, which is worse than streaming.
        assertNull(resolver.localSource("10241"))
    }

    @Test
    fun aRowWhoseFileHasGoneIsNotTrusted() = runTest {
        completed("10241")
        // The platform reclaimed space, or the viewer cleared app data.
        files.delete("/downloads/10241.mkv")

        assertNull(resolver.localSource("10241"))
    }

    @Test
    fun aFileThatNoLongerMatchesItsRecordedSizeIsNotTrusted() = runTest {
        completed("10241")
        files.write("/downloads/10241.mkv", ByteArray(500))

        assertNull(resolver.localSource("10241"))
    }

    @Test
    fun sidecarSubtitlesComeWithTheLocalSource() = runTest {
        completed("10241")
        store.insertSubtitle("10241", SubtitleRequest("99004", "eng", "/downloads/10241.eng.srt"))
        store.insertSubtitle("10241", SubtitleRequest("99005", "fra", "/downloads/10241.fra.srt"))
        files.write("/downloads/10241.eng.srt", ByteArray(10))
        files.write("/downloads/10241.fra.srt", ByteArray(10))

        val source = assertNotNull(resolver.localSource("10241"))

        assertEquals(listOf("eng", "fra"), source.subtitles.map { it.language })
    }

    @Test
    fun aSubtitleWhoseFileIsMissingIsDroppedRatherThanOffered() = runTest {
        completed("10241")
        store.insertSubtitle("10241", SubtitleRequest("99004", "eng", "/downloads/10241.eng.srt"))

        val source = assertNotNull(resolver.localSource("10241"))

        assertTrue(source.subtitles.isEmpty())
    }

    @Test
    fun reconcilingRemovesRowsWhoseFilesHaveGone() = runTest {
        completed("10241")
        completed("10242")
        store.insertSubtitle("10242", SubtitleRequest("99004", "eng", "/downloads/10242.eng.srt"))
        files.delete("/downloads/10242.mkv")

        val removed = resolver.reconcileWithDisk()

        // A Downloads screen listing items that will not play is worse than an empty one.
        assertEquals(listOf("10242"), removed)
        assertEquals(listOf("10241"), store.all().map { it.ratingKey })
        assertTrue(store.subtitlesFor("10242").isEmpty())
    }

    @Test
    fun reconcilingLeavesIntactDownloadsAlone() = runTest {
        completed("10241")

        assertTrue(resolver.reconcileWithDisk().isEmpty())
        assertEquals(1, store.all().size)
    }
}
