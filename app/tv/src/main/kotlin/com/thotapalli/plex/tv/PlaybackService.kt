package com.thotapalli.plex.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thotapalli.plex.player.exo.ExoPlayerEngine

/**
 * The foreground media session for background and screen-locked playback (request #2, CLAUDE.md
 * section 8). See the mobile [PlaybackService] for the full rationale: the player is owned by the
 * UI-scoped [ExoPlayerEngine], so Media3's own foreground bookkeeping does not fire here — this
 * service calls [startForeground] itself in [onStartCommand] so the `startForegroundService` that
 * started it is always matched in Android's window (else the app is crashed), which is what holds
 * the process in the foreground while a video plays.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        ExoPlayerEngine.activeSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Thotapalli Plex")
            .setContentText("Playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()

        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
    }
}
