package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.model.MediaPart
import com.thotapalli.plex.core.model.SubtitleStream
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CLAUDE.md section 11 rules 1 and 2: the original file, fetched in ranges so an
 * interruption resumes.
 *
 * The 200 cases are the ones that matter. A server that answers a ranged request with the
 * whole file is not an error the HTTP layer reports, so nothing but this class stands
 * between it and a file assembled out of the wrong bytes.
 */
class RangedDownloadSourceTest {

    private val scope = ServerScope("https://server.example:32400", "server-token")
    private val headers = PlexHeaders { mapOf(PlexHeaderNames.CLIENT_IDENTIFIER to "client-1") }

    private val part = MediaPart(
        partId = "88001",
        fileKey = "/library/parts/88001/1700000100/file.mkv",
        container = "mkv",
        sizeBytes = 20L * 1024 * 1024,
        durationMs = 7_200_000,
        videoStreams = emptyList(),
        audioStreams = emptyList(),
        subtitleStreams = listOf(
            subtitle(id = "99004", external = true, key = "/library/streams/99004"),
            subtitle(id = "99005", external = false, key = null),
        ),
    )

    private val recorded = mutableListOf<HttpRequestData>()

    private fun source(
        keys: DownloadKeys,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): RangedDownloadSource {
        val client = HttpClient(
            MockEngine { request ->
                recorded += request
                handler(request)
            },
        ) {
            expectSuccess = false
        }
        return RangedDownloadSource(PlexHttp(client), headers, scope, keys)
    }

    private suspend fun keys(): RecordedDownloadKeys =
        RecordedDownloadKeys().apply { record(part) }

    private fun body(from: Int, count: Int): ByteArray =
        ByteArray(count) { ((from + it) % 251).toByte() }

    // --- the ordinary case ----------------------------------------------------------------

    @Test
    fun aPartialContentResponseYieldsExactlyTheRequestedSegment() = runTest {
        val transport = source(keys()) {
            respond(
                content = body(100, 100),
                status = HttpStatusCode.PartialContent,
                headers = headersOf(HttpHeaders.ContentRange, "bytes 100-199/20971520"),
            )
        }

        val segment = transport.fetchRange("88001", 100, 199)

        assertEquals(100, segment.size)
        assertEquals(body(100, 100).toList(), segment.toList())
    }

    @Test
    fun theRangeHeaderIsInclusiveAndCarriesTheServerToken() = runTest {
        val transport = source(keys()) {
            respond(body(0, 8), HttpStatusCode.PartialContent)
        }

        transport.fetchRange("88001", 0, 7)

        val request = recorded.single()
        // Inclusive on both ends, which is how the queue counts its 8 MB segments.
        assertEquals("bytes=0-7", request.headers[HttpHeaders.Range])
        assertEquals("server-token", request.headers[PlexHeaderNames.TOKEN])
        assertEquals("client-1", request.headers[PlexHeaderNames.CLIENT_IDENTIFIER])
    }

    @Test
    fun theUrlIsTheOriginalFileAndNeverATranscode() = runTest {
        val transport = source(keys()) {
            respond(body(0, 8), HttpStatusCode.PartialContent)
        }

        transport.fetchRange("88001", 0, 7)

        val url = recorded.single().url.toString()
        assertContains(url, "/library/parts/88001/1700000100/file.mkv")
        assertFalse(url.contains("transcode"), "a download must never be a transcode")
    }

    // --- a server that ignores the range header ---------------------------------------------

    @Test
    fun aServerThatIgnoresTheRangeAtTheStartOfTheFileYieldsOnlyTheSegment() = runTest {
        // 200 with the whole file rather than 206 with the segment. From byte zero the
        // leading bytes are still the right bytes, so the segment is taken and the rest of
        // the stream dropped rather than held in memory.
        val transport = source(keys()) {
            respond(body(0, 500), HttpStatusCode.OK)
        }

        val segment = transport.fetchRange("88001", 0, 99)

        assertEquals(100, segment.size)
        assertEquals(body(0, 100).toList(), segment.toList())
    }

    @Test
    fun aServerThatIgnoresTheRangePartWayThroughIsRefusedRatherThanCorruptingTheFile() = runTest {
        val transport = source(keys()) {
            respond(body(0, 500), HttpStatusCode.OK)
        }

        // The body starts at byte zero, and the queue would append it at byte 100. That
        // splices the start of the file into its middle, and the size check on completion
        // would not catch it because the byte count is right.
        assertFailsWith<RangeNotSupportedException> { transport.fetchRange("88001", 100, 199) }
    }

    @Test
    fun aPartialContentResponseFromTheWrongOffsetIsRefused() = runTest {
        val transport = source(keys()) {
            respond(
                content = body(0, 100),
                status = HttpStatusCode.PartialContent,
                headers = headersOf(HttpHeaders.ContentRange, "bytes 0-99/20971520"),
            )
        }

        assertFailsWith<RangeNotSupportedException> { transport.fetchRange("88001", 100, 199) }
    }

    @Test
    fun aPartialContentResponseWithNoContentRangeIsTakenAtItsWord() = runTest {
        // Nothing to check against, and a 206 is an explicit statement that the range was
        // honoured, so this is the one case where the offset is trusted.
        val transport = source(keys()) {
            respond(body(100, 100), HttpStatusCode.PartialContent)
        }

        assertEquals(100, transport.fetchRange("88001", 100, 199).size)
    }

    // --- short and failed responses -----------------------------------------------------------

    @Test
    fun aResponseShorterThanTheRangeYieldsWhatArrived() = runTest {
        // Not an error here: the queue advances by the count it is handed and asks for the
        // next range from there.
        val transport = source(keys()) {
            respond(body(0, 40), HttpStatusCode.PartialContent)
        }

        assertEquals(40, transport.fetchRange("88001", 0, 99).size)
    }

    @Test
    fun aServerErrorSurfacesAsAPlexApiException() = runTest {
        val transport = source(keys()) { respondError(HttpStatusCode.InternalServerError) }

        val error = assertFailsWith<PlexApiException> { transport.fetchRange("88001", 0, 99) }
        assertEquals(500, error.status)
    }

    @Test
    fun anEmptyRangeIsRejectedBeforeAnyRequestIsMade() = runTest {
        val transport = source(keys()) { respond(ByteArray(0), HttpStatusCode.PartialContent) }

        assertFailsWith<IllegalArgumentException> { transport.fetchRange("88001", 100, 99) }
        assertTrue(recorded.isEmpty())
    }

    // --- subtitles ------------------------------------------------------------------------------

    @Test
    fun aSubtitleIsFetchedWholeFromItsRecordedStreamKey() = runTest {
        val content = "1\n00:00:01,000 --> 00:00:02,000\nSubtitle\n"
        val transport = source(keys()) { respond(content.encodeToByteArray(), HttpStatusCode.OK) }

        val bytes = transport.fetchSubtitle("99004")

        assertEquals(content, bytes.decodeToString())
        assertContains(recorded.single().url.toString(), "/library/streams/99004")
    }

    @Test
    fun anUnrecordedPartOrStreamFailsRatherThanGuessingAUrl() = runTest {
        val transport = source(keys()) { respond(ByteArray(0), HttpStatusCode.PartialContent) }

        assertFailsWith<UnknownDownloadKeyException> { transport.fetchRange("70000", 0, 99) }
        assertFailsWith<UnknownDownloadKeyException> { transport.fetchSubtitle("70000") }
        assertTrue(recorded.isEmpty(), "a guessed URL would download the wrong file")
    }

    @Test
    fun onlyExternalSubtitleStreamsAreRecorded() = runTest {
        val keys = keys()

        // Embedded tracks travel inside the file, so there is nothing to fetch for them.
        assertEquals("/library/streams/99004", keys.subtitleKey("99004"))
        assertNull(keys.subtitleKey("99005"))
        assertEquals("/library/parts/88001/1700000100/file.mkv", keys.partKey("88001"))
    }

    private fun subtitle(id: String, external: Boolean, key: String?) = SubtitleStream(
        id = id,
        codec = "srt",
        language = "English",
        languageCode = "eng",
        title = null,
        forced = false,
        default = false,
        external = external,
        key = key,
    )
}
