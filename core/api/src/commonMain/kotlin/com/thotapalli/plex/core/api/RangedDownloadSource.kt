package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.download.DownloadTransport
import com.thotapalli.plex.core.model.MediaPart
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The server side of the download queue: ranged reads of the original file.
 *
 * Lives in core/api because it speaks HTTP, and Ktor is confined to this module. Nothing
 * about Ktor appears in the signatures below, so core/download stays free of it exactly as
 * [PlexServerApi] keeps it out of core/data.
 *
 * The URL is always the direct file, never a transcode. A downloaded copy that has been
 * re-encoded is not the library's copy. See CLAUDE.md section 11 rule 1.
 */
class RangedDownloadSource(
    http: PlexHttp,
    private val identityHeaders: PlexHeaders,
    private val scope: ServerScope,
    private val keys: DownloadKeys,
) : DownloadTransport {

    private val client = http.client
    private val urls = PlexUrls(scope.baseUri, scope.accessToken)

    /**
     * One 8 MB segment, as an inclusive byte range.
     *
     * The response is read to the requested length and no further, so a server that ignores
     * the range and starts streaming the whole file cannot pull a 30 GB movie into memory.
     */
    override suspend fun fetchRange(partId: String, startByte: Long, endByte: Long): ByteArray {
        require(startByte >= 0) { "range start $startByte is negative" }
        require(endByte >= startByte) { "range $startByte-$endByte is empty" }

        val key = keys.partKey(partId)
            ?: throw UnknownDownloadKeyException("no part key recorded for part $partId")
        val expected = endByte - startByte + 1

        return client.prepareGet(urls.partKey(key)) {
            applyIdentity(this)
            header(HttpHeaders.Range, "bytes=$startByte-$endByte")
        }.execute { response ->
            when (response.status) {
                HttpStatusCode.PartialContent -> {
                    // A 206 that starts somewhere other than where we asked would be spliced
                    // into the file at the wrong offset and corrupt it silently, so the
                    // offset the server reports is checked rather than trusted.
                    val reported = contentRangeStart(response.headers[HttpHeaders.ContentRange])
                    if (reported != null && reported != startByte) {
                        throw RangeNotSupportedException(
                            "asked for bytes from $startByte, server answered from $reported",
                        )
                    }
                    response.readAtMost(expected)
                }

                HttpStatusCode.OK -> {
                    // 200 means the Range header was ignored and the body is the whole file
                    // from byte zero. Appending that at a non-zero offset would splice the
                    // start of the file into the middle of it, so this fails loudly instead.
                    // At offset zero the leading bytes are the right bytes, so the segment is
                    // taken and the stream is dropped.
                    if (startByte > 0) {
                        throw RangeNotSupportedException(
                            "server ignored the range header and answered 200 for bytes " +
                                "$startByte-$endByte; resuming would corrupt the file",
                        )
                    }
                    response.readAtMost(expected)
                }

                else -> {
                    response.requireSuccess("download part $partId")
                    // A success that is neither 200 nor 206 carries no body worth appending.
                    // Returning nothing lets the queue treat it as a server that stopped
                    // early, which fails the row rather than truncating the file.
                    ByteArray(0)
                }
            }
        }
    }

    /**
     * An external subtitle, read whole.
     *
     * Sidecars are kilobytes rather than gigabytes, so they need neither ranges nor resume.
     */
    override suspend fun fetchSubtitle(streamId: String): ByteArray {
        val key = keys.subtitleKey(streamId)
            ?: throw UnknownDownloadKeyException("no stream key recorded for subtitle $streamId")

        val response = client.get(urls.subtitle(key)) { applyIdentity(this) }
        response.requireSuccess("subtitle $streamId")
        return response.readRawBytes()
    }

    /**
     * Reads at most [count] bytes and abandons the rest.
     *
     * A short read is not an error here: the queue advances by the number of bytes it was
     * handed and asks for the next range from there.
     */
    private suspend fun HttpResponse.readAtMost(count: Long): ByteArray {
        val limit = count.toInt()
        val buffer = ByteArray(limit)
        val channel = bodyAsChannel()

        var filled = 0
        while (filled < limit) {
            val read = channel.readAvailable(buffer, filled, limit - filled)
            if (read <= 0) break
            filled += read
        }

        return if (filled == limit) buffer else buffer.copyOf(filled)
    }

    private suspend fun applyIdentity(builder: HttpRequestBuilder) {
        val identity = identityHeaders.headers()
        builder.headers {
            identity.forEach { (name, value) -> append(name, value) }
            append(PlexHeaderNames.TOKEN, scope.accessToken)
        }
    }

    private companion object {
        /** `bytes 8388608-16777215/20971520` reduced to its first number. */
        fun contentRangeStart(value: String?): Long? = value
            ?.substringAfter("bytes ", "")
            ?.substringBefore('/')
            ?.substringBefore('-')
            ?.trim()
            ?.toLongOrNull()
    }
}

/**
 * Resolves the server paths behind the identifiers the queue carries.
 *
 * The download schema in CLAUDE.md section 7 stores a part id and a subtitle stream id,
 * which is what [DownloadTransport] is handed. Neither is enough to build a URL on its own:
 * the direct file path also carries the part's `updatedAt` stamp, and a subtitle stream has
 * a key of its own. Rather than widen the transport interface, which the queue's tests
 * specify, the mapping is supplied separately by whoever enqueued the item.
 */
interface DownloadKeys {

    /** The part key exactly as the server reported it, or null if it is not known. */
    suspend fun partKey(partId: String): String?

    /** The external subtitle stream key, or null if it is not known. */
    suspend fun subtitleKey(streamId: String): String?
}

/**
 * Remembers the keys of everything enqueued in this session.
 *
 * Populated with [record] at enqueue time, when the caller still has the [MediaPart] in
 * hand. A queue restarted from the database with no prior [record] cannot resolve its rows,
 * which surfaces as [UnknownDownloadKeyException] rather than a wrong URL; the coordinator
 * re-records from metadata before restarting the queue.
 */
class RecordedDownloadKeys : DownloadKeys {

    // The queue runs on its own coroutine and record() is called from the interface, so the
    // two genuinely do touch these maps from different places.
    private val guard = Mutex()
    private val parts = mutableMapOf<String, String>()
    private val subtitles = mutableMapOf<String, String>()

    /** Records the file key, and the key of every external subtitle, for one part. */
    suspend fun record(part: MediaPart) {
        guard.withLock {
            parts[part.partId] = part.fileKey
            part.subtitleStreams
                // Embedded tracks travel inside the file and need no action of their own.
                .filter { it.external }
                .forEach { stream -> stream.key?.let { subtitles[stream.id] = it } }
        }
    }

    override suspend fun partKey(partId: String): String? = guard.withLock { parts[partId] }

    override suspend fun subtitleKey(streamId: String): String? =
        guard.withLock { subtitles[streamId] }
}

/**
 * The server answered without honouring the byte range.
 *
 * Separate from [PlexApiException] because the status was a success: the failure is that
 * the bytes on the wire are not the bytes that were asked for.
 */
class RangeNotSupportedException(message: String) : Exception(message)

/** A download row referred to a part or stream whose server key was never recorded. */
class UnknownDownloadKeyException(message: String) : Exception(message)
