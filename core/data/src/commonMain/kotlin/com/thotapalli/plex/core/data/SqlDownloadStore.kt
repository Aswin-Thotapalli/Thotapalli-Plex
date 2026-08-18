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

/** The section 7 download tables, behind the section 11 queue's interface. */
class SqlDownloadStore(database: PlexDatabase) : DownloadStore {

    private val downloads = database.downloadQueries
    private val subtitles = database.downloadSubtitleQueries

    override suspend fun insert(row: DownloadRow) {
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

    override suspend fun insertSubtitle(ratingKey: String, subtitle: SubtitleRequest) {
        subtitles.insert(
            rating_key = ratingKey,
            stream_id = subtitle.streamId,
            language = subtitle.language,
            local_path = subtitle.localPath,
        )
    }

    override suspend fun nextQueued(): DownloadRow? =
        downloads.selectNextQueued().executeAsOneOrNull()?.toDownloadRow()

    override suspend fun byRatingKey(ratingKey: String): DownloadRow? =
        downloads.selectByRatingKey(ratingKey).executeAsOneOrNull()?.toDownloadRow()

    override suspend fun all(): List<DownloadRow> =
        downloads.selectAll().executeAsList().map { it.toDownloadRow() }

    override suspend fun completed(): List<DownloadRow> =
        downloads.selectCompleted().executeAsList().map { it.toDownloadRow() }

    override suspend fun subtitlesFor(ratingKey: String): List<SubtitleRow> =
        subtitles.selectForItem(ratingKey).executeAsList().map { it.toSubtitleRow() }

    override suspend fun updateProgress(ratingKey: String, receivedBytes: Long) {
        downloads.updateProgress(receivedBytes, ratingKey)
    }

    override suspend fun updateState(ratingKey: String, state: DownloadState) {
        downloads.updateState(state.name, ratingKey)
    }

    override suspend fun updateStateAndProgress(
        ratingKey: String,
        state: DownloadState,
        receivedBytes: Long,
    ) {
        downloads.updateStateAndProgress(state.name, receivedBytes, ratingKey)
    }

    override suspend fun delete(ratingKey: String) {
        downloads.delete(ratingKey)
    }

    override suspend fun deleteSubtitles(ratingKey: String) {
        subtitles.deleteForItem(ratingKey)
    }

    override suspend fun totalBytesOnDisk(): Long =
        downloads.totalBytesOnDisk().executeAsOne().SUM ?: 0L
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
class SqlPendingTimelineStore(database: PlexDatabase) : PendingTimelineStore {

    private val queries = database.pendingTimelineQueries

    override suspend fun insert(row: PendingTimelineRow) {
        queries.insert(
            rating_key = row.ratingKey,
            position_ms = row.positionMs,
            duration_ms = row.durationMs,
            state = row.state,
            recorded_at = row.recordedAtMs,
        )
    }

    override suspend fun collapsed(): List<PendingTimelineRow> =
        queries.selectCollapsed().executeAsList().map { it.toRow() }

    override suspend fun all(): List<PendingTimelineRow> =
        queries.selectAll().executeAsList().map { it.toRow() }

    override suspend fun count(): Long = queries.count().executeAsOne()

    override suspend fun deleteUpTo(ratingKey: String, recordedAtMs: Long) {
        queries.deleteUpTo(ratingKey, recordedAtMs)
    }

    override suspend fun deleteAll() {
        queries.deleteAll()
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
