package com.thotapalli.plex.core.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * The shared player interface from CLAUDE.md section 8.
 *
 * Implemented by Media3 on Android and by libmpv on Windows. Nothing above this interface
 * knows which is running, which is what lets the transcode fallback in section 10 retry a
 * failed Media3 direct play through libmpv without the calling screen changing.
 */
interface PlayerEngine {
    val state: StateFlow<PlaybackState>
    val positionMs: StateFlow<Long>

    fun load(source: PlaybackSource, startAtMs: Long)
    fun play()
    fun pause()
    fun seekTo(ms: Long)

    /**
     * Called on seek bar drag start and drag end.
     *
     * While scrubbing, the engine decodes only what it needs to show a frame at the new
     * position rather than trying to play from every intermediate one.
     */
    fun setScrubbing(active: Boolean)

    fun selectAudioTrack(id: String)
    fun selectSubtitleTrack(id: String?)
    fun release()
}

data class PlaybackSource(
    val uri: String,
    val mode: PlaybackMode,
    val headers: Map<String, String>,
    /** Drives the refresh rate match in CLAUDE.md section 9. Null means no mode change. */
    val frameRate: Float?,
)

enum class PlaybackMode {
    /** The original file, straight from the server. Always preferred. */
    DIRECT,

    /** The server's HLS stream. A silent fallback, never a first choice. */
    TRANSCODE,
}

/**
 * What the engine is doing.
 *
 * [Failed] carries the reason so the fallback chain in section 10 can tell a decoder
 * problem, which a transcode fixes, from a network problem, which it does not.
 */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Buffering : PlaybackState
    data object Ready : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data object Ended : PlaybackState
    data class Failed(val reason: PlaybackFailure, val cause: Throwable?) : PlaybackState

    val isActive: Boolean get() = this is Playing || this is Paused || this is Buffering || this is Ready
}

/** Only the first three warrant a transcode. See CLAUDE.md section 10. */
enum class PlaybackFailure {
    DECODER_INITIALISATION,
    UNSUPPORTED_TRACK,

    /** No frame rendered within 8000 ms. */
    NO_FIRST_FRAME,

    NETWORK,
    SOURCE_NOT_FOUND,
    UNKNOWN,
    ;

    /** A transcode replaces the decoder's problem with the server's. It fixes only these. */
    val warrantsTranscode: Boolean
        get() = this == DECODER_INITIALISATION || this == UNSUPPORTED_TRACK || this == NO_FIRST_FRAME
}

/** A selectable track, as the player overlay lists it. */
data class PlayerTrack(
    val id: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
)

/** Tracks the engine is currently offering. */
data class PlayerTracks(
    val audio: List<PlayerTrack> = emptyList(),
    val subtitle: List<PlayerTrack> = emptyList(),
)

/** No frame within this long is treated as a direct play failure. See CLAUDE.md section 10. */
const val FIRST_FRAME_TIMEOUT_MS = 8_000L
