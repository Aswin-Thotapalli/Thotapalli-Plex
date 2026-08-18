package com.thotapalli.plex.core.download

/**
 * Decides whether an item plays from disk or from the server.
 *
 * A completed download wins unconditionally, even when the server is reachable: the local
 * copy is the library's own file, so playing it saves the network and cannot be worse than
 * streaming the same bytes. See CLAUDE.md section 16 phase 6 step 2.
 */
class OfflineResolver(
    private val store: DownloadStore,
    private val files: DownloadFileSystem,
) {

    /**
     * Where to play [ratingKey] from.
     *
     * Returns null when there is no usable local copy, in which case the caller streams.
     */
    suspend fun localSource(ratingKey: String): LocalSource? {
        val row = store.byRatingKey(ratingKey) ?: return null
        if (row.state != DownloadState.COMPLETED) return null

        // The row says complete but the file may have been deleted from underneath us, by
        // the platform reclaiming space or by the viewer clearing app data. Trusting the
        // row alone would hand the player a path that does not exist.
        if (!files.exists(row.localPath)) return null
        if (files.sizeOf(row.localPath) != row.totalBytes) return null

        return LocalSource(
            path = row.localPath,
            sizeBytes = row.totalBytes,
            subtitles = store.subtitlesFor(ratingKey)
                .filter { files.exists(it.localPath) }
                .map { LocalSubtitle(it.language, it.localPath) },
        )
    }

    /** True when the item can be played with no network at all. */
    suspend fun isPlayableOffline(ratingKey: String): Boolean = localSource(ratingKey) != null

    /**
     * Removes rows whose files have gone.
     *
     * Run at start up, because a Downloads screen listing items that will not play is worse
     * than one that is honestly empty.
     */
    suspend fun reconcileWithDisk(): List<String> {
        val orphaned = store.completed()
            .filter { !files.exists(it.localPath) || files.sizeOf(it.localPath) != it.totalBytes }

        orphaned.forEach { row ->
            store.subtitlesFor(row.ratingKey).forEach { files.delete(it.localPath) }
            store.deleteSubtitles(row.ratingKey)
            store.delete(row.ratingKey)
        }

        return orphaned.map { it.ratingKey }
    }
}

data class LocalSource(
    val path: String,
    val sizeBytes: Long,
    val subtitles: List<LocalSubtitle>,
)

data class LocalSubtitle(
    val language: String,
    val path: String,
)
