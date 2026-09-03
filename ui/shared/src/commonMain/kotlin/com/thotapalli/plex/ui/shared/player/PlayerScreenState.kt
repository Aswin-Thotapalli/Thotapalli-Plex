package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Immutable
import com.thotapalli.plex.core.model.Chapter
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.playback.PlaybackQuality
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.core.playback.SubtitleStyle

/**
 * Everything the overlay draws.
 *
 * @Immutable: every property is a val and the instance is replaced wholesale on each emission,
 * never mutated in place. Marking it tells Compose the value is a safe skip key, so a child that is
 * handed the whole state but only reads a slice of it can be skipped when its inputs are unchanged —
 * without the annotation the function-typed fields below make Compose infer the class unstable and
 * recompose every reader on each 250 ms position tick.
 */
@Immutable
data class PlayerScreenState(
    val title: String = "",
    val subtitle: String? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,

    /** While scrubbing this is the drag position, not the engine's. */
    val scrubPositionMs: Long? = null,

    val controlsVisible: Boolean = true,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val openSheet: TrackSheetKind? = null,

    val showSkipIntro: Boolean = false,
    /** True while the credits marker is active — offers a manual "Skip Credits" button (never an
     *  automatic jump). See CLAUDE.md section 14 (per user: skip is an option, not automatic). */
    val showSkipCredits: Boolean = false,
    val showNextEpisodePrompt: Boolean = false,
    val nextEpisodeTitle: String? = null,
    val countdownSeconds: Int = 10,

    /** Whether an adjacent episode exists, so the transport can offer explicit previous/next. */
    val hasPreviousEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,

    val showTranscodingChip: Boolean = false,

    /** Set when playback has failed with no recovery left (e.g. the server or internet dropped
     *  mid-stream). The overlay shows this as a message with a way out, instead of a frozen frame
     *  or a crash. Null while playback is healthy. See CLAUDE.md section 10 (#1). */
    val errorMessage: String? = null,

    /** Windows only. See CLAUDE.md section 14 item 7. */
    val showFullScreenToggle: Boolean = false,
    val isFullScreen: Boolean = false,

    val trickplayUrlAt: (Long) -> String? = { null },

    // --- Wave 2 additions -----------------------------------------------------------------
    /** Current playback rate; the overlay's speed control reflects and sets it (#12). */
    val playbackSpeed: Float = 1f,
    /** Chapter marks for the scrubber + a chapter list, from the item's metadata (#15). */
    val chapters: List<Chapter> = emptyList(),
    /** Remaining time on the sleep timer, or null when off (#18). */
    val sleepTimerRemainingMs: Long? = null,
    /** Subtitle appearance the engine is applying; the appearance control edits it (#14). */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** The offered streaming qualities and the active one (#11). Empty when only original applies. */
    val qualities: List<PlaybackQuality> = emptyList(),
    val currentQualityLabel: String? = null,
    /** Upcoming items (the rest of the show / a built queue) for an Up Next panel (#19). */
    val upNext: List<MediaItem> = emptyList(),
) {
    val isPlaying: Boolean get() = playbackState is PlaybackState.Playing

    /** The position the overlay shows: the drag position while scrubbing, else the engine's. */
    val displayPositionMs: Long get() = scrubPositionMs ?: positionMs
}

enum class TrackSheetKind { AUDIO, SUBTITLE }

/** What the overlay can ask for. Stable: a bag of callbacks, none of which mutate after construction. */
@Immutable
data class PlayerActions(
    val onPlayPause: () -> Unit = {},
    /** Any tap or press on the picture — reveals the controls without changing playback. */
    val onUserInput: () -> Unit = {},
    /**
     * A single tap on the picture (touch): show the controls if hidden, hide them if shown. A tap
     * must never pause — pausing is only the transport button. See CLAUDE.md section 12 (overlay).
     */
    val onToggleControls: () -> Unit = {},
    val onSeekBack: () -> Unit = {},
    val onSeekForward: () -> Unit = {},
    /** Double-tap the right of the picture: jump forward ten seconds. */
    val onSeekForward10: () -> Unit = {},
    /**
     * Seek by a relative delta in milliseconds, clamped to the item's bounds. Drives the
     * television held-D-pad scrub of thirty seconds per 400 ms. See CLAUDE.md section 13.5.
     */
    val onSeekRelative: (Long) -> Unit = {},
    val onScrubStart: () -> Unit = {},
    val onScrub: (Long) -> Unit = {},
    val onScrubEnd: (Long) -> Unit = {},
    val onSkipIntro: () -> Unit = {},
    val onSkipCredits: () -> Unit = {},
    /** The auto-play route: the credit skip, the countdown and a natural end all fire this. */
    val onPlayNext: () -> Unit = {},
    /** The explicit transport control: play the previous episode now. */
    val onPlayPreviousEpisode: () -> Unit = {},
    /** The explicit transport control: play the next episode now, distinct from auto-play. */
    val onPlayNextEpisodeNow: () -> Unit = {},
    val onCancelAutoPlay: () -> Unit = {},
    val onOpenAudioTracks: () -> Unit = {},
    val onOpenSubtitleTracks: () -> Unit = {},
    val onSelectAudioTrack: (String?) -> Unit = {},
    val onSelectSubtitleTrack: (String?) -> Unit = {},
    val onDismissSheet: () -> Unit = {},
    val onToggleFullScreen: () -> Unit = {},
    val onBack: () -> Unit = {},

    // --- Wave 2 additions -----------------------------------------------------------------
    /** Set playback rate (0.75×–2×) (#12). */
    val onSetSpeed: (Float) -> Unit = {},
    /** Seek to an absolute position — used by chapter jumps and the chapter list (#15). */
    val onSeekToPosition: (Long) -> Unit = {},
    /** Arm the sleep timer for the given milliseconds, or null to cancel (#18). */
    val onSetSleepTimer: (Long?) -> Unit = {},
    /** Apply subtitle appearance live (#14). */
    val onSetSubtitleStyle: (com.thotapalli.plex.core.playback.SubtitleStyle) -> Unit = {},
    /** Switch streaming quality; reloads at the new cap at the same position (#11). */
    val onSelectQuality: (com.thotapalli.plex.core.playback.PlaybackQuality) -> Unit = {},
    /** Play a specific upcoming item from the Up Next panel (#19). */
    val onPlayUpNext: (com.thotapalli.plex.core.model.MediaItem) -> Unit = {},
)
