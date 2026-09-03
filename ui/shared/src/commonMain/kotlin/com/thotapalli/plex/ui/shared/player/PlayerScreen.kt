package com.thotapalli.plex.ui.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.thotapalli.plex.core.api.PlexServerSource
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.input.HeldSeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlayerColours
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.formatPosition
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * The player screen from CLAUDE.md section 14 item 7.
 *
 * This is the seam that finally connects the fully built player to the rest of the app: it
 * creates the platform engine and video surface, wires them to a [PlaybackController], and
 * draws the [PlayerOverlay] above the surface. Everything below it — timeline reporting,
 * markers, auto-play, the transcode fallback — already exists and simply runs once started.
 *
 * The screen always renders on the dark tokens; the player ignores the light theme.
 * See CLAUDE.md section 12.
 */
@Composable
fun PlayerScreen(
    container: AppContainer,
    item: MediaItem,
    serverScope: ServerScope,
    urls: PlexUrls,
    startAtMs: Long,
    onExit: () -> Unit,
    onToggleFullScreen: () -> Unit = {},
    isFullScreen: Boolean = false,
    /** Emits when connectivity returns, so a failed stream retries from its last position without an
     *  app restart (§10, #1). Null on targets that do not surface network changes. */
    networkRegained: SharedFlow<Unit>? = null,
    /** True while the activity is folded into a Picture-in-Picture window (mobile only). The video
     *  keeps rendering, but the transport overlay and any open track sheet are hidden: a PiP window
     *  is too small for controls, and the system supplies its own. See the mobile MainActivity. */
    collapseControls: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // The player owns the screen in landscape; leaving it hands orientation back to the system.
    // No-op on the desktop. See CLAUDE.md section 14 and the mobile orientation requirement.
    LandscapeWhilePlaying()

    // The engine is created on the platform side and handed back through VideoSurface.
    var engine by remember { mutableStateOf<PlayerEngine?>(null) }
    var controller by remember { mutableStateOf<PlaybackController?>(null) }

    // What is playing now. Auto-play-next swaps this to the following episode without ever
    // tearing down the engine or the surface. See CLAUDE.md section 8.
    var target by remember { mutableStateOf(PlayTarget(item, startAtMs)) }

    // Build the controller once, as soon as the engine exists. A monotonic clock is enough:
    // the reporter throttle and the auto-play countdown both work on elapsed differences.
    LaunchedEffect(engine) {
        val engineNow = engine ?: return@LaunchedEffect
        val origin = TimeSource.Monotonic.markNow()
        val sessionId = container.identity.newSessionIdentifier()
        val built = PlaybackController(
            engine = engineNow,
            api = container.serverApi,
            serverScope = serverScope,
            urls = urls,
            sessionIdentifier = sessionId,
            identityHeaders = container.identity.playbackHeaders(sessionId),
            scope = container.scope,
            nowMs = { origin.elapsedNow().inWholeMilliseconds },
            offlineTimeline = container.offlineTimeline,
            // Apply the viewer's playback settings (§9 display-rate match; §14.9 preferred
            // languages + subtitles-on default) to the engine's initial selection.
            matchDisplayRate = container.settings.matchDisplayRate,
            preferredAudioLanguage = container.settings.preferredAudioLanguage.ifBlank { null },
            preferredSubtitleLanguage = container.settings.preferredSubtitleLanguage.ifBlank { null },
            subtitlesOnByDefault = container.settings.subtitlesOnByDefault,
            // Offline playback (#1) + the saved subtitle appearance (#14).
            offlineResolver = container.offlineResolver,
            initialSubtitleStyle = com.thotapalli.plex.core.playback.SubtitleStyle(
                scalePercent = container.settings.subtitleScalePercent,
                foregroundArgb = container.settings.subtitleForegroundArgb,
                backgroundOpacityPercent = container.settings.subtitleBackgroundOpacityPercent,
            ),
            // Keep the local cache in step with playback, so the detail screen's "next unwatched",
            // the library watched badges and the offline continue-watching fallback reflect what was
            // just watched instead of resuming an already-finished episode/section. See §5.
            recordLocalOffset = { ratingKey, positionMs ->
                container.repository.recordLocalOffset(ratingKey, positionMs)
            },
            recordLocalWatched = { ratingKey: String ->
                container.repository.recordLocalWatched(ratingKey)
            },
        )
        // The credit skip, the countdown and a natural end all route the next episode here.
        built.onPlayNextEpisode = { next -> target = PlayTarget(next, next.viewOffsetMs) }
        // The explicit previous-episode transport control routes the prior episode here, mirroring
        // next: the engine and surface are reused, so no teardown happens on an episode change.
        built.onPlayPreviousEpisode = { previous -> target = PlayTarget(previous, previous.viewOffsetMs) }
        controller = built
    }

    // When connectivity returns, retry a stream that had failed — so a dropped server or Wi-Fi
    // recovers on its own from the last position, with no app restart (§10, #1). retry() is a no-op
    // unless the controller is actually in the failed state.
    LaunchedEffect(controller, networkRegained) {
        val active = controller ?: return@LaunchedEffect
        networkRegained?.collect { active.retry() }
    }

    // Start playback for the current target, and again whenever it changes. The engine and
    // surface are reused across episodes, which is what keeps the surface from being redrawn.
    LaunchedEffect(controller, target) {
        val active = controller ?: return@LaunchedEffect
        val media = target.item
        val detail = runCatching { container.repository.detail(serverScope, media.ratingKey) }.getOrNull()
        val previousEpisode = runCatching { previousEpisodeFor(container.serverApi, serverScope, media) }.getOrNull()
        val nextEpisode = runCatching { nextEpisodeFor(container.serverApi, serverScope, media) }.getOrNull()
        active.start(media, detail, previousEpisode, nextEpisode, target.startAtMs)
    }

    // Sends state=stopped and releases the engine when the screen leaves. The work runs on
    // the container scope so it survives this composition being torn down. See CLAUDE.md
    // section 5.
    DisposableEffect(Unit) {
        onDispose {
            val active = controller
            val engineNow = engine
            container.scope.launch {
                if (active != null) active.stopAndRelease() else engineNow?.release()
            }
        }
    }

    val idleState = remember { MutableStateFlow(PlayerScreenState()) }
    val screenState by (controller?.state ?: idleState).collectAsState()
    val actions = remember(controller, onExit) {
        (controller?.actions() ?: PlayerActions()).copy(onBack = onExit)
    }

    // The player screen ignores the light theme and always renders on the dark tokens, so the
    // whole surface — the letterbox around the video, the loading state and every control — is
    // forced dark regardless of the system setting. This is also what stops a white flash before
    // the first frame: the ambient background and content colours are the dark tokens, never the
    // light ones. See CLAUDE.md section 12.
    ThotapalliTheme(forceDark = true) {
        if (container.isDesktop) {
            // The desktop player is a single window: mpv renders the video into a GL framebuffer the
            // VideoSurface owns, and the overlay below is composited straight over it in that same
            // framebuffer (see VideoSurface.jvm). So the controls are handed to VideoSurface rather
            // than drawn here — the outer Compose window cannot paint over the GL surface, and no
            // second window is involved. Full screen is app-level, supplied through the screen's
            // params, so it is wired onto the overlay's toggle here.
            val desktopState = screenState.copy(
                showFullScreenToggle = true,
                isFullScreen = isFullScreen,
            )
            val desktopActions = remember(actions, onToggleFullScreen) {
                actions.copy(onToggleFullScreen = onToggleFullScreen)
            }

            // The main window owns the keyboard (the GL canvas is non-focusable), so its shortcut
            // handler still reaches the live actions through the bridge. Only the actions and the
            // "a video is active" flag are needed now; the overlay itself no longer lives there.
            LaunchedEffect(controller, desktopActions) {
                controller?.let { c ->
                    container.playerBridge.publish(c.state, desktopActions, onActivity = { c.noteInput() })
                }
            }
            DisposableEffect(Unit) { onDispose { container.playerBridge.clear() } }

            Box(modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(
                    bind = { engine = it },
                    onPointerActivity = { controller?.noteInput() },
                    modifier = Modifier.fillMaxSize(),
                    overlay = {
                        // A separate composition inside the GL surface, so it sets up its own theme.
                        ThotapalliTheme(forceDark = true) {
                            Box(Modifier.fillMaxSize()) {
                                PlayerOverlay(
                                    state = desktopState,
                                    actions = desktopActions,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                when (desktopState.openSheet) {
                                    TrackSheetKind.AUDIO -> TrackSheet(
                                        title = "Audio",
                                        tracks = desktopState.audioTracks,
                                        allowNone = false,
                                        onSelect = desktopActions.onSelectAudioTrack,
                                        onDismiss = desktopActions.onDismissSheet,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    TrackSheetKind.SUBTITLE -> TrackSheet(
                                        title = "Subtitles",
                                        tracks = desktopState.subtitleTracks,
                                        allowNone = true,
                                        onSelect = desktopActions.onSelectSubtitleTrack,
                                        onDismiss = desktopActions.onDismissSheet,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    null -> Unit
                                }
                            }
                        }
                    },
                )
            }
        } else {
            // Television has no pointer, so the remote drives the player through key events:
            // any key wakes the overlay, the centre button plays or pauses, and left/right jump
            // ten seconds. The player box takes first focus so those keys reach it. On phone and
            // tablet this modifier is absent and touch gestures in the overlay do the same work.
            // See CLAUDE.md section 12 (overlay trigger) and section 13 (television input).
            val playerFocus = remember { FocusRequester() }
            LaunchedEffect(controller) {
                if (container.isTelevision) runCatching { playerFocus.requestFocus() }
            }
            // When a track sheet closes, hand focus back to the player so the remote never lands on
            // nothing (reference §40, §49) — no dead-end after Audio/Subtitles.
            LaunchedEffect(screenState.openSheet) {
                if (container.isTelevision && screenState.openSheet == null) {
                    runCatching { playerFocus.requestFocus() }
                }
            }

            // Holding left or right on the D-pad scrubs at thirty seconds per 400 ms. HeldSeek
            // turns the elapsed hold time into steps; a short press instead does a single quick
            // seek. A monotonic clock keeps the cadence identical on every remote, independent of
            // its key-repeat rate. See CLAUDE.md section 13.5.
            val heldSeekOrigin = remember { TimeSource.Monotonic.markNow() }
            val heldSeek = remember { HeldSeek(nowMs = { heldSeekOrigin.elapsedNow().inWholeMilliseconds }) }
            // Whether the current hold has already produced at least one step. When it has, the
            // key-up is the end of a scrub and must not also fire the short-press seek.
            var steppedThisPress by remember { mutableStateOf(false) }

            // Poll while a direction key is held, applying each thirty-second step as a relative
            // seek. Stepping only begins once the key has been held past the long-press threshold,
            // so a tap produces no step and falls through to the short-press seek on key-up.
            if (container.isTelevision) {
                LaunchedEffect(actions) {
                    while (true) {
                        if (heldSeek.isHeld) {
                            val step = heldSeek.stepMs()
                            if (step != 0L) {
                                steppedThisPress = true
                                actions.onSeekRelative(step)
                            }
                        }
                        delay(HELD_SEEK_POLL_MS)
                    }
                }
            }

            val remoteControl = if (container.isTelevision) {
                Modifier
                    .focusRequester(playerFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val c = controller ?: return@onPreviewKeyEvent false
                        val isDirectionSeek = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                val wasVisible = screenState.controlsVisible
                                // Back is layered (reference §36, §48): an open track sheet closes
                                // first, then visible controls hide, and only a Back on a bare picture
                                // exits playback (propagated, not consumed). Handled before noteInput
                                // so Back never itself wakes the controls.
                                if (event.key == Key.Back || event.key == Key.Escape) {
                                    return@onPreviewKeyEvent when {
                                        screenState.openSheet != null -> { actions.onDismissSheet(); true }
                                        wasVisible -> { actions.onToggleControls(); true }
                                        else -> false
                                    }
                                }
                                c.noteInput()
                                when (event.key) {
                                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                                        actions.onPlayPause(); true
                                    }
                                    Key.MediaFastForward -> { actions.onSeekForward10(); true }
                                    Key.MediaRewind -> { actions.onSeekBack(); true }
                                    // Begin a held seek. The key repeats while held; only the first
                                    // press (when nothing is held yet) resets the stepped flag, so a
                                    // repeat cannot clear a hold that has already stepped.
                                    Key.DirectionRight -> {
                                        if (!heldSeek.isHeld) steppedThisPress = false
                                        heldSeek.onKeyDown(forward = true); true
                                    }
                                    Key.DirectionLeft -> {
                                        if (!heldSeek.isHeld) steppedThisPress = false
                                        heldSeek.onKeyDown(forward = false); true
                                    }
                                    // A first press on a resting screen only wakes the controls.
                                    else -> !wasVisible
                                }
                            }
                            KeyEventType.KeyUp -> {
                                if (isDirectionSeek) {
                                    // A short press produced no step: do a single quick seek.
                                    if (!steppedThisPress) {
                                        if (event.key == Key.DirectionRight) actions.onSeekForward10()
                                        else actions.onSeekBack()
                                    }
                                    heldSeek.onKeyUp()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
            } else {
                Modifier
            }

            Box(
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(remoteControl),
            ) {
                VideoSurface(bind = { engine = it }, onPointerActivity = {}, modifier = Modifier.fillMaxSize())

                // In Picture-in-Picture the window is a thumbnail: the transport overlay and any open
                // sheet are suppressed so only the picture shows, and the system's own PiP controls
                // stand in. Everything below the surface keeps running, so leaving PiP restores the
                // controls with no reload.
                if (!collapseControls) {
                    PlayerOverlay(state = screenState, actions = actions, modifier = Modifier.fillMaxSize())

                    when (screenState.openSheet) {
                        TrackSheetKind.AUDIO -> TrackSheet(
                            title = "Audio",
                            tracks = screenState.audioTracks,
                            allowNone = false,
                            onSelect = actions.onSelectAudioTrack,
                            onDismiss = actions.onDismissSheet,
                            modifier = Modifier.fillMaxSize(),
                        )
                        TrackSheetKind.SUBTITLE -> TrackSheet(
                            title = "Subtitles",
                            tracks = screenState.subtitleTracks,
                            allowNone = true,
                            onSelect = actions.onSelectSubtitleTrack,
                            onDismiss = actions.onDismissSheet,
                            modifier = Modifier.fillMaxSize(),
                        )
                        null -> Unit
                    }
                }
            }
        }
    }
}

/**
 * The desktop player's controls, in a persistent bar beneath the video — the picture is a
 * heavyweight native window the Compose overlay cannot draw over. Scrubber and times on top,
 * transport and title below. Always on the dark player tokens.
 */
@Composable
private fun DesktopControlBar(
    state: PlayerScreenState,
    actions: PlayerActions,
    onToggleFullScreen: () -> Unit,
    isFullScreen: Boolean,
) {
    // Subtitles start on when the file carries them; the toggle flips mpv's sid.
    var subtitlesOn by remember { mutableStateOf(true) }
    val colours = PlayerColours
    val duration = state.durationMs.coerceAtLeast(1L)
    var dragMs by remember { mutableStateOf<Long?>(null) }
    val shownMs = (dragMs ?: state.displayPositionMs).coerceIn(0L, duration)

    Column(
        Modifier
            .fillMaxWidth()
            .background(colours.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlexText(formatPosition(shownMs), style = PlexTheme.type.caption, colour = colours.textSecondary)
            Slider(
                value = shownMs.toFloat() / duration.toFloat(),
                onValueChange = { fraction ->
                    if (dragMs == null) actions.onScrubStart()
                    val ms = (fraction * duration).toLong()
                    dragMs = ms
                    actions.onScrub(ms)
                },
                onValueChangeFinished = {
                    dragMs?.let { actions.onScrubEnd(it) }
                    dragMs = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = colours.accent,
                    activeTrackColor = colours.accent,
                    inactiveTrackColor = colours.border,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
            )
            PlexText(formatPosition(duration), style = PlexTheme.type.caption, colour = colours.textSecondary)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BarIcon(PlexIconKind.BACK, actions.onBack)
            Column(Modifier.weight(1f)) {
                if (state.title.isNotBlank()) {
                    PlexText(state.title, style = PlexTheme.type.label, colour = colours.textPrimary, maxLines = 1)
                }
                state.subtitle?.takeIf { it.isNotBlank() }?.let {
                    PlexText(it, style = PlexTheme.type.caption, colour = colours.textSecondary, maxLines = 1)
                }
            }
            SubtitleToggle(on = subtitlesOn) {
                subtitlesOn = !subtitlesOn
                actions.onSelectSubtitleTrack(if (subtitlesOn) "1" else null)
            }
            BarText("−10s", actions.onSeekBack)
            BarIcon(
                kind = if (state.isPlaying) PlexIconKind.PAUSE else PlexIconKind.PLAY,
                onClick = actions.onPlayPause,
                big = true,
            )
            BarText("+30s", actions.onSeekForward)
            BarIcon(
                kind = if (isFullScreen) PlexIconKind.FULLSCREEN_EXIT else PlexIconKind.FULLSCREEN,
                onClick = onToggleFullScreen,
            )
        }
    }
}

@Composable
private fun BarIcon(kind: PlexIconKind, onClick: () -> Unit, big: Boolean = false) {
    val colours = PlayerColours
    Box(
        Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(if (big) colours.accent else colours.surfaceElevated, Radius.pill)
            .padding(if (big) Spacing.sm else Spacing.xs),
    ) {
        PlexIcon(
            kind = kind,
            tint = if (big) colours.background else colours.textPrimary,
            size = if (big) 28.dp else 22.dp,
        )
    }
}

@Composable
private fun SubtitleToggle(on: Boolean, onClick: () -> Unit) {
    val colours = PlayerColours
    Box(
        Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(if (on) colours.accent else colours.surfaceElevated, Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        PlexText(
            "CC",
            style = PlexTheme.type.label,
            colour = if (on) colours.background else colours.textPrimary,
        )
    }
}

@Composable
private fun BarText(label: String, onClick: () -> Unit) {
    val colours = PlayerColours
    Box(
        Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(colours.surfaceElevated, Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        PlexText(label, style = PlexTheme.type.label, colour = colours.textPrimary)
    }
}

/** How often the television held-D-pad scrub polls HeldSeek for its next step. */
private const val HELD_SEEK_POLL_MS = 100L

private data class PlayTarget(val item: MediaItem, val startAtMs: Long)

/**
 * The next episode to auto-play, or null when there is none.
 *
 * Only an episode has a successor: the next one in the show's flat episode order. A movie
 * never does. See CLAUDE.md section 8, auto-play next.
 */
private suspend fun nextEpisodeFor(
    api: PlexServerSource,
    scope: ServerScope,
    item: MediaItem,
): Episode? {
    if (item !is Episode) return null
    val all = api.allEpisodes(scope, item.showRatingKey)
    val index = all.indexOfFirst { it.ratingKey == item.ratingKey }
    return if (index >= 0 && index + 1 < all.size) all[index + 1] else null
}

/**
 * The previous episode, or null when there is none — the mirror of [nextEpisodeFor]: the one
 * before the current episode in the show's flat episode order. A movie never has one, and neither
 * does the first episode of a show. See CLAUDE.md section 8.
 */
private suspend fun previousEpisodeFor(
    api: PlexServerSource,
    scope: ServerScope,
    item: MediaItem,
): Episode? {
    if (item !is Episode) return null
    val all = api.allEpisodes(scope, item.showRatingKey)
    val index = all.indexOfFirst { it.ratingKey == item.ratingKey }
    return if (index >= 1) all[index - 1] else null
}
