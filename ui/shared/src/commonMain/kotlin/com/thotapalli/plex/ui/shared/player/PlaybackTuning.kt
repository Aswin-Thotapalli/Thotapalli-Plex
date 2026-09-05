package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.thotapalli.plex.ui.shared.AppContainer

/**
 * Engine behaviour the viewer can change, read by the platform video surface when it builds the
 * engine. Carried as a composition local because the surface is the one place the engine is
 * constructed and it has no other route to the settings.
 *
 * Both default to what proved right on real televisions: tunnelling off, because in tunnelled
 * mode the picture keeps going after a seek while the hardware-synced audio track is rebuilt —
 * five to twelve seconds of silence with video — and passthrough on, so a receiver still gets
 * Dolby and DTS as a bitstream. See ExoPlayerEngine and CLAUDE.md sections 8 and 18 item 3.
 */
@Immutable
data class PlaybackTuning(
    val tunnelledVideo: Boolean = false,
    val audioPassthrough: Boolean = true,
)

val LocalPlaybackTuning = compositionLocalOf { PlaybackTuning() }

/** The current settings as a [PlaybackTuning], re-read whenever [settingsRevision] moves. */
@Composable
fun rememberPlaybackTuning(container: AppContainer, settingsRevision: Int): PlaybackTuning =
    remember(settingsRevision) {
        PlaybackTuning(
            tunnelledVideo = container.settings.tunnelledPlayback,
            audioPassthrough = container.settings.audioPassthrough,
        )
    }
