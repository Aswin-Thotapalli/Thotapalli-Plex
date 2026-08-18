package com.thotapalli.plex.core.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads on Android phone, tablet, Android TV and Google TV.
 *
 * `context.getExternalFilesDir("downloads")` for all four, per CLAUDE.md section 11. That
 * directory belongs to the application, so it needs no storage permission and it is removed
 * when the application is uninstalled.
 */
class AndroidDownloadFileSystem(context: Context) : DownloadFileSystem {

    private val application = context.applicationContext

    /**
     * Resolved on each use rather than once at construction, because external storage can be
     * unavailable at the moment this class happens to be built and available a second later.
     */
    private val root: File
        get() = (application.getExternalFilesDir(DIRECTORY)
        // Removable external storage can be unmounted. Falling back to internal storage
        // keeps the queue working; refusing to resolve a path would fail every row.
            ?: File(application.filesDir, DIRECTORY))
            .also { if (!it.exists()) it.mkdirs() }

    override fun pathFor(ratingKey: String, container: String): String =
        File(root, "${safe(ratingKey)}.${safe(container)}").absolutePath

    /**
     * Distinct per stream and per language, so a title with English and French sidecars
     * keeps both rather than the second overwriting the first.
     */
    override fun subtitlePathFor(ratingKey: String, streamId: String, language: String): String =
        File(root, "${safe(ratingKey)}.${safe(language)}.${safe(streamId)}.srt").absolutePath

    override fun sizeOf(path: String): Long {
        val file = File(path)
        return if (file.isFile) file.length() else 0L
    }

    override fun exists(path: String): Boolean = File(path).exists()

    /**
     * Appends to the file in place.
     *
     * Opening in append mode is what makes the resume in CLAUDE.md section 11 rule 2 work:
     * reading the file back to rewrite it whole would cost a full copy per 8 MB segment and
     * would need twice the disc space of the title being downloaded.
     */
    override suspend fun append(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { it.write(bytes) }
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).use { it.write(bytes) }
    }

    override fun delete(path: String) {
        File(path).delete()
    }

    private companion object {
        const val DIRECTORY = "downloads"

        /**
         * Rating keys are numeric and containers are short words, but both arrive from the
         * server, and a separator inside either would write outside the downloads directory.
         */
        fun safe(value: String): String =
            value.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
                .joinToString("")
                .ifEmpty { "_" }
    }
}
