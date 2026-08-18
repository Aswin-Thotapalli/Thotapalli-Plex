package com.thotapalli.plex.ui.shared.player

import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerTrack

/** Everything the overlay draws. */
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
    val showNextEpisodePrompt: Boolean = false,
    val nextEpisodeTitle: String? = null,
    val countdownSeconds: Int = 10,

    val showTranscodingChip: Boolean = false,

    /** Windows only. See CLAUDE.md section 14 item 7. */
    val showFullScreenToggle: Boolean = false,
    val isFullScreen: Boolean = false,

    val trickplayUrlAt: (Long) -> String? = { null },
) {
    val isPlaying: Boolean get() = playbackState is PlaybackState.Playing

    /** The position the overlay shows: the drag position while scrubbing, else the engine's. */
    val displayPositionMs: Long get() = scrubPositionMs ?: positionMs
}

enum class TrackSheetKind { AUDIO, SUBTITLE }

/** What the overlay can ask for. */
data class PlayerActions(
    val onPlayPause: () -> Unit = {},
    val onSeekBack: () -> Unit = {},
    val onSeekForward: () -> Unit = {},
    val onScrubStart: () -> Unit = {},
    val onScrub: (Long) -> Unit = {},
    val onScrubEnd: (Long) -> Unit = {},
    val onSkipIntro: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
    val onCancelAutoPlay: () -> Unit = {},
    val onOpenAudioTracks: () -> Unit = {},
    val onOpenSubtitleTracks: () -> Unit = {},
    val onSelectAudioTrack: (String?) -> Unit = {},
    val onSelectSubtitleTrack: (String?) -> Unit = {},
    val onDismissSheet: () -> Unit = {},
    val onToggleFullScreen: () -> Unit = {},
    val onBack: () -> Unit = {},
)
