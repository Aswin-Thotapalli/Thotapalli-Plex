package com.thotapalli.plex.core.model

/** One physical file behind an item, and the streams inside it. */
data class MediaPart(
    val partId: String,
    val fileKey: String,
    val container: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val subtitleStreams: List<SubtitleStream>,
) {
    /**
     * The content frame rate, which drives the refresh rate match in CLAUDE.md section 9.
     * Null when the server did not report one, in which case no mode change is attempted.
     */
    val frameRate: Float? get() = videoStreams.firstOrNull()?.frameRate
}

data class VideoStream(
    val id: String,
    val codec: String,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    val bitDepth: Int?,
    val hdr: Boolean,
    val title: String?,
)

data class AudioStream(
    val id: String,
    val codec: String,
    val channels: Int,
    val language: String?,
    val languageCode: String?,
    val title: String?,
    val default: Boolean,
) {
    /** Formats that only reach a receiver intact through bitstream passthrough. */
    val bitstreamOnly: Boolean
        get() = codec.lowercase() in setOf("truehd", "dts", "dca", "eac3", "ac3")
}

data class SubtitleStream(
    val id: String,
    val codec: String,
    val language: String?,
    val languageCode: String?,
    val title: String?,
    val forced: Boolean,
    val default: Boolean,
    /** External subtitles download separately. Embedded tracks need no action. */
    val external: Boolean,
    val key: String?,
)
