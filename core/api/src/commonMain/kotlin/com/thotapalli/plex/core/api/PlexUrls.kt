package com.thotapalli.plex.core.api

import io.ktor.http.encodeURLParameter

/**
 * Builds the server URLs from CLAUDE.md section 5.
 *
 * Every one of these carries the server access token as a query parameter rather than a
 * header, because they are handed to an image loader and to a media player, neither of
 * which sends this client's headers.
 */
class PlexUrls(
    private val baseUri: String,
    private val accessToken: String,
) {

    private val base = baseUri.trimEnd('/')

    /**
     * Artwork, resized by the server.
     *
     * Asking the server to transcode is not an optimisation, it is the difference between
     * a poster grid pulling a few kilobytes per tile and pulling full size art.
     */
    fun artwork(path: String?, width: Int, height: Int): String? {
        if (path.isNullOrBlank()) return null
        // Ask the server to resize the image to the display size. This keeps each request small and,
        // crucially, clean-scaling: serving the full stored image and letting the client downscale
        // it looks over-sharp/aliased ("weirdly sharp") and is heavy to download and decode for a
        // whole grid. The server transcode returns exactly the pixels the tile shows. See §5.
        val inner = "$base$path?X-Plex-Token=${accessToken.encodeURLParameter()}"
        return "$base/photo/:/transcode" +
            "?width=$width" +
            "&height=$height" +
            "&minSize=1" +
            "&upscale=1" +
            "&url=${inner.encodeURLParameter()}" +
            "&X-Plex-Token=${accessToken.encodeURLParameter()}"
    }

    /** The original file, for direct play and for downloads. Never a transcode. */
    fun directFile(partId: String, updatedAt: Long, container: String): String =
        "$base/library/parts/$partId/$updatedAt/file.$container" +
            "?X-Plex-Token=${accessToken.encodeURLParameter()}"

    /** The part key exactly as the server reported it, which already includes the path. */
    fun partKey(key: String): String =
        "$base$key?X-Plex-Token=${accessToken.encodeURLParameter()}"

    /**
     * The universal transcode HLS stream, the silent fallback when direct play fails.
     * See CLAUDE.md section 10.
     *
     * With no [maxVideoBitrateKbps] the server is asked to remux where it can
     * (`directStream=1`), which preserves the original video untouched — the current default
     * behaviour. Passing a cap turns remux off (`directStream=0`) so the server actually
     * transcodes the video down to the ceiling, and pins `videoQuality=100` so the only thing
     * constraining quality is the bitrate cap. This is what a constrained remote connection
     * uses. See CLAUDE.md section 11.
     */
    fun transcodeStream(
        ratingKey: String,
        startAtMs: Long,
        sessionIdentifier: String,
        maxVideoBitrateKbps: Int? = null,
    ): String {
        val capped = maxVideoBitrateKbps != null
        val path = buildString {
            append("/video/:/transcode/universal/start.m3u8")
            append("?path=%2Flibrary%2Fmetadata%2F").append(ratingKey)
            append("&mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1")
            append("&offset=").append(startAtMs / 1000)
            append("&directPlay=0")
            // Remux stays on only when no cap is asked for; a cap forces a real transcode.
            append("&directStream=").append(if (capped) 0 else 1)
            append("&subtitles=burn")
            if (capped) {
                append("&maxVideoBitrate=").append(maxVideoBitrateKbps)
                append("&videoQuality=100")
            }
            append("&X-Plex-Session-Identifier=").append(sessionIdentifier)
        }
        return withToken(path)
    }

    /** A trickplay thumbnail for the seek preview. */
    fun trickplay(partId: String, offsetMs: Long): String =
        "$base/library/parts/$partId/indexes/sd/$offsetMs" +
            "?X-Plex-Token=${accessToken.encodeURLParameter()}"

    /** An external subtitle stream, downloaded separately from the video. */
    fun subtitle(streamKey: String): String =
        if (streamKey.startsWith("http")) streamKey
        else "$base$streamKey?X-Plex-Token=${accessToken.encodeURLParameter()}"

    fun withToken(path: String): String {
        val separator = if (path.contains('?')) "&" else "?"
        return "$base$path${separator}X-Plex-Token=${accessToken.encodeURLParameter()}"
    }
}
