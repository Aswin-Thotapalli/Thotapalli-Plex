package com.thotapalli.plex.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.thotapalli.plex.core.download.ActiveDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps a download alive when the app is backgrounded.
 *
 * Without a foreground service the process is a candidate for the OS to kill the moment the last
 * activity stops, which is exactly when a download is running unattended. This service promotes the
 * process to foreground with an ongoing notification (CLAUDE.md section 11: downloads run one at a
 * time until done), mirrors the queue's [ActiveDownload] progress into that notification, and stands
 * itself down the instant the queue goes idle.
 *
 * It owns no download logic of its own — the work stays in [com.thotapalli.plex.core.download.DownloadQueue].
 * The service is a lifetime holder and a progress mirror, nothing more.
 */
class DownloadService : Service() {

    // Main.immediate so notification updates post without an extra dispatch hop; the collection
    // itself does no blocking work.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must happen within a few seconds of the start request, so it is the very
        // first thing done — before touching the container, which could in principle be uninitialised.
        // ServiceCompat passes the DATA_SYNC type on API 29+ (required on API 34+) and falls back to
        // the plain call on older releases; minSdk is 31 so the typed path is always taken here.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        val queue: StateFlow<ActiveDownload?>? =
            (application as? ThotapalliApplication)?.container?.downloadQueue?.active

        if (queue == null) {
            // No queue on this process (downloads not wired up). Nothing to guard.
            stop()
            return START_NOT_STICKY
        }

        // Observe once. A second start (queue picking up the next item, or the Application observer
        // firing again) reuses the running collector rather than stacking a second one.
        if (collectJob == null) {
            collectJob = scope.launch {
                queue.collect { active ->
                    if (active == null) {
                        // Queue idle: drop foreground status and let the process be reclaimed.
                        stop()
                    } else {
                        NotificationManagerCompat.from(this@DownloadService)
                            .safeNotify(NOTIFICATION_ID, buildNotification(active))
                    }
                }
            }
        }

        // Not sticky: if the OS kills us under memory pressure we do not want a bare restart with no
        // active download. The Application observer restarts the service when a download next runs.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stop() {
        collectJob?.cancel()
        collectJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(active: ActiveDownload?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // A platform system icon so no drawable resource has to be introduced for this.
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (active == null || active.totalBytes <= 0L) {
            // Startup or unknown size: an indeterminate bar until the first progress arrives.
            builder.setContentText("Preparing download")
                .setProgress(0, 0, true)
        } else {
            val percent = (active.fraction * 100).toInt()
            builder.setContentText("$percent%")
                .setProgress(100, percent, false)
        }

        return builder.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing media downloads"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    /**
     * POST_NOTIFICATIONS is a runtime permission on Android 13+. If the user denied it the notify
     * call throws, and that must not take the service down — the download keeps running, only the
     * notification is absent. The initial [startForeground] is unaffected by the denial.
     */
    private fun NotificationManagerCompat.safeNotify(id: Int, notification: Notification) {
        runCatching { notify(id, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 42
    }
}
