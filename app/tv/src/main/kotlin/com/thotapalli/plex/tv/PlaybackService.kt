package com.thotapalli.plex.tv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thotapalli.plex.player.exo.ExoPlayerEngine

/**
 * The foreground media session for background and screen-locked playback (request #2, CLAUDE.md
 * section 8). See the mobile [PlaybackService] for the rationale: the player is owned by the
 * UI-scoped [ExoPlayerEngine], and this service only hosts the session it publishes to
 * [ExoPlayerEngine.activeSession] so the platform draws the media notification and keeps the
 * process alive with the screen off.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        ExoPlayerEngine.activeSession
}
