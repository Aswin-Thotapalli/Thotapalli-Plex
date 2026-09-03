package com.thotapalli.plex.core.data

import com.thotapalli.plex.core.data.db.PlexDatabase
import com.thotapalli.plex.core.download.DownloadRow
import com.thotapalli.plex.core.download.DownloadState
import com.thotapalli.plex.core.download.DownloadStore
import com.thotapalli.plex.core.download.PendingTimelineRow
import com.thotapalli.plex.core.download.PendingTimelineStore
import com.thotapalli.plex.core.download.SubtitleRequest
import com.thotapalli.plex.core.download.SubtitleRow
import com.thotapalli.plex.core.data.db.Download as DownloadDbRow
import com.thotapalli.plex.core.data.db.Download_subtitle as SubtitleDbRow
import com.thotapalli.plex.core.data.db.Pending_timeline as PendingDbRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The section 7 download tables, behind the section 11 queue's interface.
 *
 * Every query runs on [dbContext] — the same single-slot dispatcher [LibraryRepository] uses — so DB
 * work never blocks the caller's thread and the desktop's single JDBC connection is never touched
 * concurrently by the queue and a library read.
 */
class SqlDownloadStore(
    database: PlexDatabase,
    private val dbContext: CoroutineDispatcher = defaultDbDispatcher(),
) : DownloadStore {

    private val downloads = database.downloadQueries
    private val subtitles = database.downloadSubtitleQueries

    override suspend fun insert(row: DownloadRow) {
        withContext(dbContext) {
            downloads.insert(
                rating_key = row.ratingKey,
                part_id = row.partId,
                local_path = row.localPath,
                total_bytes = row.totalBytes,
                received_bytes = row.receivedBytes,
                state = row.state.name,
                queued_at = row.queuedAtMs,
            )
        }
    }

    override suspend fun insertSubtitle(ratingKey: String, subtitle: SubtitleRequest) {
        withContext(dbContext) {
            subtitles.insert(
                rating_key = ratingKey,
                stream_id = subtitle.streamId,
                language = subtitle.language,
                local_path = subtitle.localPath,
            )
        }
    }

    override suspend fun nextQueued(): DownloadRow? = withContext(dbContext) {
        downloads.selectNextQueued().executeAsOneOrNull()?.toDownloadRow()
    }

    override suspend fun byRatingKey(ratingKey: String): DownloadRow? = withContext(dbContext) {
        downloads.selectByRatingKey(ratingKey).executeAsOneOrNull()?.toDownloadRow()
    }

    override suspend fun all(): List<DownloadRow> = withContext(dbContext) {
        downloads.selectAll().executeAsList().map { it.toDownloadRow() }
    }

    override suspend fun completed(): List<DownloadRow> = withContext(dbContext) {
        downloads.selectCompleted().executeAsList().map { it.toDownloadRow() }
    }

    override suspend fun subtitlesFor(ratingKey: String): List<SubtitleRow> = withContext(dbContext) {
        subtitles.selectForItem(ratingKey).executeAsList().map { it.toSubtitleRow() }
    }

    override suspend fun updateProgress(ratingKey: String, receivedBytes: Long) {
        withContext(dbContext) { downloads.updateProgress(receivedBytes, ratingKey) }
    }

    override suspend fun updateState(ratingKey: String, state: DownloadState) {
        withContext(dbContext) { downloads.updateState(state.name, ratingKey) }
    }

    override suspend fun updateStateAndProgress(
        ratingKey: String,
        state: DownloadState,
        receivedBytes: Long,
    ) {
        withContext(dbContext) { downloads.updateStateAndProgress(state.name, receivedBytes, ratingKey) }
    }

    override suspend fun delete(ratingKey: String) {
        withContext(dbContext) { downloads.delete(ratingKey) }
    }

    override suspend fun deleteSubtitles(ratingKey: String) {
        withContext(dbContext) { subtitles.deleteForItem(ratingKey) }
    }

    override suspend fun totalBytesOnDisk(): Long = withContext(dbContext) {
        downloads.totalBytesOnDisk().executeAsOne().SUM ?: 0L
    }
}

private fun DownloadDbRow.toDownloadRow() = DownloadRow(
    ratingKey = rating_key,
    partId = part_id,
    localPath = local_path,
    totalBytes = total_bytes,
    receivedBytes = received_bytes,
    // An unrecognised state means a newer build wrote the row. Treating it as queued is
    // safe: the queue re-derives everything from the bytes on disk.
    state = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.QUEUED),
    queuedAtMs = queued_at,
)

private fun SubtitleDbRow.toSubtitleRow() = SubtitleRow(
    ratingKey = rating_key,
    streamId = stream_id,
    language = language,
    localPath = local_path,
)

/** The section 7 pending_timeline table, behind the offline queue's interface. */
class SqlPendingTimelineStore(
    database: PlexDatabase,
    private val dbContext: CoroutineDispatcher = defaultDbDispatcher(),
) : PendingTimelineStore {

    private val queries = database.pendingTimelineQueries

    override suspend fun insert(row: PendingTimelineRow) {
        withContext(dbContext) {
            queries.insert(
                rating_key = row.ratingKey,
                position_ms = row.positionMs,
                duration_ms = row.durationMs,
                state = row.state,
                recorded_at = row.recordedAtMs,
            )
        }
    }

    override suspend fun collapsed(): List<PendingTimelineRow> = withContext(dbContext) {
        queries.selectCollapsed().executeAsList().map { it.toRow() }
    }

    override suspend fun all(): List<PendingTimelineRow> = withContext(dbContext) {
        queries.selectAll().executeAsList().map { it.toRow() }
    }

    override suspend fun count(): Long = withContext(dbContext) { queries.count().executeAsOne() }

    override suspend fun deleteUpTo(ratingKey: String, recordedAtMs: Long) {
        withContext(dbContext) { queries.deleteUpTo(ratingKey, recordedAtMs) }
    }

    override suspend fun deleteAll() {
        withContext(dbContext) { queries.deleteAll() }
    }
}

private fun PendingDbRow.toRow() = PendingTimelineRow(
    id = id,
    ratingKey = rating_key,
    positionMs = position_ms,
    durationMs = duration_ms,
    state = state,
    recordedAtMs = recorded_at,
)
