package com.thotapalli.plex.ui.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.thotapalli.plex.core.api.PlexServerSource
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
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
    modifier: Modifier = Modifier,
) {
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
        )
        // The credit skip, the countdown and a natural end all route the next episode here.
        built.onPlayNextEpisode = { next -> target = PlayTarget(next, next.viewOffsetMs) }
        controller = built
    }

    // Start playback for the current target, and again whenever it changes. The engine and
    // surface are reused across episodes, which is what keeps the surface from being redrawn.
    LaunchedEffect(controller, target) {
        val active = controller ?: return@LaunchedEffect
        val media = target.item
        val detail = runCatching { container.repository.detail(serverScope, media.ratingKey) }.getOrNull()
        val nextEpisode = runCatching { nextEpisodeFor(container.serverApi, serverScope, media) }.getOrNull()
        active.start(media, detail, nextEpisode, target.startAtMs)
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
            // The desktop video is a heavyweight native window the Compose overlay cannot
            // paint over, so controls sit in a persistent bar beneath the picture.
            Column(modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    VideoSurface(bind = { engine = it }, modifier = Modifier.fillMaxSize())
                }
                DesktopControlBar(state = screenState, actions = actions, onToggleFullScreen = onToggleFullScreen, isFullScreen = isFullScreen)
            }
        } else {
            Box(
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    // Any touch reveals the controls. Buttons in the overlay consume their
                    // own taps, so this only fires for the picture itself. See section 12.
                    .pointerInput(controller) {
                        detectTapGestures(onPress = { controller?.noteInput() })
                    },
            ) {
                VideoSurface(bind = { engine = it }, modifier = Modifier.fillMaxSize())

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
