package com.thotapalli.plex.player.exo

import android.app.Activity
import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.thotapalli.plex.core.model.DiagnosticCategory
import com.thotapalli.plex.core.model.Diagnostics
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.thotapalli.plex.core.playback.PlaybackFailure
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.core.playback.PlayerTracks
import com.thotapalli.plex.core.playback.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Media3 engine, set up exactly as CLAUDE.md section 8 specifies.
 *
 * None of the configuration here is default behaviour. Tunnelling, extension renderers,
 * dynamic scheduling and scrubbing mode are each there for a stated reason.
 */
@UnstableApi
class ExoPlayerEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preferredAudioLanguage: String = "eng",
    /**
     * Tunnelled video on a television: the platform synchronises audio and video in hardware and
     * the application never sees a decoded frame. Off by default — see the track selector below.
     */
    private val tunnelledVideo: Boolean = false,
    /** Bitstream Dolby and DTS to the audio output where the device reports it can. See [buildPlayer]. */
    private val audioPassthrough: Boolean = true,
) : PlayerEngine {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _tracks = MutableStateFlow(PlayerTracks())
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    private val _renderedFirstFrame = MutableStateFlow(false)
    val renderedFirstFrame: StateFlow<Boolean> = _renderedFirstFrame.asStateFlow()

    /**
     * The display aspect ratio of the current video, width over height, with the stream's pixel
     * aspect ratio already applied. Zero until the first [VideoSize] arrives.
     *
     * A bare SurfaceView stretched to MATCH_PARENT would distort anything whose shape is not the
     * view's shape — a 2.39:1 film squashed onto a 16:9 phone. The surface's host frame reads this
     * to size itself to the picture and letterbox the remainder, exactly as Media3's own PlayerView
     * does through its AspectRatioFrameLayout. See CLAUDE.md section 8.
     */
    private val _videoAspectRatio = MutableStateFlow(0f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    /**
     * The subtitle appearance the viewer has chosen.
     *
     * Media3 attached to a bare SurfaceView renders no text cues itself, so a Compose cue
     * layer above the surface is what will draw subtitles for direct play. That layer reads
     * this flow for its size, colour and background box. See CLAUDE.md sections 8 and 12.
     */
    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    /**
     * The cues Media3 is currently emitting for the selected text track.
     *
     * A bare SurfaceView has no place for Media3 to draw text, so the engine forwards every
     * [CueGroup] from [Player.Listener.onCues] here and the Android surface overlays a Media3
     * SubtitleView above the video to render them. This carries text cues and bitmap cues
     * (PGS, VOBSUB) alike, and works the same for direct play and transcode since both paths
     * feed the same player. See CLAUDE.md sections 8 and 12.
     */
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues: StateFlow<List<Cue>> = _cues.asStateFlow()

    /**
     * Refresh-rate matching from CLAUDE.md section 9. Constructed once a surface is attached,
     * because the window and the surface both come from that view. Null on a device where the
     * hosting Activity cannot be resolved, in which case matching is skipped.
     */
    private var surfaceView: SurfaceView? = null
    private var displayModeController: AndroidDisplayModeController? = null

    /** The content rate for the current item, from [PlaybackSource.frameRate]. Null means no match. */
    private var pendingFrameRate: Float? = null

    /** Matching is attempted once per [load], after the first frame proves the surface is valid. */
    private var rateMatchAttempted = false

    /**
     * Television devices only, detected rather than assumed. Gates tunnelling, which is a
     * television feature and would cost a phone compatibility for nothing.
     */
    private val isTelevision: Boolean by lazy {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            // Tunnelling is opt-in, a revisit of CLAUDE.md §8 on evidence from real televisions. In
            // tunnelled mode the video decoder keeps presenting after a seek while the hardware-synced
            // AudioTrack is torn down and rebuilt — and with a bitstream output the TV or receiver
            // must re-lock onto it — which showed as picture with five to twelve seconds of silence
            // after every seek. Without tunnelling the picture is slaved to the audio clock, so the
            // two resume together, and §9 rate matching never depended on tunnelling. The setting
            // remains for hardware that genuinely needs it.
            .setTunnelingEnabled(isTelevision && tunnelledVideo)
            .setPreferredAudioLanguages(preferredAudioLanguage)
            .build()
    }

    private var positionJob: Job? = null
    private var firstFrameJob: Job? = null
    private var durationMs: Long = 0

    /**
     * The HTTP client behind the Media3 OkHttp data source.
     *
     * No call timeout: a direct-play stream is a single long-lived response and a call timeout would
     * kill it mid-film. The read timeout guards against a silently stalled socket — a dead relay that
     * never sends FIN — so Media3 sees an error and the section 10 chain can fall back rather than
     * hang on the 8 s no-first-frame watchdog forever. Connection pooling is OkHttp's default.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val player: ExoPlayer by lazy { buildPlayer() }

    // ExoPlayer must be touched only on the thread that created it (the main looper). The controller
    // calls into the engine from coroutines that are not always on main — most dangerously the
    // failure-driven transcode retry, which runs in the engine-state collector — so every method
    // that touches the player routes through here. Without this a dropped stream, a transcode
    // fallback, or an end-of-item retry crashes with "Player is accessed on the wrong thread".
    private val mainHandler = Handler(Looper.getMainLooper())
    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    // experimentalSetDynamicSchedulingEnabled carries its own opt-in marker on top of
    // UnstableApi. The marker is a Java @RequiresOptIn checked by Android Lint, so it needs
    // androidx.annotation.OptIn rather than Kotlin's, which has no effect on it.
    // It reduces playback loop wake-ups. See CLAUDE.md section 8 point 3.
    @androidx.annotation.OptIn(ExperimentalApi::class)
    private fun buildPlayer(): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            // EXTENSION_RENDERER_MODE_ON, not PREFER — a deliberate revisit of CLAUDE.md §8 in service
            // of §18.3 ("passthrough where the device supports it") and the best-audio goal.
            //
            // PREFER puts the bundled FFmpeg audio renderer FIRST, so Dolby/DTS are always DECODED to
            // PCM and bitstream passthrough never happens — a home-theatre receiver never receives the
            // Atmos/DTS-HD object or lossless stream. ON puts the platform MediaCodec audio renderer
            // first, which (via DefaultAudioSink's device AudioCapabilities) passes those formats
            // through to an AVR that reports the capability and decodes on a device — a phone speaker —
            // that does not. FFmpeg stays as the fallback for anything the platform rejects, so the §8
            // goal (audio never forces a server transcode) is fully preserved, and decoder fallback
            // covers a buggy platform decoder. Net: passthrough where it matters, no regression where
            // it does not. Passthrough is a setting because a bitstream output has to re-lock after
            // every seek on some TVs; turning it off decodes to PCM in FFmpeg (PREFER), which resumes
            // instantly.
            .setExtensionRendererMode(
                if (audioPassthrough) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
            )
            .setEnableDecoderFallback(true)

        // Tuned for direct play of large, high-bitrate files over the LAN or a relay, but MEMORY-
        // BOUNDED and scaled to the device. The forward + back buffers are capped by BYTES, not just
        // duration: a 60 Mbps remux buffered purely by time (the old prioritise-time setting) reaches
        // ~450 MB, which OOMs or gets a low-RAM tablet's process reclaimed. With a byte cap the buffer
        // holds whichever comes first — the duration on modest bitrates, the byte ceiling on high
        // ones (~8 s at 60 Mbps on a normal device) — so playback stays smooth without ballooning.
        // A back buffer still lets the 10 s seek-back replay from memory. See CLAUDE.md sections 8/10.
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val lowRam = activityManager.isLowRamDevice || memInfo.totalMem < LOW_RAM_THRESHOLD_BYTES

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ if (lowRam) 10_000 else 15_000,
                /* maxBufferMs = */ if (lowRam) 30_000 else 60_000,
                // What a seek waits for before the picture resumes. Kept short so a seek feels
                // immediate; the minimum buffer above keeps filling behind it.
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ if (lowRam) 10_000 else 20_000,
                /* retainBackBufferFromKeyframe = */ true,
            )
            // The hard memory ceiling on the forward buffer. false = respect it (do not let duration
            // override the byte cap). Smaller on a low-RAM device so the whole app stays well under
            // what Android will reclaim a backgrounded process for.
            .setTargetBufferBytes(if (lowRam) LOW_RAM_BUFFER_BYTES else NORMAL_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        return ExoPlayer.Builder(context, renderers)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            // Reduces playback loop wake-ups. Experimental, added in Media3 1.10.
            .experimentalSetDynamicSchedulingEnabled(true)
            // Background / screen-locked playback (request #2): hand audio focus to the system so
            // a call or another app pauses us cleanly and we resume after, and pause instead of
            // blaring when headphones are unplugged. USAGE_MEDIA / CONTENT_TYPE_MOVIE describes
            // video-with-audio to the audio policy.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Tunnelled seeks land on the nearest keyframe. An exact seek has to decode from the
            // previous keyframe up to the target first, and in tunnelled mode the application cannot
            // drop those frames — the hardware presents whatever is queued — so the pre-roll is paid
            // on screen while the rebuilt audio track is still starting. Snapping to the keyframe
            // removes that pre-roll; a ±10/30 s jump landing a second or two off is invisible on a
            // sofa. Non-tunnelled playback keeps exact seeks: the picture waits for audio anyway.
            .setSeekParameters(
                if (isTelevision && tunnelledVideo) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT,
            )
            .build()
            .also {
                it.addListener(listener)
                it.addAnalyticsListener(seekAudioProbe)
                // Hold a CPU + network wakelock while playing so audio keeps flowing with the
                // screen off; released automatically when playback stops. See CLAUDE.md section 8.
                it.setWakeMode(C.WAKE_MODE_NETWORK)
            }
    }

    private var mediaSession: MediaSession? = null

    /**
     * Builds (once) a MediaSession bound to this player and publishes it to [activeSession]. It backs
     * the lock-screen / notification transport controls and lets the foreground playback service
     * keep the process alive while the screen is off (request #2, CLAUDE.md section 8). The
     * app-module service reads [activeSession] rather than holding an engine reference. Called from
     * [load], on the main thread, since MediaSession must be built there.
     */
    private fun ensureMediaSession() {
        if (mediaSession != null) return
        mediaSession = MediaSession.Builder(context, player).build().also { activeSession = it }
    }

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: durationMs
            _state.value = when (playbackState) {
                Player.STATE_IDLE -> PlaybackState.Idle
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY -> if (player.playWhenReady) PlaybackState.Playing else PlaybackState.Paused
                Player.STATE_ENDED -> PlaybackState.Ended
                else -> PlaybackState.Idle
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // The player is now actually rendering: safe to promote the process to a foreground media
            // service (Media3 can post its notification at once). Latched true and only cleared on
            // release, so a pause does not tear the service down. See [sessionActive].
            if (isPlaying) _sessionActive.value = true
            if (_state.value is PlaybackState.Failed) return
            if (player.playbackState == Player.STATE_READY) {
                _state.value = if (isPlaying) PlaybackState.Playing else PlaybackState.Paused
            }
        }

        override fun onRenderedFirstFrame() {
            _renderedFirstFrame.value = true
            // The surface is guaranteed valid now, which is what Surface.setFrameRate needs.
            maybeMatchDisplayRate()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // width * pixelWidthHeightRatio / height is the true display shape: a source with
            // non-square pixels (anamorphic DVD, some broadcast) reports a storage size that is not
            // its display size, and pixelWidthHeightRatio corrects for it. Zero dimensions (audio
            // gap, track change) leave the last good ratio in place rather than collapsing the frame.
            if (videoSize.width > 0 && videoSize.height > 0) {
                _videoAspectRatio.value =
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = PlaybackState.Failed(error.toPlaybackFailure(), error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            _tracks.value = tracks.toPlayerTracks()
        }

        // Media3 delivers decoded cues here. On a bare SurfaceView it has nowhere to render
        // them, so they are pushed to the overlay SubtitleView instead. Empty when the text
        // track is off or between cues, which clears the last line. See CLAUDE.md section 8.
        override fun onCues(cueGroup: CueGroup) {
            _cues.value = cueGroup.cues
        }
    }

    /**
     * Attaches the video surface.
     *
     * A SurfaceView, never a TextureView. The decoder writes directly to a hardware layer
     * the compositor scans out, rather than through a texture composited with the interface.
     * See CLAUDE.md section 8.
     */
    fun attachSurface(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        player.setVideoSurfaceView(surfaceView)

        // The refresh-rate controller needs the Activity for the window's preferred mode id and
        // the surface for Surface.setFrameRate; both are reachable from the view's context.
        if (displayModeController == null) {
            surfaceView.context.findActivity()?.let { activity ->
                displayModeController = AndroidDisplayModeController(activity) { this.surfaceView }
            }
        }
    }

    fun detachSurface() = onMain {
        player.clearVideoSurface()
        surfaceView = null
    }

    override fun load(source: PlaybackSource, startAtMs: Long) = onMain {
        _renderedFirstFrame.value = false
        _cues.value = emptyList()
        _state.value = PlaybackState.Buffering

        // Re-evaluate the refresh rate for this item. Auto-play-next reuses this engine, so the
        // next episode must match afresh rather than inherit the previous file's decision.
        pendingFrameRate = source.frameRate
        rateMatchAttempted = false

        // The viewer's language preferences drive the initial track selection. A null preferred
        // subtitle language leaves Media3 to its default; subtitles are off unless the viewer has
        // asked for them on by default, in which case an undetermined-language text track counts.
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(source.preferredAudioLanguage ?: "eng")
            .setPreferredTextLanguage(source.preferredSubtitleLanguage)
            .setSelectUndeterminedTextLanguage(source.subtitlesOnByDefault)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !source.subtitlesOnByDefault)
            .build()

        val httpFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(source.headers)

        // A downloaded item plays from a local file:// URI, while streaming uses http(s). Wrapping
        // the HTTP factory in DefaultDataSource routes file/content/asset URIs to the right local
        // source and everything else to HTTP, so one factory serves both. The HTTP factory alone
        // cannot open a local file, which is what broke playing a download (§11, #1).
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        player.setMediaSource(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(source.uri)),
        )
        player.prepare()
        if (startAtMs > 0) player.seekTo(startAtMs)

        // Publish a session so the foreground service can host it and lock-screen controls appear.
        ensureMediaSession()

        startPositionUpdates()
        watchForFirstFrame()
    }

    override fun play() = onMain {
        player.playWhenReady = true
        // Screen-locked / background audio is carried by the wakelock + audio-focus set on the
        // player (see buildPlayer) and the MediaSession for media-button handling. We deliberately
        // do NOT start a foreground service here: on Android 14+ a mediaPlayback foreground service
        // that fails to post its notification in the allowed window crashes the app a few seconds
        // into playback, and that async crash cannot be caught at the call site. Keeping playback
        // rock-solid matters more than lock-screen transport controls. See CLAUDE.md section 8.
        ensureMediaSession()
    }

    override fun pause() = onMain {
        player.playWhenReady = false
    }

    override fun seekTo(ms: Long) = onMain {
        seekStartedAtMs = SystemClock.elapsedRealtime()
        player.seekTo(ms)
        _positionMs.value = ms
    }

    /** When the last seek was asked for, or null once its audio has resumed. See [seekAudioProbe]. */
    private var seekStartedAtMs: Long? = null

    /**
     * Measures the one number that decides whether tunnelling is worth keeping on a given
     * television: how long after a seek the sound comes back. Media3 reports the moment the audio
     * position starts advancing again after a flush, so the gap between the seek and that moment
     * is exactly the silence the viewer hears. Logged with the modes in force, so the Diagnostics
     * screen shows "tunnelled + passthrough: 6 s" against "neither: 400 ms" on real hardware,
     * which is evidence the emulator can never give (it has no tunnelling HAL).
     */
    private val seekAudioProbe = object : AnalyticsListener {
        override fun onAudioPositionAdvancing(
            eventTime: AnalyticsListener.EventTime,
            playoutStartSystemTimeMs: Long,
        ) {
            val started = seekStartedAtMs ?: return
            seekStartedAtMs = null
            val gapMs = SystemClock.elapsedRealtime() - started
            Diagnostics.record(
                DiagnosticCategory.PLAYBACK,
                "Audio resumed $gapMs ms after seek " +
                    "(tunnelled=${isTelevision && tunnelledVideo}, passthrough=$audioPassthrough)",
            )
        }
    }

    /**
     * Scrubbing mode, added in Media3 1.8. On seek bar drag start and false on drag end.
     * See CLAUDE.md section 8.
     */
    override fun setScrubbing(active: Boolean) = onMain {
        player.setScrubbingModeEnabled(active)
    }

    override fun selectAudioTrack(id: String) = onMain { selectTrack(id, C.TRACK_TYPE_AUDIO) }

    override fun selectSubtitleTrack(id: String?) = onMain {
        if (id == null) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            selectTrack(id, C.TRACK_TYPE_TEXT)
        }
    }

    private fun selectTrack(id: String, trackType: Int) {
        val group = player.currentTracks.groups
            .filter { it.type == trackType }
            .firstOrNull { group ->
                (0 until group.length).any { trackId(group.mediaTrackGroup.id, it) == id }
            } ?: return

        val index = (0 until group.length)
            .firstOrNull { trackId(group.mediaTrackGroup.id, it) == id } ?: return

        trackSelector.parameters = trackSelector.buildUponParameters()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    /** 1.0 is normal; the overlay offers 0.75x–2x. See CLAUDE.md section 8. */
    override fun setPlaybackSpeed(speed: Float) = onMain {
        player.setPlaybackSpeed(speed)
    }

    /**
     * Stores the subtitle appearance for the Compose cue layer to read. Media3 on a bare
     * SurfaceView draws no text itself, so the style is applied where the cues are rendered
     * rather than on the player. See CLAUDE.md sections 8 and 12.
     */
    override fun setSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle.value = style
    }

    override fun release() {
        positionJob?.cancel()
        positionJob = null
        firstFrameJob?.cancel()
        firstFrameJob = null
        onMain {
            // Put any refresh-rate change back before the player leaves, so a switched mode does not
            // stick after playback. Guarded inside the controller. See CLAUDE.md section 9.
            runCatching { displayModeController?.restore() }
            // Tear the session down before the player it wraps, and clear the shared handle so the
            // foreground service stops advertising a dead session. See CLAUDE.md section 8.
            mediaSession?.let { session ->
                if (activeSession === session) activeSession = null
                runCatching { session.release() }
            }
            mediaSession = null
            player.removeListener(listener)
            player.release()
            _state.value = PlaybackState.Idle
            // The session is gone; let the app module stop the foreground media service.
            _sessionActive.value = false
        }
    }

    /**
     * Runs the section 9 sequence once per file, after the first frame.
     *
     * A non-null [pendingFrameRate] is the signal that matching is wanted: the controller in
     * PlaybackController only supplies a frame rate when it should be matched. The switch can
     * block and blanks the screen briefly, so it runs on the engine scope and the controller
     * swallows every failure. Its own log line carries content rate and display rate before and
     * after, as CLAUDE.md section 16 phase 5 step 3 asks for.
     */
    private fun maybeMatchDisplayRate() {
        if (rateMatchAttempted) return
        val fps = pendingFrameRate ?: return
        val controller = displayModeController ?: return
        rateMatchAttempted = true
        scope.launch {
            runCatching { controller.matchForContent(contentFrameRate = fps, enabled = true) }
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                // player.currentPosition must be read on the main thread too.
                onMain { _positionMs.value = player.currentPosition }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /**
     * No rendered frame within 8000 ms counts as a direct play failure, even though Media3
     * reported no error. A decoder that accepts the stream and then produces nothing looks
     * exactly like a very slow buffer, and waiting forever is worse than a transcode.
     * See CLAUDE.md section 10.
     */
    private fun watchForFirstFrame() {
        firstFrameJob?.cancel()
        firstFrameJob = scope.launch {
            delay(com.thotapalli.plex.core.playback.FIRST_FRAME_TIMEOUT_MS)
            if (!_renderedFirstFrame.value && _state.value !is PlaybackState.Failed) {
                _state.value = PlaybackState.Failed(PlaybackFailure.NO_FIRST_FRAME, null)
            }
        }
    }

    companion object {
        private const val SEEK_BACK_MS = 10_000L
        private const val SEEK_FORWARD_MS = 30_000L
        private const val POSITION_POLL_MS = 250L

        /** Below this much total RAM the device is treated as memory-constrained (a ~3–4 GB tablet). */
        private const val LOW_RAM_THRESHOLD_BYTES = 4L * 1024 * 1024 * 1024

        /** Forward-buffer byte ceilings: ~8 s at 60 Mbps on a normal device, half that on a low-RAM one. */
        private const val NORMAL_BUFFER_BYTES = 64 * 1024 * 1024
        private const val LOW_RAM_BUFFER_BYTES = 24 * 1024 * 1024

        /**
         * The MediaSession of the engine currently playing, or null when nothing is playing. The
         * foreground [MediaSessionService] in the app modules reads this in onGetSession so it can
         * host the session without holding a reference to the UI-scoped engine. Set when [load]
         * builds the session and cleared on [release]. See CLAUDE.md section 8 (request #2).
         */
        @Volatile
        var activeSession: MediaSession? = null
            internal set

        /**
         * True while a playback session is live and has actually started playing. The app module's
         * foreground [MediaSessionService] is started off this so the process is held in the
         * foreground while a video plays — which is what stops a memory-constrained tablet from
         * killing the app mid-playback and relaunching it (losing the last progress report).
         *
         * It is set only once playback reaches [PlaybackState.Playing], never merely when the session
         * is built: a `mediaPlayback` foreground service that is started before its session has a
         * playing player cannot post its notification in the window Android 14+ requires and crashes
         * the app. Waiting for Playing guarantees Media3 posts the media notification immediately, so
         * the service goes foreground in time. Cleared on [release]. See CLAUDE.md section 8.
         */
        private val _sessionActive: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val sessionActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _sessionActive
    }
}

/** Walks the context wrapper chain to the hosting Activity, or null if there is none. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun trackId(groupId: String, index: Int): String = "$groupId:$index"

@UnstableApi
internal fun Tracks.toPlayerTracks(): PlayerTracks {
    fun collect(type: Int): List<PlayerTrack> = groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                PlayerTrack(
                    id = trackId(group.mediaTrackGroup.id, index),
                    label = format.label
                        ?: listOfNotNull(
                            format.language,
                            format.sampleMimeType?.substringAfterLast('/')?.uppercase(),
                            format.channelCount.takeIf { it > 0 }?.let { "${it}ch" },
                        ).joinToString(" ").ifBlank { "Track ${index + 1}" },
                    language = format.language,
                    selected = group.isTrackSelected(index),
                )
            }
        }

    return PlayerTracks(audio = collect(C.TRACK_TYPE_AUDIO), subtitle = collect(C.TRACK_TYPE_TEXT))
}

/**
 * Maps a Media3 error onto the failure kinds the section 10 fallback chain distinguishes.
 * Only a decoder or track problem is worth a transcode.
 */
internal fun PlaybackException.toPlaybackFailure(): PlaybackFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    -> PlaybackFailure.DECODER_INITIALISATION

    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    -> PlaybackFailure.UNSUPPORTED_TRACK

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    -> PlaybackFailure.NETWORK

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    -> PlaybackFailure.SOURCE_NOT_FOUND

    else -> PlaybackFailure.UNKNOWN
}
