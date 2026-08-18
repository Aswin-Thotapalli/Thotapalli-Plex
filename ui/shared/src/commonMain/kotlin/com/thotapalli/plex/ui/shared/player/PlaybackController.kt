package com.thotapalli.plex.ui.shared.player

import com.thotapalli.plex.core.api.PlexServerSource
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.api.TimelineState as ApiTimelineState
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.playback.AutoPlayCountdown
import com.thotapalli.plex.core.playback.MarkerController
import com.thotapalli.plex.core.playback.PlaybackFallbackChain
import com.thotapalli.plex.core.playback.PlaybackMode
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.TimelineReporter
import com.thotapalli.plex.core.playback.TimelineSink
import com.thotapalli.plex.core.playback.TimelineState
import com.thotapalli.plex.core.download.OfflineTimelineQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives one playback session.
 *
 * Owns the timeline reporting, the markers, the auto-play countdown, the control fade and
 * the transcode fallback, so an engine only has to play what it is handed. That is what
 * lets Media3 and libmpv behave identically without either knowing about the other.
 */
class PlaybackController(
    private val engine: PlayerEngine,
    private val api: PlexServerSource,
    private val serverScope: ServerScope,
    private val urls: PlexUrls,
    private val sessionIdentifier: String,
    private val identityHeaders: Map<String, String>,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    /**
     * Where progress goes when the server is unreachable. Null means it is simply lost,
     * which is only acceptable on a target with no offline support at all.
     */
    private val offlineTimeline: OfflineTimelineQueue? = null,
    /** Only Android phone and tablet has a second engine to retry through. */
    hasSecondaryEngine: Boolean = false,
    private val onRequestSecondaryEngine: (() -> Unit)? = null,
) {

    private val _state = MutableStateFlow(PlayerScreenState())
    val state: StateFlow<PlayerScreenState> = _state.asStateFlow()

    private val fallback = PlaybackFallbackChain(hasSecondaryEngine)
    private val countdown = AutoPlayCountdown(nowMs)
    private val reporter = TimelineReporter(ServerTimelineSink(), nowMs)

    private var markers = MarkerController()
    private var current: MediaItem? = null
    private var detail: MediaDetail? = null
    private var nextEpisode: Episode? = null
    private var partId: String? = null
    private var lastInputAtMs: Long = 0
    private var tickJob: Job? = null
    private var chipJob: Job? = null

    var onPlayNextEpisode: ((Episode) -> Unit)? = null

    // --- starting -----------------------------------------------------------------------

    suspend fun start(
        item: MediaItem,
        detail: MediaDetail?,
        nextEpisode: Episode?,
        startAtMs: Long,
    ) {
        this.current = item
        this.detail = detail
        this.nextEpisode = nextEpisode
        this.partId = detail?.primaryPart?.partId

        fallback.reset()
        countdown.reset()
        reporter.startItem(item.ratingKey)

        markers = MarkerController(
            markers = detail?.markers.orEmpty(),
            durationMs = item.durationMs,
            hasNextEpisode = nextEpisode != null,
        )

        _state.value = PlayerScreenState(
            title = titleFor(item),
            subtitle = subtitleFor(item),
            durationMs = item.durationMs,
            positionMs = startAtMs,
            nextEpisodeTitle = nextEpisode?.title,
            controlsVisible = true,
            trickplayUrlAt = { positionMs ->
                partId?.takeIf { detail?.primaryPart != null }?.let { urls.trickplay(it, positionMs) }
            },
        )

        noteInput()
        loadCurrentAttempt(startAtMs)
        observeEngine()
        startTicking()
    }

    private fun loadCurrentAttempt(startAtMs: Long) {
        val item = current ?: return
        val attempt = fallback.current()

        val uri = when (attempt.mode) {
            PlaybackMode.DIRECT -> directUri() ?: transcodeUri(item, startAtMs)
            PlaybackMode.TRANSCODE -> transcodeUri(item, startAtMs)
        }

        engine.load(
            PlaybackSource(
                uri = uri,
                mode = attempt.mode,
                headers = identityHeaders,
                frameRate = detail?.primaryPart?.frameRate,
            ),
            startAtMs,
        )
        engine.play()
    }

    private fun directUri(): String? {
        val part = detail?.primaryPart ?: return null
        // The part key already carries the path the server expects, so it is used verbatim
        // rather than rebuilt from the id.
        return urls.partKey(part.fileKey)
    }

    private fun transcodeUri(item: MediaItem, startAtMs: Long): String =
        urls.withToken(
            "/video/:/transcode/universal/start.m3u8" +
                "?path=%2Flibrary%2Fmetadata%2F${item.ratingKey}" +
                "&mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1" +
                "&offset=${startAtMs / 1000}" +
                "&directPlay=0&directStream=1&subtitles=burn" +
                "&X-Plex-Session-Identifier=$sessionIdentifier",
        )

    // --- engine observation ---------------------------------------------------------------

    private fun observeEngine() {
        scope.launch {
            engine.state.collect { playbackState ->
                _state.update { it.copy(playbackState = playbackState) }

                if (playbackState is PlaybackState.Failed) {
                    handleFailure(playbackState)
                }
                if (playbackState is PlaybackState.Ended) {
                    onEnded()
                }
            }
        }

        scope.launch {
            engine.positionMs.collect { position ->
                _state.update { it.copy(positionMs = position) }
            }
        }
    }

    /**
     * The silent fallback from CLAUDE.md section 10.
     *
     * The viewer is told nothing except a small chip. A failure the transcode cannot fix
     * ends the chain and surfaces as an error instead.
     */
    private suspend fun handleFailure(failed: PlaybackState.Failed) {
        val next = fallback.next(failed.reason) ?: return

        val position = _state.value.positionMs

        if (next == PlaybackFallbackChain.Attempt.DIRECT_SECONDARY) {
            onRequestSecondaryEngine?.invoke()
            return
        }

        loadCurrentAttempt(position)
        showTranscodingChip()
    }

    private fun showTranscodingChip() {
        chipJob?.cancel()
        _state.update { it.copy(showTranscodingChip = true) }
        chipJob = scope.launch {
            delay(TRANSCODE_CHIP_MS)
            _state.update { it.copy(showTranscodingChip = false) }
        }
    }

    // --- the tick -------------------------------------------------------------------------

    /**
     * One loop drives timeline reporting, marker checks, the countdown and the control
     * fade, so those four cannot drift out of step with each other.
     */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private suspend fun tick() {
        val item = current ?: return
        val snapshot = _state.value
        val position = snapshot.positionMs

        reporter.onTick(
            ratingKey = item.ratingKey,
            state = if (snapshot.isPlaying) TimelineState.PLAYING else TimelineState.PAUSED,
            positionMs = position,
            durationMs = snapshot.durationMs,
        )

        // A credits marker skips automatically into the next episode.
        if (markers.shouldAutoSkipCredits(position)) {
            playNext()
            return
        }

        val showPrompt = markers.showNextEpisodePrompt(position)
        if (showPrompt) countdown.start()
        if (countdown.isElapsed()) {
            playNext()
            return
        }

        // Controls fade after 3000 ms of no input.
        val idleFor = nowMs() - lastInputAtMs
        val controlsVisible = idleFor < CONTROLS_TIMEOUT_MS || snapshot.openSheet != null

        _state.update {
            it.copy(
                showSkipIntro = markers.showSkipIntro(position),
                showNextEpisodePrompt = showPrompt && countdown.isRunning,
                countdownSeconds = countdown.remainingSeconds(),
                controlsVisible = controlsVisible,
            )
        }
    }

    private suspend fun onEnded() {
        if (nextEpisode != null) playNext()
    }

    private fun playNext() {
        val next = nextEpisode ?: return
        countdown.reset()
        onPlayNextEpisode?.invoke(next)
    }

    // --- actions --------------------------------------------------------------------------

    /** Any pointer movement, touch or remote key press. */
    fun noteInput() {
        lastInputAtMs = nowMs()
        if (_state.value.showNextEpisodePrompt) countdown.cancel()
        _state.update { it.copy(controlsVisible = true) }
    }

    fun actions(): PlayerActions = PlayerActions(
        onPlayPause = {
            noteInput()
            val item = current
            if (_state.value.isPlaying) engine.pause() else engine.play()
            if (item != null) {
                scope.launch {
                    reporter.onImmediate(
                        item.ratingKey,
                        if (_state.value.isPlaying) TimelineState.PAUSED else TimelineState.PLAYING,
                        _state.value.positionMs,
                        _state.value.durationMs,
                    )
                }
            }
        },
        onSeekBack = { noteInput(); seekBy(-SEEK_BACK_MS) },
        onSeekForward = { noteInput(); seekBy(SEEK_FORWARD_MS) },
        onScrubStart = {
            noteInput()
            engine.setScrubbing(true)
        },
        onScrub = { positionMs ->
            noteInput()
            _state.update { it.copy(scrubPositionMs = positionMs) }
            engine.seekTo(positionMs)
        },
        onScrubEnd = { positionMs ->
            noteInput()
            engine.setScrubbing(false)
            engine.seekTo(positionMs)
            _state.update { it.copy(scrubPositionMs = null) }
            reportSeek(positionMs)
        },
        onSkipIntro = {
            noteInput()
            markers.skipIntroTargetMs()?.let {
                engine.seekTo(it)
                reportSeek(it)
            }
        },
        onPlayNext = { noteInput(); playNext() },
        onCancelAutoPlay = {
            countdown.cancel()
            _state.update { it.copy(showNextEpisodePrompt = false) }
        },
        onOpenAudioTracks = {
            noteInput()
            _state.update { it.copy(openSheet = TrackSheetKind.AUDIO) }
        },
        onOpenSubtitleTracks = {
            noteInput()
            _state.update { it.copy(openSheet = TrackSheetKind.SUBTITLE) }
        },
        onSelectAudioTrack = { id ->
            id?.let(engine::selectAudioTrack)
            _state.update { it.copy(openSheet = null) }
        },
        onSelectSubtitleTrack = { id ->
            engine.selectSubtitleTrack(id)
            _state.update { it.copy(openSheet = null) }
        },
        onDismissSheet = { _state.update { it.copy(openSheet = null) } },
    )

    private fun seekBy(deltaMs: Long) {
        val target = (_state.value.positionMs + deltaMs)
            .coerceIn(0, _state.value.durationMs.coerceAtLeast(0))
        engine.seekTo(target)
        reportSeek(target)
    }

    private fun reportSeek(positionMs: Long) {
        val item = current ?: return
        scope.launch {
            reporter.onImmediate(
                item.ratingKey,
                if (_state.value.isPlaying) TimelineState.PLAYING else TimelineState.PAUSED,
                positionMs,
                _state.value.durationMs,
            )
        }
    }

    /**
     * Sends state=stopped and then releases.
     *
     * Blocks for at most 2000 ms waiting for the report: losing one position is better than
     * hanging the interface on a server that has stopped answering.
     * See CLAUDE.md section 5.
     */
    suspend fun stopAndRelease() {
        tickJob?.cancel()
        chipJob?.cancel()

        current?.let { item ->
            withTimeoutOrNull(TimelineReporter.STOP_TIMEOUT_MS) {
                runCatching {
                    reporter.onStop(item.ratingKey, _state.value.positionMs, _state.value.durationMs)
                }
            }
        }

        engine.release()
    }

    private fun titleFor(item: MediaItem): String =
        if (item is Episode) item.showTitle.ifBlank { item.title } else item.title

    private fun subtitleFor(item: MediaItem): String? = (item as? Episode)?.let {
        "S${it.seasonIndex.toString().padStart(2, '0')}" +
            "E${it.episodeIndex.toString().padStart(2, '0')}  ${it.title}"
    }

    /**
     * Reports to the server, falling back to the offline queue.
     *
     * A report that cannot reach the server is written to pending_timeline and replayed on
     * reconnection, which is what makes a position recorded in aeroplane mode show up on
     * the server afterwards. See CLAUDE.md section 11.
     */
    private inner class ServerTimelineSink : TimelineSink {
        override suspend fun timeline(
            ratingKey: String,
            state: TimelineState,
            positionMs: Long,
            durationMs: Long,
        ) {
            runCatching {
                api.timeline(
                    scope = serverScope,
                    ratingKey = ratingKey,
                    state = when (state) {
                        TimelineState.PLAYING -> ApiTimelineState.PLAYING
                        TimelineState.PAUSED -> ApiTimelineState.PAUSED
                        TimelineState.STOPPED -> ApiTimelineState.STOPPED
                    },
                    positionMs = positionMs,
                    durationMs = durationMs,
                    sessionIdentifier = sessionIdentifier,
                )
            }.onFailure {
                offlineTimeline?.record(ratingKey, positionMs, durationMs, state.name.lowercase())
            }
        }

        override suspend fun scrobble(ratingKey: String) {
            runCatching { api.scrobble(serverScope, ratingKey) }.onFailure {
                // Recorded at the full duration, so the replay marks it watched rather than
                // leaving it a few seconds short forever.
                offlineTimeline?.record(
                    ratingKey,
                    _state.value.durationMs,
                    _state.value.durationMs,
                    "stopped",
                )
            }
        }
    }

    private companion object {
        const val TICK_MS = 500L
        const val CONTROLS_TIMEOUT_MS = 3_000L
        const val TRANSCODE_CHIP_MS = 4_000L
        const val SEEK_BACK_MS = -10_000L
        const val SEEK_FORWARD_MS = 30_000L
    }
}
