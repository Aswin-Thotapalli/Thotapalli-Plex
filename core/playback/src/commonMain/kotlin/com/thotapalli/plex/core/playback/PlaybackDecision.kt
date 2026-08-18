package com.thotapalli.plex.core.playback

/**
 * The capability parameters sent to the decision endpoint, and the fallback chain that
 * follows a failed direct play. See CLAUDE.md section 10.
 */
object PlaybackCapabilities {

    val containers = listOf("mkv", "mp4", "mov", "avi", "ts", "m2ts", "webm")

    val video = listOf("h264", "hevc", "av1", "vp9", "mpeg2video", "vc1")

    val audio = listOf("aac", "ac3", "eac3", "dts", "truehd", "flac", "mp3", "opus", "vorbis", "pcm")

    val subtitles = listOf("srt", "ass", "ssa", "pgs", "vobsub", "dvb_subtitle")

    /** Formats that only survive intact through bitstream passthrough. */
    private val passthroughOnly = setOf("ac3", "eac3", "dts", "truehd")

    /**
     * The audio list, narrowed when the device reports no passthrough support.
     *
     * Claiming a format the device cannot bitstream makes the server send it untouched and
     * the device then fails to decode it, which is worse than asking for a transcode up
     * front. See CLAUDE.md section 10 and section 18 point 3.
     */
    fun audioFor(supportsPassthrough: Boolean): List<String> =
        if (supportsPassthrough) audio else audio.filterNot { it in passthroughOnly }
}

/**
 * Chooses what to try next after a direct play fails.
 *
 * The order is fixed by CLAUDE.md section 10: direct play, then on Android phone and
 * tablet one retry through libmpv, then a silent transcode.
 */
class PlaybackFallbackChain(
    /** Only Android phone and tablet has a second engine to retry through. */
    private val hasSecondaryEngine: Boolean,
) {

    private var attempt: Attempt = Attempt.DIRECT_PRIMARY

    fun current(): Attempt = attempt

    fun reset() {
        attempt = Attempt.DIRECT_PRIMARY
    }

    /**
     * Returns the next thing to try, or null when there is nothing left.
     *
     * A failure that a transcode cannot fix, such as the network being down or the file
     * being gone, ends the chain rather than burning the server's transcoder on it.
     */
    fun next(failure: PlaybackFailure): Attempt? {
        if (!failure.warrantsTranscode) return null

        attempt = when (attempt) {
            Attempt.DIRECT_PRIMARY ->
                if (hasSecondaryEngine) Attempt.DIRECT_SECONDARY else Attempt.TRANSCODE
            Attempt.DIRECT_SECONDARY -> Attempt.TRANSCODE
            Attempt.TRANSCODE -> return null
        }
        return attempt
    }

    enum class Attempt {
        /** Media3 on Android, libmpv on Windows. */
        DIRECT_PRIMARY,

        /** libmpv on Android phone and tablet, before any transcode. */
        DIRECT_SECONDARY,

        /** The server's HLS stream. Silent, with a chip. */
        TRANSCODE,
        ;

        val mode: PlaybackMode
            get() = if (this == TRANSCODE) PlaybackMode.TRANSCODE else PlaybackMode.DIRECT
    }
}
