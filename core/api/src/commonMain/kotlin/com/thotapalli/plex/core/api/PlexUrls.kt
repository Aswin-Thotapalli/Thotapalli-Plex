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
