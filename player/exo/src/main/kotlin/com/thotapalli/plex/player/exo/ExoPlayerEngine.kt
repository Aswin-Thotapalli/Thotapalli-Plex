package com.thotapalli.plex.player.exo

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.thotapalli.plex.core.playback.PlaybackFailure
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.core.playback.PlayerTracks
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
            .build()
            .also { it.addListener(listener) }
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
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = PlaybackState.Failed(error.toPlaybackFailure(), error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            _tracks.value = tracks.toPlayerTracks()
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
        player.setVideoSurfaceView(surfaceView)
    }

    fun detachSurface() {
        player.clearVideoSurface()
    }

    override fun load(source: PlaybackSource, startAtMs: Long) {
        _renderedFirstFrame.value = false
        _state.value = PlaybackState.Buffering

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(source.headers)
            .setAllowCrossProtocolRedirects(true)

        player.setMediaSource(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(source.uri)),
        )
        player.prepare()
        if (startAtMs > 0) player.seekTo(startAtMs)

        startPositionUpdates()
        watchForFirstFrame()
    }

    override fun play() {
        player.playWhenReady = true
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

    override fun release() {
        positionJob?.cancel()
        positionJob = null
        player.removeListener(listener)
        player.release()
        _state.value = PlaybackState.Idle
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

    private companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
        const val POSITION_POLL_MS = 250L
    }
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
