package com.thotapalli.plex.player.exo

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
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
     * Television devices only, detected rather than assumed.
     *
     * Tunnelling routes decoded video around the application so the hardware performs audio
     * and video synchronisation. It is a television feature and enabling it on a phone
     * costs compatibility for nothing.
     */
    private val isTelevision: Boolean by lazy {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setTunnelingEnabled(isTelevision)
            .setPreferredAudioLanguages(preferredAudioLanguage)
            .build()
    }

    private var positionJob: Job? = null
    private var durationMs: Long = 0

    val player: ExoPlayer by lazy { buildPlayer() }

    // experimentalSetDynamicSchedulingEnabled carries its own opt-in marker on top of
    // UnstableApi. The marker is a Java @RequiresOptIn checked by Android Lint, so it needs
    // androidx.annotation.OptIn rather than Kotlin's, which has no effect on it.
    // It reduces playback loop wake-ups. See CLAUDE.md section 8 point 3.
    @androidx.annotation.OptIn(ExperimentalApi::class)
    private fun buildPlayer(): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            // The bundled FFmpeg decoder handles audio formats the device decoder rejects,
            // which avoids a server transcode triggered by audio alone.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderers)
            .setTrackSelector(trackSelector)
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
            .build()
            .also {
                it.addListener(listener)
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

    fun detachSurface() {
        player.clearVideoSurface()
        surfaceView = null
    }

    override fun load(source: PlaybackSource, startAtMs: Long) {
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

        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(source.headers)
            .setAllowCrossProtocolRedirects(true)

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

    override fun play() {
        player.playWhenReady = true
        // Screen-locked / background audio is carried by the wakelock + audio-focus set on the
        // player (see buildPlayer) and the MediaSession for media-button handling. We deliberately
        // do NOT start a foreground service here: on Android 14+ a mediaPlayback foreground service
        // that fails to post its notification in the allowed window crashes the app a few seconds
        // into playback, and that async crash cannot be caught at the call site. Keeping playback
        // rock-solid matters more than lock-screen transport controls. See CLAUDE.md section 8.
        ensureMediaSession()
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun seekTo(ms: Long) {
        player.seekTo(ms)
        _positionMs.value = ms
    }

    /**
     * Scrubbing mode, added in Media3 1.8. On seek bar drag start and false on drag end.
     * See CLAUDE.md section 8.
     */
    override fun setScrubbing(active: Boolean) {
        player.setScrubbingModeEnabled(active)
    }

    override fun selectAudioTrack(id: String) = selectTrack(id, C.TRACK_TYPE_AUDIO)

    override fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        selectTrack(id, C.TRACK_TYPE_TEXT)
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
    override fun setPlaybackSpeed(speed: Float) {
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
                _positionMs.value = player.currentPosition
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
        scope.launch {
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

        /**
         * The MediaSession of the engine currently playing, or null when nothing is playing. The
         * foreground [MediaSessionService] in the app modules reads this in onGetSession so it can
         * host the session without holding a reference to the UI-scoped engine. Set when [load]
         * builds the session and cleared on [release]. See CLAUDE.md section 8 (request #2).
         */
        @Volatile
        var activeSession: MediaSession? = null
            internal set
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
