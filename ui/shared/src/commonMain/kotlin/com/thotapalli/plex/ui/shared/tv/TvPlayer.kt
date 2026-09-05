package com.thotapalli.plex.ui.shared.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.AppContainer
import com.thotapalli.plex.ui.shared.formatPosition
import com.thotapalli.plex.ui.shared.input.HeldSeek
import com.thotapalli.plex.ui.shared.player.PlaybackController
import com.thotapalli.plex.ui.shared.player.PlayerActions
import com.thotapalli.plex.ui.shared.player.PlayerScreenState
import com.thotapalli.plex.ui.shared.player.TrackSheetKind
import com.thotapalli.plex.ui.shared.player.VideoSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * The television player. Same engine, same [PlaybackController], same timeline reporting and
 * markers as every other target — only what the remote does is rebuilt.
 *
 * The previous television player held focus on one node that swallowed every key, so the
 * buttons drawn on the overlay could never be reached. This one is a small set of focus owners,
 * exactly one of which holds the remote at a time:
 *
 *  - SURFACE, while the controls are hidden. The only place a direction key is consumed for
 *    something other than focus: select plays or pauses, left and right seek (a hold scrubs), and
 *    anything else wakes the controls.
 *  - OVERLAY, while the controls are shown. Real buttons, real focus: left and right walk the
 *    transport, up reaches the seek bar and the skip button, select activates. On the seek bar,
 *    left and right seek — a slider's native meaning — and up/down leave it.
 *  - SKIP, when an intro or credits marker is active and the controls are hidden: the skip button
 *    takes focus so one press of select skips, the way every television app does it.
 *  - NEXT UP, when the auto-play countdown starts: the card takes focus with Play now and Cancel
 *    as buttons. Stepping between them keeps the countdown running; anything else cancels it.
 *  - TRACKS, while the audio or subtitle picker is open.
 *
 * Back is layered at the root: picker, then countdown, then controls, then the player itself.
 * Every rule here is a declaration of who owns focus, never a key intercepted on the way to a
 * button. See CLAUDE.md sections 8, 12, 13 and 14 item 7.
 */
@Composable
internal fun TvPlayer(
    container: AppContainer,
    item: MediaItem,
    serverScope: ServerScope,
    urls: PlexUrls,
    startAtMs: Long,
    onExit: () -> Unit,
    networkRegained: SharedFlow<Unit>? = null,
    modifier: Modifier = Modifier,
) {
    var engine by remember { mutableStateOf<PlayerEngine?>(null) }
    var controller by remember { mutableStateOf<PlaybackController?>(null) }
    var target by remember { mutableStateOf(PlayTarget(item, startAtMs)) }

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
            matchDisplayRate = container.settings.matchDisplayRate,
            preferredAudioLanguage = container.settings.preferredAudioLanguage.ifBlank { null },
            preferredSubtitleLanguage = container.settings.preferredSubtitleLanguage.ifBlank { null },
            subtitlesOnByDefault = container.settings.subtitlesOnByDefault,
            offlineResolver = container.offlineResolver,
            initialSubtitleStyle = com.thotapalli.plex.core.playback.SubtitleStyle(
                scalePercent = container.settings.subtitleScalePercent,
                foregroundArgb = container.settings.subtitleForegroundArgb,
                backgroundOpacityPercent = container.settings.subtitleBackgroundOpacityPercent,
            ),
            recordLocalOffset = { ratingKey, positionMs -> container.repository.recordLocalOffset(ratingKey, positionMs) },
            recordLocalWatched = { ratingKey: String -> container.repository.recordLocalWatched(ratingKey) },
        )
        built.onPlayNextEpisode = { next -> target = PlayTarget(next, next.viewOffsetMs) }
        built.onPlayPreviousEpisode = { previous -> target = PlayTarget(previous, previous.viewOffsetMs) }
        controller = built
    }

    LaunchedEffect(controller, networkRegained) {
        val active = controller ?: return@LaunchedEffect
        networkRegained?.collect { active.retry() }
    }

    LaunchedEffect(controller, target) {
        val active = controller ?: return@LaunchedEffect
        val media = target.item
        val detail = runCatching { container.repository.detail(serverScope, media.ratingKey) }.getOrNull()
        val previous = runCatching { adjacentEpisode(container, serverScope, media, -1) }.getOrNull()
        val next = runCatching { adjacentEpisode(container, serverScope, media, +1) }.getOrNull()
        active.start(media, detail, previous, next, target.startAtMs)
    }

    DisposableEffect(Unit) {
        onDispose {
            val active = controller
            val engineNow = engine
            container.scope.launch { if (active != null) active.stopAndRelease() else engineNow?.release() }
        }
    }

    val idleState = remember { MutableStateFlow(PlayerScreenState()) }
    val state by (controller?.state ?: idleState).collectAsState()
    val actions = remember(controller, onExit) { (controller?.actions() ?: PlayerActions()).copy(onBack = onExit) }

    ThotapalliTheme(forceDark = true) {
        TvPlayerSurface(
            controller = controller,
            state = state,
            actions = actions,
            bindEngine = { engine = it },
            modifier = modifier,
        )
    }
}

private enum class Owner { SURFACE, OVERLAY, SKIP, NEXT_UP, TRACKS, ERROR }

/** Where focus goes when the controls wake: the seek bar after a seek key, else play/pause. */
private enum class WakeTarget { PLAY, SEEK }

@Composable
private fun TvPlayerSurface(
    controller: PlaybackController?,
    state: PlayerScreenState,
    actions: PlayerActions,
    bindEngine: (PlayerEngine) -> Unit,
    modifier: Modifier,
) {
    val surfaceFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val nextUpZone = remember { TvZoneState(parent = null) }
    val tracksZone = remember { TvZoneState(parent = null) }
    val errorZone = remember { TvZoneState(parent = null) }
    var wakeTarget by remember { mutableStateOf(WakeTarget.PLAY) }

    val skipVisible = state.showSkipIntro || state.showSkipCredits
    val owner = when {
        state.errorMessage != null -> Owner.ERROR
        state.openSheet != null -> Owner.TRACKS
        state.showNextEpisodePrompt -> Owner.NEXT_UP
        state.controlsVisible -> Owner.OVERLAY
        skipVisible -> Owner.SKIP
        else -> Owner.SURFACE
    }

    // Exactly one owner holds the remote. Re-seated only when the owner changes, so moving
    // between buttons inside the overlay is never disturbed.
    LaunchedEffect(owner, controller) {
        delay(OWNER_SETTLE_MS)
        when (owner) {
            Owner.SURFACE -> runCatching { surfaceFocus.requestFocus() }
            Owner.OVERLAY -> runCatching {
                if (wakeTarget == WakeTarget.SEEK) seekFocus.requestFocus() else playFocus.requestFocus()
            }
            Owner.SKIP -> runCatching { skipFocus.requestFocus() }
            Owner.NEXT_UP -> nextUpZone.requestFocus()
            Owner.TRACKS -> tracksZone.requestFocus()
            Owner.ERROR -> errorZone.requestFocus()
        }
    }

    // Held left/right on the idle surface or the seek bar scrubs at thirty seconds per 400 ms.
    val heldOrigin = remember { TimeSource.Monotonic.markNow() }
    val heldSeek = remember { HeldSeek(nowMs = { heldOrigin.elapsedNow().inWholeMilliseconds }) }
    var stepped by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(actions) {
        while (true) {
            if (heldSeek.isHeld) {
                val step = heldSeek.stepMs()
                if (step != 0L) {
                    if (!stepped) {
                        stepped = true
                        actions.onScrubStart()
                        scrubMs = state.positionMs
                    }
                    val next = ((scrubMs ?: state.positionMs) + step).coerceIn(0L, state.durationMs.coerceAtLeast(0L))
                    scrubMs = next
                    actions.onScrub(next)
                }
            }
            delay(HELD_POLL_MS)
        }
    }
    // A short press seeks; a hold that scrubbed commits where it landed.
    val seekKeys: (KeyEvent) -> Boolean = handler@{ event ->
        val forward = when (event.key) {
            Key.DirectionRight -> true
            Key.DirectionLeft -> false
            else -> return@handler false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (!heldSeek.isHeld) stepped = false
                heldSeek.onKeyDown(forward)
                controller?.noteNavigation()
                true
            }
            KeyEventType.KeyUp -> {
                heldSeek.onKeyUp()
                if (stepped) {
                    scrubMs?.let { actions.onScrubEnd(it) }
                    scrubMs = null
                } else {
                    if (forward) actions.onSeekForward() else actions.onSeekBack()
                }
                true
            }
            else -> false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val c = controller ?: return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    // Back is layered: picker, countdown, controls, then the player itself (not consumed).
                    Key.Back, Key.Escape -> when {
                        state.openSheet != null -> { if (down) actions.onDismissSheet(); true }
                        state.showNextEpisodePrompt -> { if (down) actions.onCancelAutoPlay(); true }
                        state.controlsVisible -> { if (down) c.toggleControls(); true }
                        else -> false
                    }
                    // Media keys work from anywhere.
                    Key.MediaPlayPause -> { if (down) actions.onPlayPause(); true }
                    Key.MediaPlay -> { if (down && !state.isPlaying) actions.onPlayPause(); true }
                    Key.MediaPause -> { if (down && state.isPlaying) actions.onPlayPause(); true }
                    Key.MediaFastForward -> { if (down) actions.onSeekForward(); true }
                    Key.MediaRewind -> { if (down) actions.onSeekBack(); true }
                    Key.MediaNext -> { if (down && state.hasNextEpisode) actions.onPlayNextEpisodeNow(); true }
                    Key.MediaPrevious -> { if (down && state.hasPreviousEpisode) actions.onPlayPreviousEpisode(); true }
                    // Direction keys keep the controls awake and then go to focus search, untouched.
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                        if (down && state.controlsVisible) c.noteNavigation()
                        false
                    }
                    else -> false
                }
            },
    ) {
        VideoSurface(bind = bindEngine, onPointerActivity = {}, modifier = Modifier.fillMaxSize())

        // The idle surface. Holds focus while nothing else does; the one place direction keys mean
        // something other than "move focus".
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(surfaceFocus)
                .onKeyEvent { event ->
                    val c = controller ?: return@onKeyEvent false
                    if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                        wakeTarget = WakeTarget.SEEK
                        return@onKeyEvent seekKeys(event)
                    }
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            wakeTarget = WakeTarget.PLAY
                            actions.onPlayPause()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            wakeTarget = WakeTarget.PLAY
                            c.noteInput()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        )

        // The controls.
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            TvPlayerOverlay(
                state = state,
                actions = actions,
                controller = controller,
                playFocus = playFocus,
                seekFocus = seekFocus,
                seekKeys = seekKeys,
                scrubMs = scrubMs,
            )
        }

        // Skip intro / credits: bottom right, above the transport, focused on its own when the
        // controls are hidden so one press of select skips.
        AnimatedVisibility(
            visible = skipVisible && state.openSheet == null && !state.showNextEpisodePrompt,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvDims.overscanX, bottom = SKIP_BOTTOM),
        ) {
            TvButton(
                label = if (state.showSkipIntro) "Skip intro" else "Skip credits",
                key = "skip",
                primary = true,
                focusRequester = skipFocus,
                onClick = { if (state.showSkipIntro) actions.onSkipIntro() else actions.onSkipCredits() },
                modifier = Modifier.onKeyEvent { event ->
                    // A direction key on the lone skip button wakes the controls instead of going nowhere.
                    val direction = event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                    if (direction && !state.controlsVisible && event.type == KeyEventType.KeyDown) {
                        wakeTarget = WakeTarget.PLAY
                        controller?.noteInput()
                        true
                    } else {
                        false
                    }
                },
            )
        }

        // Up next: the ten second countdown with Play now and Cancel as buttons.
        if (state.showNextEpisodePrompt) {
            TvNextUp(
                title = state.nextEpisodeTitle.orEmpty(),
                secondsRemaining = state.countdownSeconds,
                zone = nextUpZone,
                onPlayNow = actions.onPlayNext,
                onCancel = actions.onCancelAutoPlay,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvDims.overscanX, bottom = TvDims.overscanY + Spacing.lg),
            )
        }

        // The audio or subtitle picker.
        state.openSheet?.let { sheet ->
            TvTrackPanel(
                title = if (sheet == TrackSheetKind.AUDIO) "Audio" else "Subtitles",
                tracks = if (sheet == TrackSheetKind.AUDIO) state.audioTracks else state.subtitleTracks,
                allowNone = sheet == TrackSheetKind.SUBTITLE,
                zone = tracksZone,
                onSelect = if (sheet == TrackSheetKind.AUDIO) actions.onSelectAudioTrack else actions.onSelectSubtitleTrack,
                onDismiss = actions.onDismissSheet,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Transcoding chip, lower left, fades on its own.
        AnimatedVisibility(
            visible = state.showTranscodingChip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = TvDims.overscanX, bottom = TvDims.overscanY),
        ) {
            Box(Modifier.clip(TvShape.pill).background(Color(0xB3000000)).padding(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                PlexText("Transcoding", style = PlexTheme.type.caption, colour = TvPalette.textDim)
            }
        }

        state.errorMessage?.let { message ->
            TvPlayerError(
                message = message,
                zone = errorZone,
                onRetry = { controller?.retry() },
                onBack = actions.onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// --- overlay --------------------------------------------------------------------------------

@Composable
private fun TvPlayerOverlay(
    state: PlayerScreenState,
    actions: PlayerActions,
    controller: PlaybackController?,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    seekKeys: (KeyEvent) -> Boolean,
    scrubMs: Long?,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6000000))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = TvDims.overscanX, vertical = TvDims.overscanY),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Title and, for an episode, the show line.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.title.isNotBlank()) {
                    PlexText(state.title, style = PlexTheme.type.title, colour = TvPalette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.subtitle?.takeIf { it.isNotBlank() }?.let {
                    PlexText(it, style = PlexTheme.type.label, colour = TvPalette.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            TvSeekBar(
                state = state,
                scrubMs = scrubMs,
                focusRequester = seekFocus,
                onKey = seekKeys,
                onSelect = actions.onPlayPause,
                onFocused = { controller?.noteNavigation() },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.hasPreviousEpisode) {
                    TvIconButton(TvGlyph.SKIP_PREVIOUS, key = "prev", onClick = actions.onPlayPreviousEpisode, contentDescription = "Previous episode")
                }
                TvIconButton(TvGlyph.REPLAY_10, key = "back10", onClick = actions.onSeekBack, contentDescription = "Back 10 seconds")
                TvIconButton(
                    glyph = if (state.isPlaying) TvGlyph.PAUSE else TvGlyph.PLAY,
                    key = "play",
                    size = 64.dp,
                    accent = true,
                    focusRequester = playFocus,
                    onClick = actions.onPlayPause,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
                TvIconButton(TvGlyph.FORWARD_30, key = "fwd30", onClick = actions.onSeekForward, contentDescription = "Forward 30 seconds")
                if (state.hasNextEpisode) {
                    TvIconButton(TvGlyph.SKIP_NEXT, key = "next", onClick = actions.onPlayNextEpisodeNow, contentDescription = "Next episode")
                }
                Spacer(Modifier.weight(1f))
                if (state.audioTracks.size > 1) {
                    TvButton(label = "Audio", key = "audio", glyph = TvGlyph.AUDIO, onClick = actions.onOpenAudioTracks)
                }
                if (state.subtitleTracks.isNotEmpty()) {
                    TvButton(label = "Subtitles", key = "subtitles", glyph = TvGlyph.SUBTITLES, onClick = actions.onOpenSubtitleTracks)
                }
            }
        }
    }
}

/**
 * The seek bar. A focus target whose left and right mean seek — a slider's own semantics, the
 * deliberate exception to the direction-key rule — with a trickplay frame above the handle while
 * scrubbing. Up and down are untouched, so they leave the bar for the skip button or the transport.
 */
@Composable
private fun TvSeekBar(
    state: PlayerScreenState,
    scrubMs: Long?,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
    onSelect: () -> Unit,
    onFocused: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val duration = state.durationMs.coerceAtLeast(1L)
    val shown = (scrubMs ?: state.displayPositionMs).coerceIn(0L, duration)
    val fraction = shown.toFloat() / duration.toFloat()
    val thumbSize by animateFloatAsState(if (focused) 1f else 0.6f, label = "tv-seek-thumb")

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Trickplay preview while scrubbing, riding above the handle.
        if (scrubMs != null) {
            val url = state.trickplayUrlAt(scrubMs)
            if (url != null) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .padding(start = 0.dp)
                            .fillMaxWidth(fraction.coerceIn(0f, 1f)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(PREVIEW_WIDTH)
                                .height(PREVIEW_HEIGHT)
                                .clip(TvShape.card)
                                .border(2.dp, TvPalette.gold, TvShape.card),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlexText(formatPosition(shown), style = PlexTheme.type.label, colour = TvPalette.text, maxLines = 1)
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .onKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> onKey(event)
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (event.type == KeyEventType.KeyUp) onSelect()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(interactionSource = interaction),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height(if (focused) 8.dp else 5.dp).clip(TvShape.pill).background(Color(0x66FFFFFF)))
                Box(Modifier.fillMaxWidth(fraction).height(if (focused) 8.dp else 5.dp).clip(TvShape.pill).background(TvPalette.gold))
                Box(Modifier.fillMaxWidth(fraction), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier
                            .size((22 * thumbSize).dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (focused) Color.White else TvPalette.gold)
                            .then(if (focused) Modifier.border(3.dp, TvPalette.gold, androidx.compose.foundation.shape.CircleShape) else Modifier),
                    )
                }
            }
            PlexText(formatPosition(duration), style = PlexTheme.type.label, colour = TvPalette.textDim, maxLines = 1)
        }
    }
}

// --- up next, tracks, error -----------------------------------------------------------------

@Composable
private fun TvNextUp(
    title: String,
    secondsRemaining: Int,
    zone: TvZoneState,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvZone(zone) {
        TvPanel(
            modifier
                .widthIn(max = 520.dp)
                .tvZone(zone, trap = true)
                .onKeyEvent { event ->
                    // Any input but stepping between the two buttons cancels the countdown (§14.7).
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                        onCancel(); true
                    } else {
                        false
                    }
                },
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PlexText("Up next", style = PlexTheme.type.caption, colour = TvPalette.textMuted)
                PlexText(title, style = PlexTheme.type.title, colour = TvPalette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TvButton(label = "Play now ($secondsRemaining)", key = "play-now", primary = true, onClick = onPlayNow)
                    TvButton(label = "Cancel", key = "cancel", onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun TvTrackPanel(
    title: String,
    tracks: List<PlayerTrack>,
    allowNone: Boolean,
    zone: TvZoneState,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvZone(zone) {
        Box(
            modifier
                .fillMaxHeight()
                .width(420.dp)
                .background(TvPalette.surface)
                .tvZone(zone, trap = true)
                .padding(horizontal = Spacing.lg, vertical = TvDims.overscanY),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PlexText(title, style = PlexTheme.type.title, colour = TvPalette.text)
                Spacer(Modifier.height(Spacing.xs))
                if (allowNone) {
                    val off = tracks.none { it.selected }
                    TvListRow(
                        title = "Off",
                        key = "off",
                        selected = off,
                        onClick = { onSelect(null); onDismiss() },
                        trailing = { focused -> if (off) TvGlyphIcon(TvGlyph.CHECK, tint = if (focused) TvPalette.ink else TvPalette.gold, size = 22.dp) },
                    )
                }
                tracks.forEach { track ->
                    TvListRow(
                        title = track.label,
                        key = track.id,
                        selected = track.selected,
                        onClick = { onSelect(track.id); onDismiss() },
                        trailing = { focused -> if (track.selected) TvGlyphIcon(TvGlyph.CHECK, tint = if (focused) TvPalette.ink else TvPalette.gold, size = 22.dp) },
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                TvButton(label = "Close", key = "close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun TvPlayerError(
    message: String,
    zone: TvZoneState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvZone(zone) {
        TvPanel(modifier.widthIn(max = 560.dp).tvZone(zone, trap = true)) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlexText("Playback stopped", style = PlexTheme.type.title, colour = TvPalette.text)
                PlexText(message, style = PlexTheme.type.body, colour = TvPalette.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TvButton(label = "Try again", key = "retry", primary = true, onClick = onRetry)
                    TvButton(label = "Back", key = "back", onClick = onBack)
                }
            }
        }
    }
}

// --- helpers --------------------------------------------------------------------------------

private data class PlayTarget(val item: MediaItem, val startAtMs: Long)

/** The episode [offset] places after (or before) [item] in the show's flat order, or null. */
private suspend fun adjacentEpisode(
    container: AppContainer,
    scope: ServerScope,
    item: MediaItem,
    offset: Int,
): Episode? {
    if (item !is Episode) return null
    val all = container.serverApi.allEpisodes(scope, item.showRatingKey)
    val index = all.indexOfFirst { it.ratingKey == item.ratingKey }
    if (index < 0) return null
    return all.getOrNull(index + offset)
}

private const val OWNER_SETTLE_MS = 40L
private const val HELD_POLL_MS = 100L
private val SKIP_BOTTOM = 200.dp
private val PREVIEW_WIDTH = 240.dp
private val PREVIEW_HEIGHT = 135.dp
