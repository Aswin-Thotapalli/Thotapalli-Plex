package com.thotapalli.plex.core.download

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** CLAUDE.md section 11: where downloads live on Windows and how they resume. */
class WindowsDownloadFileSystemTest {

    private val root: Path = Files.createTempDirectory("thotapalli-downloads")
    private val files = WindowsDownloadFileSystem(root)

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun appendingSegmentsBuildsTheFileInOrder() = runTest {
        val path = files.pathFor("10241", "mkv")

        files.append(path, ByteArray(8) { it.toByte() })
        files.append(path, ByteArray(8) { (it + 8).toByte() })

        // Contiguous rather than overwritten. Anything else and every resumed download
        // would hold only its final segment.
        assertEquals(16L, files.sizeOf(path))
        assertEquals(
            (0 until 16).map { it.toByte() },
            Files.readAllBytes(Path.of(path)).toList(),
        )
    }

    @Test
    fun theReceivedByteCountIsTheSizeOnDisc() = runTest {
        val path = files.pathFor("10241", "mkv")
        files.append(path, ByteArray(1024))

        // This is the resume point the queue reads back, so the two have to agree exactly.
        assertEquals(1024L, files.sizeOf(path))
        assertTrue(files.exists(path))
    }

    @Test
    fun sizeOfAMissingFileIsZeroSoAFreshDownloadStartsAtTheBeginning() {
        assertEquals(0L, files.sizeOf(files.pathFor("10241", "mkv")))
        assertFalse(files.exists(files.pathFor("10241", "mkv")))
    }

    @Test
    fun writingReplacesRatherThanAppends() = runTest {
        val path = files.subtitlePathFor("10241", "99004", "eng")

        files.append(path, ByteArray(100))
        files.write(path, ByteArray(10))

        // Sidecars are fetched whole, so a retry must not leave the previous copy in front
        // of the new one.
        assertEquals(10L, files.sizeOf(path))
    }

    @Test
    fun subtitlePathsAreDistinctPerStreamAndLanguage() {
        val english = files.subtitlePathFor("10241", "99004", "eng")
        val french = files.subtitlePathFor("10241", "99005", "fra")
        val sameLanguageOtherStream = files.subtitlePathFor("10241", "99006", "eng")

        assertNotEquals(english, french)
        assertNotEquals(english, sameLanguageOtherStream)
    }

    @Test
    fun deletingRemovesThePartialFile() = runTest {
        val path = files.pathFor("10241", "mkv")
        files.append(path, ByteArray(16))

        files.delete(path)

        assertFalse(files.exists(path))
        // Deleting what is not there is what happens when a failed row is cleaned up twice.
        files.delete(path)
    }

    @Test
    fun everythingLivesUnderTheDownloadsRoot() {
        assertTrue(Path.of(files.pathFor("10241", "mkv")).startsWith(root))
        assertTrue(Path.of(files.subtitlePathFor("10241", "99004", "eng")).startsWith(root))
    }

    @Test
    fun aSeparatorInAServerSuppliedValueCannotEscapeTheDownloadsRoot() {
        // Rating keys and containers arrive from the server, and neither is validated there.
        val path = Path.of(files.pathFor("../../10241", "mkv/../../evil"))

        assertEquals(root, path.parent)
    }

    @Test
    fun theDefaultRootIsUnderLocalAppData() {
        // CLAUDE.md section 11: %LOCALAPPDATA%\ThotapalliPlex\downloads.
        val default = WindowsDownloadFileSystem.defaultRoot()

        assertEquals("downloads", default.fileName.toString())
        assertEquals("ThotapalliPlex", default.parent.fileName.toString())
    }
}
