package com.thotapalli.plex.core.download

/** In-memory implementations, so the queue's rules are tested rather than SQLite's. */
class FakeDownloadStore : DownloadStore {
    val rows = mutableMapOf<String, DownloadRow>()
    private val subtitles = mutableMapOf<String, MutableList<SubtitleRow>>()

    override suspend fun insert(row: DownloadRow) {
        rows[row.ratingKey] = row
    }

    override suspend fun insertSubtitle(ratingKey: String, subtitle: SubtitleRequest) {
        subtitles.getOrPut(ratingKey) { mutableListOf() } += SubtitleRow(
            ratingKey = ratingKey,
            streamId = subtitle.streamId,
            language = subtitle.language,
            localPath = subtitle.localPath,
        )
    }

    override suspend fun nextQueued(): DownloadRow? =
        rows.values.filter { it.state == DownloadState.QUEUED }.minByOrNull { it.queuedAtMs }

    override suspend fun byRatingKey(ratingKey: String): DownloadRow? = rows[ratingKey]

    override suspend fun all(): List<DownloadRow> = rows.values.sortedBy { it.queuedAtMs }

    override suspend fun completed(): List<DownloadRow> =
        rows.values.filter { it.state == DownloadState.COMPLETED }

    override suspend fun subtitlesFor(ratingKey: String): List<SubtitleRow> =
        subtitles[ratingKey].orEmpty()

    override suspend fun updateProgress(ratingKey: String, receivedBytes: Long) {
        rows[ratingKey]?.let { rows[ratingKey] = it.copy(receivedBytes = receivedBytes) }
    }

    override suspend fun updateState(ratingKey: String, state: DownloadState) {
        rows[ratingKey]?.let { rows[ratingKey] = it.copy(state = state) }
    }

    override suspend fun updateStateAndProgress(
        ratingKey: String,
        state: DownloadState,
        receivedBytes: Long,
    ) {
        rows[ratingKey]?.let { rows[ratingKey] = it.copy(state = state, receivedBytes = receivedBytes) }
    }

    override suspend fun delete(ratingKey: String) {
        rows.remove(ratingKey)
    }

    override suspend fun deleteSubtitles(ratingKey: String) {
        subtitles.remove(ratingKey)
    }

    override suspend fun totalBytesOnDisk(): Long =
        rows.values.filter { it.state == DownloadState.COMPLETED }.sumOf { it.receivedBytes }
}

class FakeFileSystem : DownloadFileSystem {
    val files = mutableMapOf<String, ByteArray>()

    override fun pathFor(ratingKey: String, container: String) = "/downloads/$ratingKey.$container"

    override fun subtitlePathFor(ratingKey: String, streamId: String, language: String) =
        "/downloads/$ratingKey.$language.$streamId.srt"

    override fun sizeOf(path: String): Long = files[path]?.size?.toLong() ?: 0L

    override fun exists(path: String): Boolean = files.containsKey(path)

    override suspend fun append(path: String, bytes: ByteArray) {
        files[path] = (files[path] ?: ByteArray(0)) + bytes
    }

    override suspend fun write(path: String, bytes: ByteArray) {
        files[path] = bytes
    }

    override fun delete(path: String) {
        files.remove(path)
    }
}

/**
 * A server that hands back the requested range from a synthetic file.
 *
 * [failAfterBytes] makes it throw partway, which is how an interruption is simulated.
 * [truncateAt] makes it return nothing from that offset, which is how a server that stops
 * early is simulated.
 */
class FakeTransport(
    private val totalBytes: Long,
    var failAfterBytes: Long? = null,
    var truncateAt: Long? = null,
) : DownloadTransport {

    val requestedRanges = mutableListOf<Pair<Long, Long>>()
    var subtitleFetches = 0
        private set

    override suspend fun fetchRange(partId: String, startByte: Long, endByte: Long): ByteArray {
        requestedRanges += startByte to endByte

        truncateAt?.let { if (startByte >= it) return ByteArray(0) }
        failAfterBytes?.let {
            if (startByte >= it) throw RuntimeException("connection lost at $startByte")
        }

        val end = minOf(endByte, totalBytes - 1)
        if (end < startByte) return ByteArray(0)
        val size = (end - startByte + 1).toInt()
        // Content is the byte offset modulo 251, so a wrongly assembled file is detectable.
        return ByteArray(size) { ((startByte + it) % 251).toByte() }
    }

    override suspend fun fetchSubtitle(streamId: String): ByteArray {
        subtitleFetches++
        return "1\n00:00:01,000 --> 00:00:02,000\nSubtitle\n".encodeToByteArray()
    }
}

class FakeNetwork(
    var metered: Boolean = false,
    var unmeteredOnly: Boolean = true,
) : NetworkConditions {
    override fun isMetered(): Boolean = metered
    override fun unmeteredOnly(): Boolean = unmeteredOnly
}

class FakePendingTimelineStore : PendingTimelineStore {
    val rows = mutableListOf<PendingTimelineRow>()
    private var nextId = 1L

    override suspend fun insert(row: PendingTimelineRow) {
        rows += row.copy(id = nextId++)
    }

    override suspend fun collapsed(): List<PendingTimelineRow> =
        rows.groupBy { it.ratingKey }
            .map { (_, entries) -> entries.maxBy { it.recordedAtMs } }
            .sortedBy { it.recordedAtMs }

    override suspend fun all(): List<PendingTimelineRow> = rows.sortedBy { it.recordedAtMs }

    override suspend fun count(): Long = rows.size.toLong()

    override suspend fun deleteUpTo(ratingKey: String, recordedAtMs: Long) {
        rows.removeAll { it.ratingKey == ratingKey && it.recordedAtMs <= recordedAtMs }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}
