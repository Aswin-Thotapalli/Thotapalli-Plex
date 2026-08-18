package com.thotapalli.plex.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The download queue from CLAUDE.md section 11.
 *
 * 1. The original file, never a transcode.
 * 2. Ranged requests in 8 MB segments, so an interruption resumes from the received count.
 * 3. One item at a time, so the connection stays responsive for concurrent playback.
 * 4. External subtitles download separately.
 * 5. Received bytes verified against the server-reported size on completion.
 */
class DownloadQueue(
    private val store: DownloadStore,
    private val transport: DownloadTransport,
    private val files: DownloadFileSystem,
    private val network: NetworkConditions,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {

    private val _active = MutableStateFlow<ActiveDownload?>(null)
    val active: StateFlow<ActiveDownload?> = _active.asStateFlow()

    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null
    private var pausedByUser = mutableSetOf<String>()

    /**
     * Consecutive transport failures per item.
     *
     * Without this, a download whose connection keeps dropping is retried in a tight loop
     * for as long as the application runs, which hammers the server and never surfaces to
     * the viewer.
     */
    private val failedAttempts = mutableMapOf<String, Int>()

    /** Starts the serial worker. Idempotent. */
    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                val next = store.nextQueued()

                if (next == null) {
                    // Nothing to do. Sleep until something is enqueued or a network change
                    // makes a blocked item eligible.
                    wakeUp.receive()
                    continue
                }

                if (!network.isDownloadAllowed()) {
                    // "Download on unmetered networks only". The row stays queued rather
                    // than failing, so it resumes by itself when the network changes.
                    wakeUp.receive()
                    continue
                }

                runOne(next)
            }
        }
    }

    fun stop() {
        worker?.cancel()
        worker = null
    }

    /** Called on a network change so a queue blocked on metered data wakes up. */
    fun onNetworkChanged() {
        wakeUp.trySend(Unit)
    }

    /**
     * Queues an item.
     *
     * The direct file URL, never a transcode: a downloaded copy that has been re-encoded
     * is not the library's copy. See CLAUDE.md section 11 rule 1.
     */
    suspend fun enqueue(request: DownloadRequest) {
        store.insert(
            DownloadRow(
                ratingKey = request.ratingKey,
                partId = request.partId,
                localPath = files.pathFor(request.ratingKey, request.container),
                totalBytes = request.sizeBytes,
                receivedBytes = files.sizeOf(files.pathFor(request.ratingKey, request.container)),
                state = DownloadState.QUEUED,
                queuedAtMs = nowMs(),
            ),
        )
        request.subtitles.forEach { store.insertSubtitle(request.ratingKey, it) }
        pausedByUser.remove(request.ratingKey)
        wakeUp.trySend(Unit)
    }

    suspend fun pause(ratingKey: String) {
        pausedByUser += ratingKey
        store.updateState(ratingKey, DownloadState.PAUSED)
        if (_active.value?.ratingKey == ratingKey) _active.value = null
    }

    /**
     * Puts a paused or failed item back in the queue.
     *
     * The attempt count is cleared, because the viewer asking again is a new decision, and
     * the partial file is left alone so this resumes rather than starts over.
     */
    suspend fun resume(ratingKey: String) {
        pausedByUser -= ratingKey
        failedAttempts.remove(ratingKey)
        store.updateState(ratingKey, DownloadState.QUEUED)
        wakeUp.trySend(Unit)
    }

    /** Deletes the row, the file and any sidecar subtitles. */
    suspend fun delete(ratingKey: String) {
        pausedByUser -= ratingKey
        store.byRatingKey(ratingKey)?.let { files.delete(it.localPath) }
        store.subtitlesFor(ratingKey).forEach { files.delete(it.localPath) }
        store.deleteSubtitles(ratingKey)
        store.delete(ratingKey)
        if (_active.value?.ratingKey == ratingKey) _active.value = null
    }

    private suspend fun runOne(row: DownloadRow) {
        if (row.ratingKey in pausedByUser) {
            store.updateState(row.ratingKey, DownloadState.PAUSED)
            return
        }

        store.updateState(row.ratingKey, DownloadState.RUNNING)

        // Resume point. An interrupted download continues from the bytes already on disk
        // rather than starting again. See CLAUDE.md section 11 rule 2.
        var received = files.sizeOf(row.localPath)
        if (received > row.totalBytes) {
            // Longer than the server says it is: the file on disk is not this file.
            files.delete(row.localPath)
            received = 0
        }
        store.updateProgress(row.ratingKey, received)

        _active.value = ActiveDownload(row.ratingKey, received, row.totalBytes)

        try {
            while (received < row.totalBytes) {
                if (row.ratingKey in pausedByUser) {
                    store.updateState(row.ratingKey, DownloadState.PAUSED)
                    _active.value = null
                    return
                }
                if (!network.isDownloadAllowed()) {
                    store.updateState(row.ratingKey, DownloadState.QUEUED)
                    _active.value = null
                    return
                }

                val end = minOf(received + SEGMENT_BYTES, row.totalBytes) - 1
                val segment = transport.fetchRange(row.partId, received, end)

                files.append(row.localPath, segment)
                received += segment.size
                store.updateProgress(row.ratingKey, received)
                _active.value = ActiveDownload(row.ratingKey, received, row.totalBytes)

                if (segment.isEmpty()) {
                    // The server stopped supplying bytes before the expected size. Treating
                    // this as complete would leave a truncated file that plays and then cuts
                    // off partway, which is worse than a visible failure.
                    fail(row, "server stopped at $received of ${row.totalBytes} bytes")
                    return
                }
            }

            // Verify on completion. A mismatch marks the row failed and deletes the partial
            // file. See CLAUDE.md section 11 rule 5.
            val onDisk = files.sizeOf(row.localPath)
            if (onDisk != row.totalBytes) {
                fail(row, "size mismatch: $onDisk on disk, ${row.totalBytes} reported")
                return
            }

            downloadSubtitles(row.ratingKey)

            failedAttempts.remove(row.ratingKey)
            store.updateStateAndProgress(row.ratingKey, DownloadState.COMPLETED, onDisk)
            _active.value = null
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Shutting down. The row stays queued and the bytes stay on disk.
            store.updateState(row.ratingKey, DownloadState.QUEUED)
            _active.value = null
            throw kotlinx.coroutines.CancellationException()
        } catch (error: Exception) {
            // A transport failure always keeps the partial file, because the whole point of
            // ranged requests is that the next attempt resumes rather than starts again.
            _active.value = null
            lastError = error

            val attempts = (failedAttempts[row.ratingKey] ?: 0) + 1
            failedAttempts[row.ratingKey] = attempts

            if (attempts >= MAX_ATTEMPTS) {
                // Stop retrying and let the viewer see it. The bytes stay on disk, so
                // resume() picks up where this left off rather than starting again.
                store.updateState(row.ratingKey, DownloadState.FAILED)
            } else {
                store.updateState(row.ratingKey, DownloadState.QUEUED)
                delay(RETRY_BACKOFF_MS * attempts)
            }
        }
    }

    /** External subtitles download separately. Embedded tracks need no action. */
    private suspend fun downloadSubtitles(ratingKey: String) {
        store.subtitlesFor(ratingKey).forEach { subtitle ->
            runCatching {
                val bytes = transport.fetchSubtitle(subtitle.streamId)
                files.write(subtitle.localPath, bytes)
            }
            // A missing sidecar is not worth failing the video for; the embedded tracks and
            // the video itself are still usable.
        }
    }

    private suspend fun fail(row: DownloadRow, reason: String) {
        files.delete(row.localPath)
        store.updateStateAndProgress(row.ratingKey, DownloadState.FAILED, 0)
        _active.value = null
        lastError = IllegalStateException(reason)
    }

    var lastError: Throwable? = null
        private set

    companion object {
        /** 8 MB segments, per CLAUDE.md section 11 rule 2. */
        const val SEGMENT_BYTES = 8L * 1024 * 1024

        /** Consecutive transport failures before an item is left for the viewer to retry. */
        const val MAX_ATTEMPTS = 3

        const val RETRY_BACKOFF_MS = 5_000L
    }
}

data class DownloadRequest(
    val ratingKey: String,
    val partId: String,
    val container: String,
    val sizeBytes: Long,
    val subtitles: List<SubtitleRequest> = emptyList(),
)

data class SubtitleRequest(
    val streamId: String,
    val language: String,
    val localPath: String,
)

data class ActiveDownload(
    val ratingKey: String,
    val receivedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class DownloadRow(
    val ratingKey: String,
    val partId: String,
    val localPath: String,
    val totalBytes: Long,
    val receivedBytes: Long,
    val state: DownloadState,
    val queuedAtMs: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class SubtitleRow(
    val ratingKey: String,
    val streamId: String,
    val language: String,
    val localPath: String,
)

enum class DownloadState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

/** The database side of the queue. Backed by SQLDelight in core/data. */
interface DownloadStore {
    suspend fun insert(row: DownloadRow)
    suspend fun insertSubtitle(ratingKey: String, subtitle: SubtitleRequest)
    suspend fun nextQueued(): DownloadRow?
    suspend fun byRatingKey(ratingKey: String): DownloadRow?
    suspend fun all(): List<DownloadRow>
    suspend fun completed(): List<DownloadRow>
    suspend fun subtitlesFor(ratingKey: String): List<SubtitleRow>
    suspend fun updateProgress(ratingKey: String, receivedBytes: Long)
    suspend fun updateState(ratingKey: String, state: DownloadState)
    suspend fun updateStateAndProgress(ratingKey: String, state: DownloadState, receivedBytes: Long)
    suspend fun delete(ratingKey: String)
    suspend fun deleteSubtitles(ratingKey: String)
    suspend fun totalBytesOnDisk(): Long
}

/** Ranged reads from the server. */
interface DownloadTransport {
    /** Inclusive byte range, as an HTTP Range header expresses it. */
    suspend fun fetchRange(partId: String, startByte: Long, endByte: Long): ByteArray
    suspend fun fetchSubtitle(streamId: String): ByteArray
}

/** Where downloads live. See CLAUDE.md section 11 for the per-target locations. */
interface DownloadFileSystem {
    fun pathFor(ratingKey: String, container: String): String
    fun subtitlePathFor(ratingKey: String, streamId: String, language: String): String
    fun sizeOf(path: String): Long
    fun exists(path: String): Boolean
    suspend fun append(path: String, bytes: ByteArray)
    suspend fun write(path: String, bytes: ByteArray)
    fun delete(path: String)
}

/**
 * Whether downloading is allowed right now.
 *
 * "Download on unmetered networks only", defaulting on for Android and off for Windows.
 * See CLAUDE.md section 11 rule 6.
 */
interface NetworkConditions {
    fun isMetered(): Boolean
    fun unmeteredOnly(): Boolean
    fun isDownloadAllowed(): Boolean = !unmeteredOnly() || !isMetered()
}
