package com.thotapalli.plex.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Downloads on Windows: `%LOCALAPPDATA%\ThotapalliPlex\downloads`, per CLAUDE.md section 11.
 *
 * The same directory the settings file uses, so one uninstall clears both.
 */
class WindowsDownloadFileSystem(
    private val root: Path = defaultRoot(),
) : DownloadFileSystem {

    init {
        Files.createDirectories(root)
    }

    override fun pathFor(ratingKey: String, container: String): String =
        root.resolve("${safe(ratingKey)}.${safe(container)}").toString()

    /**
     * Distinct per stream and per language, so a title with English and French sidecars
     * keeps both rather than the second overwriting the first.
     */
    override fun subtitlePathFor(ratingKey: String, streamId: String, language: String): String =
        root.resolve("${safe(ratingKey)}.${safe(language)}.${safe(streamId)}.srt").toString()

    override fun sizeOf(path: String): Long {
        val file = Path.of(path)
        return if (Files.isRegularFile(file)) Files.size(file) else 0L
    }

    override fun exists(path: String): Boolean = Files.exists(Path.of(path))

    /**
     * Appends to the file in place.
     *
     * [StandardOpenOption.APPEND] is what makes the resume in CLAUDE.md section 11 rule 2
     * work: reading the file back to rewrite it whole would cost a full copy per 8 MB
     * segment and would need twice the disc space of the title being downloaded.
     */
    override suspend fun append(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = Path.of(path)
        file.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        ).use { it.write(bytes) }
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = Path.of(path)
        file.parent?.let { Files.createDirectories(it) }
        Files.write(
            file,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        Unit
    }

    override fun delete(path: String) {
        Files.deleteIfExists(Path.of(path))
    }

    companion object {
        fun defaultRoot(): Path {
            // LOCALAPPDATA is absent when the JVM runs anywhere but Windows, which happens
            // on a developer machine long before it happens on a user's.
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return Path.of(localAppData, "ThotapalliPlex", "downloads")
        }

        /**
         * Rating keys are numeric and containers are short words, but both arrive from the
         * server, and a separator inside either would write outside the downloads directory.
         */
        private fun safe(value: String): String =
            value.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
                .joinToString("")
                .ifEmpty { "_" }
    }
}
