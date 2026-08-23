package com.thotapalli.plex.mobile

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thotapalli.plex.player.exo.ExoPlayerEngine

/**
 * The foreground media session for background and screen-locked playback (request #2, CLAUDE.md
 * section 8).
 *
 * The player itself is created and owned by the UI-scoped [ExoPlayerEngine], not by this service —
 * our surface rules keep the SurfaceView in the Compose tree. So rather than owning a player, this
 * service simply hosts the session the engine has already published to [ExoPlayerEngine.activeSession].
 * Media3's [MediaSessionService] turns that into the platform media notification and lock-screen
 * transport controls, and holds the process in the foreground so audio keeps flowing while the
 * screen is off. The engine starts the service on play and stops it on release.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        ExoPlayerEngine.activeSession
}
