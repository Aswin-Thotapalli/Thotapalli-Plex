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
import com.thotapalli.plex.core.playback.PlaybackFailure
import com.thotapalli.plex.core.playback.PlaybackFallbackChain
import com.thotapalli.plex.core.playback.PlaybackMode
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackQuality
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTracks
import com.thotapalli.plex.core.playback.SubtitleStyle
import com.thotapalli.plex.core.playback.TimelineReporter
import com.thotapalli.plex.core.playback.TimelineSink
import com.thotapalli.plex.core.playback.TimelineState
import com.thotapalli.plex.core.download.OfflineResolver
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
    /** "Match display rate to content" (§9). When false, we do not pass a frame rate to the engine. */
    private val matchDisplayRate: Boolean = true,
    /** The viewer's preferred audio/subtitle languages and subtitles-on default (§14.9), applied by
     *  the engine to the initial track selection. */
    private val preferredAudioLanguage: String? = null,
    private val preferredSubtitleLanguage: String? = null,
    private val subtitlesOnByDefault: Boolean = false,
    /** The viewer's saved subtitle appearance (§14), applied to the engine after each load. */
    private val initialSubtitleStyle: SubtitleStyle = SubtitleStyle(),
    /**
     * Resolves a completed download to a local file so playback works offline (§11). Null on a
     * target with no download support, in which case everything streams from the server.
     */
    private val offlineResolver: OfflineResolver? = null,
    /**
     * Keeps the local cache in step with what is being watched. Timeline reports go to the server,
     * but the detail screen's "next unwatched", the continue-watching fallback and the library
     * watched badges read the cache, which is otherwise only refreshed on a browse — so without this
     * a just-finished episode still looks unwatched with a stale resume point, and "Play" resumes an
     * already-watched episode or an earlier point in the current one. Null on targets with no cache.
     */
    private val recordLocalOffset: (suspend (ratingKey: String, positionMs: Long) -> Unit)? = null,
    private val recordLocalWatched: (suspend (ratingKey: String) -> Unit)? = null,
) {

    private val _state = MutableStateFlow(PlayerScreenState())
    val state: StateFlow<PlayerScreenState> = _state.asStateFlow()

    private val fallback = PlaybackFallbackChain(hasSecondaryEngine)
    private val countdown = AutoPlayCountdown(nowMs)
    private val reporter = TimelineReporter(ServerTimelineSink(), nowMs)

    private var markers = MarkerController()
    private var current: MediaItem? = null
    private var detail: MediaDetail? = null
    private var previousEpisode: Episode? = null
    private var nextEpisode: Episode? = null
    private var partId: String? = null
    private var lastInputAtMs: Long = 0
    private var tickJob: Job? = null
    private var chipJob: Job? = null
    private var sleepTimerJob: Job? = null

    /** The engine-observing collectors, cancelled before [engine] is released so a post-release state
     *  emission can't drive [handleFailure] against a dead engine. */
    private val engineJobs = mutableListOf<Job>()

    /** Once true, no further loads/failure-recovery run — the engine is being torn down. */
    private var released = false

    /** One-shot latch so an episode advance fires exactly once, even when the auto-play countdown and
     *  the engine's Ended event both request it. Cleared per item in [start]. */
    private var advancing = false

    /** The video bitrate cap for the chosen quality (§11). Null is "Original" / direct play. */
    private var selectedMaxVideoBitrateKbps: Int? = null

    /** True once a capped quality is chosen: every load then goes straight to transcode (§11). */
    private var forceTranscode: Boolean = false

    // Sticky per-series track choice (§12 parity). A manual audio or subtitle pick on one episode
    // is remembered and re-applied to later episodes of the same show, so a binge keeps the
    // viewer's language rather than snapping back to the default every episode. Held for the
    // current show only; opening a different show clears it. [stickyApplied] gates the re-apply to
    // once per load, since selecting a track re-emits the track list.
    private var stickyShowKey: String? = null
    private var stickyAudioLanguage: String? = null
    private var stickySubtitleLanguage: String? = null
    private var stickySubtitleOff: Boolean = false
    private var stickyApplied: Boolean = false

    var onPlayNextEpisode: ((Episode) -> Unit)? = null
    var onPlayPreviousEpisode: ((Episode) -> Unit)? = null

    // --- starting -----------------------------------------------------------------------

    suspend fun start(
        item: MediaItem,
        detail: MediaDetail?,
        previousEpisode: Episode?,
        nextEpisode: Episode?,
        startAtMs: Long,
    ) {
        this.current = item
        this.detail = detail
        this.previousEpisode = previousEpisode
        this.nextEpisode = nextEpisode
        this.partId = detail?.primaryPart?.partId

        fallback.reset()
        countdown.reset()
        // Every item starts at Original quality; a capped choice is per-item (§11).
        selectedMaxVideoBitrateKbps = null
        forceTranscode = false

        // A new show clears the remembered track choice; the same show (the next episode in a
        // binge) keeps it so it can be re-applied once this item's tracks surface (§12 parity).
        val showKey = (item as? Episode)?.showRatingKey
        if (showKey != stickyShowKey) {
            stickyShowKey = showKey
            stickyAudioLanguage = null
            stickySubtitleLanguage = null
            stickySubtitleOff = false
        }
        stickyApplied = false
        advancing = false
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
            hasPreviousEpisode = previousEpisode != null,
            hasNextEpisode = nextEpisode != null,
            controlsVisible = true,
            trickplayUrlAt = { positionMs ->
                partId?.takeIf { detail?.primaryPart != null }?.let { urls.trickplay(it, positionMs) }
            },
            // Wave 2: reset speed, seed chapters, subtitle appearance, the quality ladder and
            // a minimal Up Next queue for the new item.
            playbackSpeed = 1f,
            chapters = detail?.chapters ?: emptyList(),
            subtitleStyle = initialSubtitleStyle,
            qualities = QUALITY_LADDER,
            currentQualityLabel = QUALITY_LADDER.first().label,
            upNext = listOfNotNull(nextEpisode),
        )

        noteInput()
        loadCurrentAttempt(startAtMs)
        observeEngine()
        startTicking()
    }

    private suspend fun loadCurrentAttempt(startAtMs: Long) {
        val item = current ?: return
        val attempt = fallback.current()
        // A fresh load (including a transcode fallback reload) surfaces a new track list, so allow
        // the remembered per-series choice to be applied to it once more (§12 parity).
        stickyApplied = false

        // A completed local download plays from disk unconditionally, and needs no online
        // decision. See CLAUDE.md section 11.
        val localPath = if (attempt.mode == PlaybackMode.DIRECT && !forceTranscode) {
            runCatching { offlineResolver?.localSource(item.ratingKey)?.path }.getOrNull()
        } else {
            null
        }

        val (uri, mode) = when {
            localPath != null -> fileUri(localPath) to PlaybackMode.DIRECT

            // A capped quality forces the server transcode (§11), as does a TRANSCODE attempt
            // reached through the failure-driven fallback chain (§10).
            forceTranscode || attempt.mode == PlaybackMode.TRANSCODE ->
                transcodeUri(item, startAtMs) to PlaybackMode.TRANSCODE

            else -> {
                // Online DIRECT attempt. Ask the server first (§10 decision): a false answer
                // transcodes up front instead of burning eight seconds on a doomed direct play.
                // A thrown decision proceeds with direct, and the failure-driven fallback in
                // handleFailure stays as the safety net either way.
                val pid = partId
                val allowed = if (pid != null) {
                    runCatching { api.canDirectPlay(serverScope, item.ratingKey, pid) }.getOrDefault(true)
                } else {
                    true
                }
                val direct = if (allowed) directUri() else null
                if (direct != null) direct to PlaybackMode.DIRECT
                else transcodeUri(item, startAtMs) to PlaybackMode.TRANSCODE
            }
        }

        engine.load(
            PlaybackSource(
                uri = uri,
                mode = mode,
                headers = identityHeaders,
                frameRate = detail?.primaryPart?.frameRate?.takeIf { matchDisplayRate },
                preferredAudioLanguage = preferredAudioLanguage,
                preferredSubtitleLanguage = preferredSubtitleLanguage,
                subtitlesOnByDefault = subtitlesOnByDefault,
            ),
            startAtMs,
        )
        // A fresh load resets the engine, so re-apply the session's speed and subtitle
        // appearance (they must survive a quality reload and a transcode fallback).
        engine.setPlaybackSpeed(_state.value.playbackSpeed)
        engine.setSubtitleStyle(_state.value.subtitleStyle)
        engine.play()
    }

    private fun directUri(): String? {
        val part = detail?.primaryPart ?: return null
        // The part key already carries the path the server expects, so it is used verbatim
        // rather than rebuilt from the id.
        return urls.partKey(part.fileKey)
    }

    /** A local download's file path as a file:// URI, normalised so Windows drive paths parse. */
    private fun fileUri(path: String): String {
        if (path.startsWith("file:")) return path
        val forward = path.replace('\\', '/')
        return if (forward.startsWith("/")) "file://$forward" else "file:///$forward"
    }

    private fun transcodeUri(item: MediaItem, startAtMs: Long): String =
        urls.withToken(
            "/video/:/transcode/universal/start.m3u8" +
                "?path=%2Flibrary%2Fmetadata%2F${item.ratingKey}" +
                "&mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1" +
                "&offset=${startAtMs / 1000}" +
                "&directPlay=0&directStream=1&subtitles=burn" +
                (selectedMaxVideoBitrateKbps?.let { "&maxVideoBitrate=$it" } ?: "") +
                "&X-Plex-Session-Identifier=$sessionIdentifier",
        )

    // --- engine observation ---------------------------------------------------------------

    private fun observeEngine() {
        engineJobs += scope.launch {
            engine.state.collect { playbackState ->
                if (released) return@collect
                _state.update { it.copy(playbackState = playbackState) }

                if (playbackState is PlaybackState.Failed) {
                    handleFailure(playbackState)
                }
                if (playbackState is PlaybackState.Ended) {
                    onEnded()
                }
            }
        }

        engineJobs += scope.launch {
            engine.positionMs.collect { position ->
                _state.update { it.copy(positionMs = position) }
            }
        }

        engineJobs += scope.launch {
            engine.tracks.collect { tracks ->
                _state.update { it.copy(audioTracks = tracks.audio, subtitleTracks = tracks.subtitle) }
                applyStickyTracks(tracks)
            }
        }
    }

    /**
     * Re-applies the remembered per-series track choice once this item's tracks have surfaced
     * (§12 parity). Runs at most once per load — [stickyApplied] guards it, because selecting a
     * track makes the engine re-emit the track list. A remembered choice already matching the
     * engine's default selection is a no-op (the `!selected` filter), so this only acts when the
     * default differs from what the viewer chose on an earlier episode.
     */
    private fun applyStickyTracks(tracks: PlayerTracks) {
        if (stickyApplied) return
        if (tracks.audio.isEmpty() && tracks.subtitle.isEmpty()) return
        stickyApplied = true

        stickyAudioLanguage?.let { lang ->
            tracks.audio.firstOrNull { it.language == lang && !it.selected }
                ?.let { engine.selectAudioTrack(it.id) }
        }
        when {
            stickySubtitleOff -> {
                if (tracks.subtitle.any { it.selected }) engine.selectSubtitleTrack(null)
            }
            stickySubtitleLanguage != null ->
                tracks.subtitle.firstOrNull { it.language == stickySubtitleLanguage && !it.selected }
                    ?.let { engine.selectSubtitleTrack(it.id) }
        }
    }

    /**
     * The silent fallback from CLAUDE.md section 10.
     *
     * The viewer is told nothing except a small chip. A failure the transcode cannot fix
     * ends the chain and surfaces as an error instead.
     */
    private suspend fun handleFailure(failed: PlaybackState.Failed) {
        if (released) return
        val next = fallback.next(failed.reason)
        com.thotapalli.plex.core.model.Diagnostics.record(
            com.thotapalli.plex.core.model.DiagnosticCategory.PLAYBACK,
            "Playback failure (${failed.reason})" +
                if (next == null) " — no fallback left" else " — falling back to ${next.name}",
        )
        if (next == null) {
            // Nothing left to try — most often the server or the internet dropped mid-stream. Surface
            // a clear message with a way out rather than freezing on a black frame or crashing (#1).
            val message = when (failed.reason) {
                PlaybackFailure.NETWORK ->
                    "Connection lost. Check your network, or go back to watch a download offline."
                PlaybackFailure.SOURCE_NOT_FOUND ->
                    "Can't reach the server. Go back to watch a download offline."
                else ->
                    "Playback stopped. Go back to watch a download offline."
            }
            engine.pause()
            _state.update { it.copy(errorMessage = message) }
            return
        }

        val position = _state.value.positionMs

        if (next == PlaybackFallbackChain.Attempt.DIRECT_SECONDARY) {
            val requestSecondary = onRequestSecondaryEngine
            if (requestSecondary != null) {
                requestSecondary()
                return
            }
            // No secondary engine is wired on this target: don't dead-end on the secondary attempt.
            // Advance the chain once more (DIRECT_SECONDARY -> TRANSCODE, always non-null) so the load
            // below runs the transcode fallback. In practice the secondary attempt only arises on
            // Android phone/tablet, where the callback is always present, so this is belt-and-braces.
            fallback.next(failed.reason)
        }

        loadCurrentAttempt(position)
        showTranscodingChip()
    }

    /**
     * Retries a failed playback from the last position — called when the connection returns (so the
     * viewer never restarts the app), and by the error surface's Retry control. A no-op unless we are
     * actually in the failed state, so a reconnect during healthy playback does nothing. See §10 (#1).
     */
    fun retry() {
        if (_state.value.errorMessage == null) return
        val position = _state.value.positionMs
        fallback.reset()
        _state.update { it.copy(errorMessage = null) }
        scope.launch { loadCurrentAttempt(position) }
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

        // Only report while actually playing. Pause/seek/stop report through onImmediate/onStop, so a
        // periodic report while paused or buffering would be a duplicate flood. See §5 and onTick.
        if (snapshot.isPlaying) {
            reporter.onTick(
                ratingKey = item.ratingKey,
                state = TimelineState.PLAYING,
                positionMs = position,
                durationMs = snapshot.durationMs,
            )
        }

        // Credits are never skipped automatically — the viewer chooses via the Skip Credits button.
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
                showSkipCredits = markers.showSkipCredits(position),
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
        if (advancing) return
        val next = nextEpisode ?: return
        // Latch: the auto-play countdown and the engine's Ended event can both land near the true end;
        // this makes the advance fire exactly once. Cleared when the next item's start() runs.
        advancing = true
        countdown.reset()
        onPlayNextEpisode?.invoke(next)
    }

    private fun playPrevious() {
        val previous = previousEpisode ?: return
        countdown.reset()
        onPlayPreviousEpisode?.invoke(previous)
    }

    // --- actions --------------------------------------------------------------------------

    /** Any pointer movement, touch or remote key press. */
    fun noteInput() {
        lastInputAtMs = nowMs()
        if (_state.value.showNextEpisodePrompt) countdown.cancel()
        _state.update { it.copy(controlsVisible = true) }
    }

    /**
     * A single tap on the picture toggles the controls: reveal them if hidden, dismiss them if
     * shown. It never pauses — pausing is the transport button alone. Hiding backdates the last-input
     * time so the tick's idle test keeps them hidden until the next real input.
     */
    fun toggleControls() {
        if (_state.value.controlsVisible) {
            lastInputAtMs = nowMs() - CONTROLS_TIMEOUT_MS
            _state.update { it.copy(controlsVisible = false) }
        } else {
            noteInput()
        }
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
        onUserInput = { noteInput() },
        onToggleControls = { toggleControls() },
        onSeekBack = { noteInput(); seekBy(SEEK_BACK_MS) },
        onSeekForward = { noteInput(); seekBy(SEEK_FORWARD_MS) },
        // The double-tap gestures seek a fixed ten seconds each way, independent of the
        // transport buttons' -10s / +30s. See CLAUDE.md section 14 item 7.
        onSeekForward10 = { noteInput(); seekBy(SEEK_TAP_FORWARD_MS) },
        // The television held-D-pad scrub steps by ±30_000 ms each 400 ms; each step is an
        // ordinary relative seek. See CLAUDE.md section 13.5.
        onSeekRelative = { delta -> noteInput(); seekBy(delta) },
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
        onSkipCredits = {
            noteInput()
            markers.skipCreditsTargetMs()?.let {
                engine.seekTo(it)
                reportSeek(it)
            }
        },
        onPlayNext = { noteInput(); playNext() },
        onPlayPreviousEpisode = { noteInput(); playPrevious() },
        onPlayNextEpisodeNow = { noteInput(); playNext() },
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
            // Remember the chosen language so the next episode of this show keeps it (§12 parity).
            id?.let { chosen ->
                stickyAudioLanguage = _state.value.audioTracks.firstOrNull { it.id == chosen }?.language
            }
            _state.update { it.copy(openSheet = null) }
        },
        onSelectSubtitleTrack = { id ->
            engine.selectSubtitleTrack(id)
            if (id == null) {
                stickySubtitleOff = true
                stickySubtitleLanguage = null
            } else {
                stickySubtitleOff = false
                stickySubtitleLanguage =
                    _state.value.subtitleTracks.firstOrNull { it.id == id }?.language
            }
            _state.update { it.copy(openSheet = null) }
        },
        onDismissSheet = { _state.update { it.copy(openSheet = null) } },

        // --- Wave 2 ------------------------------------------------------------------------
        onSetSpeed = { s ->
            noteInput()
            engine.setPlaybackSpeed(s)
            _state.update { it.copy(playbackSpeed = s) }
        },
        onSeekToPosition = { ms ->
            noteInput()
            engine.seekTo(ms)
            reportSeek(ms)
        },
        onSetSleepTimer = { ms -> setSleepTimer(ms) },
        onSetSubtitleStyle = { st ->
            engine.setSubtitleStyle(st)
            _state.update { it.copy(subtitleStyle = st) }
        },
        onSelectQuality = { q -> selectQuality(q) },
        onPlayUpNext = { item ->
            noteInput()
            (item as? Episode)?.let { onPlayNextEpisode?.invoke(it) }
        },
    )

    /** Arms or cancels the sleep timer (§18): counts down each second, then pauses at zero. */
    private fun setSleepTimer(durationMs: Long?) {
        noteInput()
        sleepTimerJob?.cancel()
        if (durationMs == null || durationMs <= 0) {
            _state.update { it.copy(sleepTimerRemainingMs = null) }
            return
        }
        _state.update { it.copy(sleepTimerRemainingMs = durationMs) }
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (isActive && remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0)) }
            }
            if (isActive) {
                engine.pause()
                current?.let { item ->
                    runCatching {
                        reporter.onImmediate(
                            item.ratingKey,
                            TimelineState.PAUSED,
                            _state.value.positionMs,
                            _state.value.durationMs,
                        )
                    }
                }
                _state.update { it.copy(sleepTimerRemainingMs = null) }
            }
        }
    }

    /**
     * Switches streaming quality (§11) and reloads at the current position. Original allows a
     * fresh direct attempt; a capped quality forces a transcode at that video bitrate.
     */
    private fun selectQuality(quality: PlaybackQuality) {
        noteInput()
        selectedMaxVideoBitrateKbps = quality.maxVideoBitrateKbps
        forceTranscode = quality.maxVideoBitrateKbps != null
        _state.update { it.copy(currentQualityLabel = quality.label) }
        if (quality.maxVideoBitrateKbps == null) fallback.reset()
        val position = _state.value.positionMs
        scope.launch { loadCurrentAttempt(position) }
    }

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
        // Stop reacting to the engine before releasing it, so a state emission during teardown can't
        // drive handleFailure()/loadCurrentAttempt() against a released engine (use-after-release).
        released = true
        engineJobs.forEach { it.cancel() }
        engineJobs.clear()
        tickJob?.cancel()
        chipJob?.cancel()
        sleepTimerJob?.cancel()

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

            // Mirror the position into the local cache so cache-first reads (next-unwatched, badges,
            // the offline continue-watching fallback) match reality immediately. A report at or past
            // the watched threshold — including the stop report at the end of an episode — marks it
            // watched and clears the resume point rather than leaving a near-end offset that would
            // resume the last few seconds forever. See TimelineReporter and CLAUDE.md section 5.
            if (TimelineReporter.isPastScrobbleThreshold(positionMs, durationMs)) {
                recordLocalWatched?.invoke(ratingKey)
            } else {
                recordLocalOffset?.invoke(ratingKey, positionMs)
            }
        }

        override suspend fun scrobble(ratingKey: String) {
            recordLocalWatched?.invoke(ratingKey)
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
        /**
         * The streaming-quality choices offered per video in the overlay (§11).
         *
         * There are only two, and by design there is no tier above Original: the file on the server
         * is the whole of the information that exists for a title, so a playback path can send it
         * untouched (direct play) or the server can transcode it *down* to fit a constrained
         * connection — it can never manufacture detail the source never held. So "Original" is the
         * ceiling, and "Data Saver" is a capped transcode for tight data. See CLAUDE.md section 10.
         */
        val QUALITY_LADDER = listOf(
            // Direct play, full fidelity — the source file straight from the server.
            PlaybackQuality("Original", null),
            // A firm cap (~2 Mbps, 720p) that stays watchable while cutting data use hard.
            PlaybackQuality("Data Saver", 2_000),
        )

        const val TICK_MS = 500L
        const val CONTROLS_TIMEOUT_MS = 3_000L
        const val TRANSCODE_CHIP_MS = 4_000L
        const val SEEK_BACK_MS = -10_000L
        const val SEEK_FORWARD_MS = 30_000L
        const val SEEK_TAP_FORWARD_MS = 10_000L
    }
}
