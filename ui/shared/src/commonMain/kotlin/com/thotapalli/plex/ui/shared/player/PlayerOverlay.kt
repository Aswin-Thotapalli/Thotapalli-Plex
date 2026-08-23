package com.thotapalli.plex.ui.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Chapter
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.Motion
import com.thotapalli.plex.ui.design.PlayerColours
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.liquidGlass
import com.thotapalli.plex.ui.design.pressBubble
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.LoadingIndicator
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.formatPosition
import com.thotapalli.plex.ui.shared.plexFocusable
import kotlin.math.abs

/**
 * The player overlay from CLAUDE.md section 12, dressed in the liquid-glass language.
 *
 * Idle is nothing on screen: no bar, no clock, no logo, no title. Active is a bottom gradient
 * scrim, the title, a progress bar and transport controls, and nothing else. The overlay chrome —
 * the transport bar ([GlassRole.CHROME]), the track-selector and up-next sheets ([GlassRole.SHEET])
 * and the pill controls ([GlassRole.CHIP]) — is dressed by the material-role theme engine rather
 * than hand-picked tints; the one warm-amber accent marks only the active scrubber fill and the
 * call-to-action pills, and every control answers a press with a springy bubble.
 *
 * This is Compose content in a layer above the video surface. It never causes the surface to be
 * redrawn, and showing or hiding it never recreates the player or the surface. The video is not a
 * glass source — over full motion Haze cannot sample cheaply — so every glass panel here uses the
 * translucent tinted fill with the same specular, rim and glow, which still reads as glass over the
 * picture. See CLAUDE.md section 8 and section 12.
 */
@Composable
fun PlayerOverlay(
    state: PlayerScreenState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    // The player screen ignores the light theme and always renders on the dark tokens.
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision
    val edge = if (isTv) Spacing.xl else Spacing.lg

    // The controls fade after three seconds of no input, but only once the picture is actually
    // playing. While the player is buffering, paused, or has not yet reached the first frame
    // there is nothing to watch, so the controls stay put rather than leaving the user staring
    // at a bare surface. The controller drives the fade timer; this widens it so a non-playing
    // state always shows the controls. See CLAUDE.md section 8 and section 12.
    val controlsShown = state.controlsVisible || state.playbackState !is PlaybackState.Playing

    // Before the first frame — idle, buffering or readied but not started — cover the surface
    // with the dark ground and a centred loading treatment. This is what the user sees instead
    // of a grey word on a white panel, and it guarantees no white flash while the decoder spins
    // up. Once playback begins the cover is gone and the picture shows through. See CLAUDE.md
    // section 10 and section 12.
    val loading = when (state.playbackState) {
        is PlaybackState.Idle, is PlaybackState.Buffering, is PlaybackState.Ready -> true
        else -> false
    }

    // Once the picture has ever played, later buffering is a mid-stream re-buffer (a seek, a network
    // hiccup) with the last frame still on the surface — so we must NOT paint over it. Only the very
    // first spin-up (before any frame) gets the opaque dark cover; after that a seek shows a spinner
    // over the frozen frame rather than going black. This is the fix for "the screen goes blank when
    // I fast-forward / rewind."
    var hasStarted by remember { mutableStateOf(false) }
    LaunchedEffect(state.playbackState) {
        if (state.playbackState is PlaybackState.Playing) hasStarted = true
    }
    val coverOpaque = loading && !hasStarted

    // A brief "10s" nudge shown after a double-tap seek: -1 on the left, +1 on the right, 0 none.
    var seekHint by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekHint) {
        if (seekHint != 0) {
            delay(650)
            seekHint = 0
        }
    }

    // The device brightness/volume knobs, and which of the overlay's own sheets (speed, quality,
    // chapters, sleep, subtitle appearance, up next) is open. These sheets are self-managed here
    // rather than through the controller's audio/subtitle openSheet, so they need no plumbing
    // outside the overlay. See CLAUDE.md section 12 and section 18.
    val hardware = rememberPlayerHardware()
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var sheet by remember { mutableStateOf<OverlaySheet?>(null) }

    // A centre flash of the play or pause icon on every tap, so the toggle is unmistakable even
    // when the controls are hidden. The counter fires the effect on each tap; the icon reflects
    // the state the tap produced.
    val playPauseFlash = remember { mutableIntStateOf(0) }
    var flashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(playPauseFlash.intValue) {
        if (playPauseFlash.intValue > 0) {
            flashVisible = true
            delay(500)
            flashVisible = false
        }
    }

    Box(modifier.fillMaxSize()) {
        // The gesture bed, beneath every control. A single tap plays or pauses; a double-tap on
        // the left or right half jumps ten seconds back or forward. The buttons, seek bar and
        // sheets sit above this and consume their own taps, so it only ever fires on the picture.
        // See CLAUDE.md section 14 item 7.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        // A single tap toggles the controls — it must never pause. Pause is the
                        // transport button alone. See CLAUDE.md section 12.
                        onTap = { actions.onToggleControls() },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) {
                                actions.onSeekBack()
                                seekHint = -1
                            } else {
                                actions.onSeekForward10()
                                seekHint = 1
                            }
                        },
                    )
                }
                // Vertical-drag brightness and volume, on touch platforms only. A drag on the left
                // half rides screen brightness; a drag on the right half nudges the media volume.
                // This is a distinct gesture detector — it reacts only to vertical drags and
                // consumes only those, so the single-tap (toggle controls) and double-tap (seek)
                // above are untouched. Armed only when the platform exposes brightness, so the
                // pointer-driven desktop never grabs a vertical drag. See CLAUDE.md section 18.
                .then(
                    if (hardware.supportsBrightness) {
                        Modifier.pointerInput(Unit) {
                            var onLeftHalf = false
                            var volumeAccum = 0f
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    onLeftHalf = offset.x < size.width / 2f
                                    volumeAccum = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (onLeftHalf) {
                                        // Dragging up (negative dragAmount) brightens.
                                        val next = (brightness - dragAmount / size.height)
                                            .coerceIn(0f, 1f)
                                        brightness = next
                                        hardware.setBrightness(next)
                                    } else {
                                        // Accumulate travel and emit a step each threshold, so a
                                        // drag walks the volume up or down smoothly.
                                        volumeAccum -= dragAmount
                                        val step = size.height / 15f
                                        while (volumeAccum >= step) {
                                            hardware.nudgeVolume(up = true); volumeAccum -= step
                                        }
                                        while (volumeAccum <= -step) {
                                            hardware.nudgeVolume(up = false); volumeAccum += step
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        )

        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(Motion.playerFade()),
            exit = fadeOut(Motion.playerFade()),
        ) {
            Box(
                // Opaque dark cover only before the first frame; a mid-stream re-buffer keeps the
                // frozen frame visible behind a plain spinner.
                Modifier.fillMaxSize().then(
                    if (coverOpaque) Modifier.background(colours.background) else Modifier,
                ),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    label = if (state.playbackState is PlaybackState.Buffering) "Buffering" else "Loading",
                )
            }
        }

        // A terminal playback failure — the server or the internet dropped and there is nothing left
        // to try. Show a clear message over a dark cover with the one useful way out (go back, where
        // downloads play offline), instead of a frozen frame or a crash. See CLAUDE.md section 10 (#1).
        state.errorMessage?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colours.background.copy(alpha = 0.92f))
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    PlexText(
                        text = message,
                        style = PlexTheme.type.title,
                        colour = Color.White,
                    )
                    Box(
                        modifier = Modifier
                            .plexFocusable(shape = Radius.pill, onClick = { actions.onBack() })
                            .clip(Radius.pill)
                            .background(colours.accent, Radius.pill)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        PlexText(text = "Go back", style = PlexTheme.type.label, colour = Color.Black, maxLines = 1)
                    }
                }
            }
        }

        // Top scrim and the back control, top left. A frosted glass way out of the player.
        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(Motion.playerFade()),
            exit = fadeOut(Motion.playerFade()),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(0f to colours.scrim, 1f to Color.Transparent),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(edge),
                ) {
                    GlassIconButton(PlexIconKind.BACK, onClick = actions.onBack)
                }
            }
        }

        // The bottom cluster: gradient scrim, title, the scrubber, and the floating glass transport
        // bar. Nothing else lives here. See CLAUDE.md section 12 "Active".
        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(Motion.playerFade()),
            exit = fadeOut(Motion.playerFade()),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(280.dp)
                        .background(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to colours.scrim),
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = edge, vertical = edge),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PlexText(
                        text = state.title,
                        style = PlexTheme.type.display,
                        colour = colours.textPrimary,
                        maxLines = 1,
                    )
                    state.subtitle?.let {
                        PlexText(
                            text = it,
                            style = PlexTheme.type.body,
                            colour = colours.textSecondary,
                            maxLines = 1,
                        )
                    }

                    Spacer(Modifier.height(Spacing.xs))

                    // Position, progress bar and duration together, as CLAUDE.md section 14 item 7
                    // lists them. The scrubber sits outside the glass bar so its trickplay preview
                    // floats freely above the handle without being clipped by the glass.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PlexText(
                            text = formatPosition(state.displayPositionMs),
                            style = PlexTheme.type.caption,
                            colour = colours.textSecondary,
                        )
                        SeekBar(
                            positionMs = state.displayPositionMs,
                            durationMs = state.durationMs,
                            chapters = state.chapters,
                            trickplayUrlAt = state.trickplayUrlAt,
                            onScrubStart = actions.onScrubStart,
                            onScrub = actions.onScrub,
                            onScrubEnd = actions.onScrubEnd,
                            modifier = Modifier.weight(1f),
                        )
                        PlexText(
                            text = formatPosition(state.durationMs),
                            style = PlexTheme.type.caption,
                            colour = colours.textSecondary,
                        )
                    }

                    TransportBar(
                        state = state,
                        actions = actions,
                        onOpenSheet = { sheet = it },
                    )
                }
            }
        }

        // The large centre play/pause button, Netflix/Plex style. It shows whenever the controls
        // are up (and only once the picture is past its opaque first-frame cover), and it reflects
        // the transport state: a pause glyph while playing, a play glyph while paused or ended.
        //
        // Z-ORDER: this block is declared AFTER the gesture bed (the first child of the root Box),
        // so it paints above it, and it carries its own clickable interaction source. A clickable
        // consumes the pointer down, so a tap that lands on this button is spent here and never
        // reaches the toggle-controls gesture underneath — it is the ONLY tap target that pauses.
        // A tap anywhere else on the picture falls through to the gesture bed and merely toggles
        // the controls, which then carries this button with them through the shared fade timer.
        AnimatedVisibility(
            visible = controlsShown && !coverOpaque,
            enter = fadeIn(Motion.playerFade()),
            exit = fadeOut(Motion.playerFade()),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CenterPlayPauseButton(
                isPlaying = state.isPlaying,
                onClick = actions.onPlayPause,
            )
        }

        // The skip button shows only while the intro marker is active. An amber glass pill.
        AnimatedVisibility(
            visible = state.showSkipIntro,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(edge),
        ) {
            GlassTextButton("Skip intro", onClick = actions.onSkipIntro, accent = true)
        }

        // The next episode prompt, lower right, with a ten second countdown.
        AnimatedVisibility(
            visible = state.showNextEpisodePrompt,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(edge),
        ) {
            NextEpisodePrompt(
                title = state.nextEpisodeTitle.orEmpty(),
                secondsRemaining = state.countdownSeconds,
                onPlayNow = actions.onPlayNext,
                onCancel = actions.onCancelAutoPlay,
            )
        }

        // The transcoding chip. Lower left, fades after 4000 ms, never blocks the picture
        // and never requires dismissal. A quiet glass chip. See CLAUDE.md section 10.
        AnimatedVisibility(
            visible = state.showTranscodingChip,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.BottomStart).padding(edge),
        ) {
            Box(
                Modifier
                    .liquidGlass(shape = Radius.pill, glow = false)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            ) {
                PlexText("Transcoding", style = PlexTheme.type.caption, colour = colours.textSecondary)
            }
        }

        // The centre play/pause flash on tap: a glass burst around the icon.
        AnimatedVisibility(
            visible = flashVisible,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .liquidGlass(shape = Radius.pill)
                    .padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                PlexIcon(
                    kind = if (state.isPlaying) PlexIconKind.PLAY else PlexIconKind.PAUSE,
                    tint = colours.textPrimary,
                    size = 48.dp,
                )
            }
        }

        // The double-tap seek nudge, on the side that was tapped.
        SeekHint(visible = seekHint < 0, label = "« 10", modifier = Modifier.align(Alignment.CenterStart))
        SeekHint(visible = seekHint > 0, label = "10 »", modifier = Modifier.align(Alignment.CenterEnd))

        // The overlay's own sheets — speed, quality, chapters, sleep, subtitle appearance and up
        // next — over a dark scrim, in the same glass-sheet idiom as the audio/subtitle picker.
        OverlaySheetHost(
            state = state,
            actions = actions,
            sheet = sheet,
            onNavigate = { sheet = it },
            onDismiss = { sheet = null },
        )
    }
}

/** Which of the overlay's own glass sheets is open. See [OverlaySheetHost]. */
private enum class OverlaySheet { MORE, SPEED, QUALITY, CHAPTERS, SLEEP, SUBTITLES, UP_NEXT }

/**
 * Renders the open overlay sheet, if any. [OverlaySheet.MORE] is the overflow menu that gathers the
 * less-common controls — playback speed (#12), streaming quality (#11), chapters (#15), the sleep
 * timer (#18) and subtitle appearance (#14) — each of which opens its own leaf sheet through
 * [onNavigate]. Up next (#19) is reached straight from the transport row. Selecting an option fires
 * the matching action and closes the stack through [onDismiss].
 */
@Composable
private fun OverlaySheetHost(
    state: PlayerScreenState,
    actions: PlayerActions,
    sheet: OverlaySheet?,
    onNavigate: (OverlaySheet) -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        null -> Unit

        OverlaySheet.MORE -> OverlaySheetScaffold("More", onDismiss) {
            NavRow("Playback speed", formatSpeed(state.playbackSpeed)) { onNavigate(OverlaySheet.SPEED) }
            if (state.qualities.isNotEmpty()) {
                NavRow("Quality", state.currentQualityLabel) { onNavigate(OverlaySheet.QUALITY) }
            }
            if (state.chapters.isNotEmpty()) {
                NavRow("Chapters", null) { onNavigate(OverlaySheet.CHAPTERS) }
            }
            NavRow("Sleep timer", sleepLabel(state.sleepTimerRemainingMs)) { onNavigate(OverlaySheet.SLEEP) }
            if (state.subtitleTracks.isNotEmpty()) {
                NavRow("Subtitle appearance", null) { onNavigate(OverlaySheet.SUBTITLES) }
            }
        }

        OverlaySheet.SPEED -> OverlaySheetScaffold("Playback speed", onDismiss) {
            PLAYBACK_SPEEDS.forEach { speed ->
                TrackRow(formatSpeed(speed), selected = abs(state.playbackSpeed - speed) < 0.001f) {
                    actions.onSetSpeed(speed); onDismiss()
                }
            }
        }

        OverlaySheet.QUALITY -> OverlaySheetScaffold("Quality", onDismiss) {
            state.qualities.forEach { quality ->
                TrackRow(quality.label, selected = quality.label == state.currentQualityLabel) {
                    actions.onSelectQuality(quality); onDismiss()
                }
            }
        }

        OverlaySheet.CHAPTERS -> OverlaySheetScaffold("Chapters", onDismiss) {
            state.chapters.forEach { chapter ->
                val name = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.index}"
                val active = state.displayPositionMs in chapter.startMs until chapter.endMs
                TrackRow("$name  ·  ${formatPosition(chapter.startMs)}", selected = active) {
                    actions.onSeekToPosition(chapter.startMs); onDismiss()
                }
            }
        }

        OverlaySheet.SLEEP -> OverlaySheetScaffold("Sleep timer", onDismiss) {
            TrackRow("Off", selected = state.sleepTimerRemainingMs == null) {
                actions.onSetSleepTimer(null); onDismiss()
            }
            listOf(15, 30, 45, 60).forEach { minutes ->
                TrackRow("$minutes minutes", selected = false) {
                    actions.onSetSleepTimer(minutes * 60_000L); onDismiss()
                }
            }
            TrackRow("End of episode", selected = false) {
                val remaining = (state.durationMs - state.positionMs).coerceAtLeast(0L)
                actions.onSetSleepTimer(remaining); onDismiss()
            }
        }

        OverlaySheet.SUBTITLES -> SubtitleAppearanceSheet(state, actions, onDismiss)

        OverlaySheet.UP_NEXT -> OverlaySheetScaffold("Up next", onDismiss) {
            state.upNext.forEach { item ->
                TrackRow(item.title, selected = false) {
                    actions.onPlayUpNext(item); onDismiss()
                }
            }
        }
    }
}

/**
 * The subtitle appearance sheet (#14): text size, colour and a background box, each edited live by
 * copying the current [SubtitleStyle] and firing [PlayerActions.onSetSubtitleStyle]. The selected
 * option in each group carries the one warm accent, matching every other selection in the client.
 */
@Composable
private fun SubtitleAppearanceSheet(
    state: PlayerScreenState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    val colours = PlayerColours
    val style = state.subtitleStyle

    OverlaySheetScaffold("Subtitle appearance", onDismiss) {
        PlexText("Size", style = PlexTheme.type.caption, colour = colours.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf("A-" to 75, "A" to 100, "A+" to 150).forEach { (label, percent) ->
                GlassTextButton(
                    label,
                    onClick = { actions.onSetSubtitleStyle(style.copy(scalePercent = percent)) },
                    accent = style.scalePercent == percent,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxs))
        PlexText("Colour", style = PlexTheme.type.caption, colour = colours.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf("White" to 0xFFFFFFFFL, "Yellow" to 0xFFFFEB3BL).forEach { (label, argb) ->
                GlassTextButton(
                    label,
                    onClick = { actions.onSetSubtitleStyle(style.copy(foregroundArgb = argb)) },
                    accent = style.foregroundArgb == argb,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxs))
        PlexText("Background", style = PlexTheme.type.caption, colour = colours.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            GlassTextButton(
                "Off",
                onClick = { actions.onSetSubtitleStyle(style.copy(backgroundOpacityPercent = 0)) },
                accent = style.backgroundOpacityPercent == 0,
            )
            GlassTextButton(
                "On",
                onClick = { actions.onSetSubtitleStyle(style.copy(backgroundOpacityPercent = 60)) },
                accent = style.backgroundOpacityPercent > 0,
            )
        }
    }
}

/**
 * The shared scaffold for an overlay sheet: a dark scrim that dismisses on a background tap, and a
 * bottom glass panel carrying a title and the sheet's rows. Height is capped and the panel scrolls,
 * so a long chapter or up-next list stays reachable. Mirrors [TrackSheet]. Sixteen radius, per
 * CLAUDE.md section 12.
 */
@Composable
private fun OverlaySheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colours = PlayerColours

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colours.scrim)
            .plexFocusable(shape = Radius.sheet, onClick = onDismiss, scaleOnFocus = false),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val cap = maxHeight * 0.85f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = cap)
                .material(GlassRole.SHEET, shape = Radius.sheet)
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PlexText(title, style = PlexTheme.type.title, colour = colours.textPrimary)
            content()
        }
    }
}

/** A navigating row in the overflow menu: a label, an optional current value, opening a leaf sheet. */
@Composable
private fun NavRow(label: String, value: String?, onClick: () -> Unit) {
    val colours = PlayerColours
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlexText(label, colour = colours.textPrimary)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            PlexText(value, colour = colours.textSecondary)
        }
    }
}

/** The offered playback rates (#12). One is the source rate; the rest speed up or slow down. */
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** "1×", "1.25×", "0.5×" — the rate without trailing zeros. */
private fun formatSpeed(speed: Float): String {
    val text = if (speed % 1f == 0f) {
        speed.toInt().toString()
    } else {
        speed.toString().trimEnd('0').trimEnd('.')
    }
    return "$text×"
}

/** The sleep timer's summary value: "Off" or the remaining time as m:ss. */
private fun sleepLabel(remainingMs: Long?): String =
    if (remainingMs == null) "Off" else formatSleepClock(remainingMs)

/** The remaining sleep time as m:ss, for the "Zzz" indicator and the overflow summary. */
private fun formatSleepClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun SeekHint(visible: Boolean, label: String, modifier: Modifier = Modifier) {
    val colours = PlayerColours
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()),
        exit = fadeOut(Motion.exit()),
        modifier = modifier.padding(horizontal = Spacing.xl),
    ) {
        Box(
            Modifier
                .liquidGlass(shape = Radius.pill, glow = false)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            PlexText(label, style = PlexTheme.type.title, colour = colours.textPrimary)
        }
    }
}

/**
 * The floating transport bar: the showcase chrome glass carrying the transport controls, left to
 * right per CLAUDE.md section 14 item 7 — the previous-episode control, play/pause, seek back, seek
 * forward, the next-episode control, then the audio and subtitle selectors and the Windows
 * full-screen toggle pushed to the right. Position, the progress bar and duration sit on the scrubber
 * row just above. The bar is styled through the material-role engine ([GlassRole.CHROME]); it never
 * hand-picks tint numbers. The previous/next episode controls appear only when an adjacent episode
 * exists. See the theme engine in ui/design/Material.kt.
 */
@Composable
private fun TransportBar(
    state: PlayerScreenState,
    actions: PlayerActions,
    onOpenSheet: (OverlaySheet) -> Unit,
) {
    val isTv = PlexTheme.sizeClass.isTelevision

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .material(GlassRole.CHROME, shape = Radius.glass)
            .padding(
                horizontal = if (isTv) Spacing.md else Spacing.sm,
                vertical = if (isTv) Spacing.sm else Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Previous episode, shown only when one exists. A skip-previous glyph in a glass pill.
        if (state.hasPreviousEpisode) {
            GlassGlyphButton(forward = false, onClick = actions.onPlayPreviousEpisode)
        }
        GlassIconButton(
            kind = if (state.isPlaying) PlexIconKind.PAUSE else PlexIconKind.PLAY,
            onClick = actions.onPlayPause,
            prominent = true,
        )
        GlassTextButton("-10s", actions.onSeekBack)
        GlassTextButton("+30s", actions.onSeekForward)
        // Next episode, shown only when one exists. The explicit "next now", distinct from the
        // auto-play countdown. A skip-next glyph in a glass pill.
        if (state.hasNextEpisode) {
            GlassGlyphButton(forward = true, onClick = actions.onPlayNextEpisodeNow)
        }

        Spacer(Modifier.weight(1f))

        // The armed sleep timer, counting down. Non-interactive; a quiet glass chip. See #18.
        state.sleepTimerRemainingMs?.let { SleepIndicator(it) }

        if (state.audioTracks.size > 1) {
            GlassTextButton("Audio", actions.onOpenAudioTracks)
        }
        if (state.subtitleTracks.isNotEmpty()) {
            GlassTextButton("Subtitles", actions.onOpenSubtitleTracks)
        }
        // Up next, straight from the transport row when there is a queue (#19).
        if (state.upNext.isNotEmpty()) {
            GlassTextButton("Up Next", onClick = { onOpenSheet(OverlaySheet.UP_NEXT) })
        }
        // The overflow: speed, quality, chapters, sleep and subtitle appearance, so the row stays
        // uncrowded. See CLAUDE.md section 12.
        GlassTextButton("More", onClick = { onOpenSheet(OverlaySheet.MORE) })
        if (state.showFullScreenToggle) {
            GlassIconButton(
                kind = if (state.isFullScreen) PlexIconKind.FULLSCREEN_EXIT else PlexIconKind.FULLSCREEN,
                onClick = actions.onToggleFullScreen,
            )
        }
    }
}

/** The "Zzz m:ss" indicator shown while the sleep timer is armed. A quiet glass chip. See #18. */
@Composable
private fun SleepIndicator(remainingMs: Long) {
    val colours = PlayerColours
    Box(
        Modifier
            .material(GlassRole.CHIP, shape = Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        PlexText(
            "Zzz ${formatSleepClock(remainingMs)}",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
        )
    }
}

/**
 * The seek bar, with the trickplay preview from CLAUDE.md section 12: a thumbnail above the handle
 * in a glass card. The track carries the amber progress fill and a small glass handle rides it.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    trickplayUrlAt: (Long) -> String?,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (dragging) {
        dragFraction
    } else if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val thumb = if (isTv) 18.dp else 14.dp

    BoxWithConstraints(modifier.fillMaxWidth().height(28.dp)) {
        val trackWidth = maxWidth

        if (dragging) {
            val previewMs = (dragFraction * durationMs).toLong()
            val previewX = (trackWidth * fraction - PREVIEW_WIDTH / 2)
                .coerceIn(0.dp, (trackWidth - PREVIEW_WIDTH).coerceAtLeast(0.dp))
            TrickplayPreview(
                url = trickplayUrlAt(previewMs),
                positionMs = previewMs,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = previewX, y = -(PREVIEW_HEIGHT + Spacing.md)),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrubStart()
                        },
                        onDragEnd = {
                            dragging = false
                            onScrubEnd((dragFraction * durationMs).toLong())
                        },
                        onDragCancel = {
                            dragging = false
                            onScrubEnd((dragFraction * durationMs).toLong())
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrub((dragFraction * durationMs).toLong())
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colours.border, Radius.pill),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(colours.accent, Radius.pill),
                )
            }

            // Chapter marks: a thin notch on the track at each chapter start (#15). Drawn over both
            // the border track and the amber fill so they read at any position; the ends are skipped
            // since a mark on the very edge is invisible under the handle.
            if (chapters.isNotEmpty() && durationMs > 0) {
                val tickColour = colours.textPrimary.copy(alpha = 0.55f)
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val tickWidth = 2.dp.toPx()
                    chapters.forEach { chapter ->
                        val f = (chapter.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        if (f > 0.004f && f < 0.996f) {
                            drawRect(
                                color = tickColour,
                                topLeft = Offset(f * size.width - tickWidth / 2f, 0f),
                                size = Size(tickWidth, size.height),
                            )
                        }
                    }
                }
            }

            // The glass handle, riding the track at the current position.
            Box(
                Modifier
                    .offset(
                        x = (trackWidth * fraction - thumb / 2)
                            .coerceIn(0.dp, (trackWidth - thumb).coerceAtLeast(0.dp)),
                    )
                    .size(thumb)
                    .liquidGlass(shape = Radius.pill, glow = false),
            )
        }
    }
}

@Composable
private fun TrickplayPreview(
    url: String?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val colours = PlayerColours

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            Modifier
                .width(PREVIEW_WIDTH)
                .aspectRatio(16f / 9f)
                // A glass-framed card above the handle. See CLAUDE.md section 12.
                .liquidGlass(shape = Radius.card),
        ) {
            if (url != null) {
                Artwork(
                    url = url,
                    contentDescription = null,
                    fallbackTitle = "",
                    modifier = Modifier.fillMaxSize().clip(Radius.card),
                )
            }
        }
        PlexText(
            text = formatPosition(positionMs),
            style = PlexTheme.type.caption,
            colour = colours.textPrimary,
        )
    }
}

@Composable
private fun NextEpisodePrompt(
    title: String,
    secondsRemaining: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    val colours = PlayerColours

    Column(
        modifier = Modifier
            .material(GlassRole.SHEET, shape = Radius.glass)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlexText("Up next", style = PlexTheme.type.caption, colour = colours.textSecondary)
        PlexText(title, style = PlexTheme.type.label, colour = colours.textPrimary, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            GlassTextButton("Play now ($secondsRemaining)", onPlayNow, accent = true)
            GlassTextButton("Cancel", onCancel)
        }
    }
}

/**
 * A glass control button carrying a text label. The one warm accent fills the call-to-action pills
 * (Skip intro, Play now); everything else is quiet frosted glass. Springs down under a press with a
 * brightening specular, and grows behind an amber ring on television focus or pointer hover.
 */
@Composable
private fun GlassTextButton(
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colours = PlayerColours
    GlassControlButton(onClick = onClick, modifier = modifier, accent = accent) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = if (accent) colours.background else colours.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * The large, centred play/pause control — the primary touch target on the picture, in the accent-
 * filled circular treatment of the transport play/pause button, scaled up for a comfortable tap
 * (64.dp, 72.dp on television). It owns its own interaction source in the same idiom as
 * [GlassControlButton]: a press springs the bubble, and a television focus or a pointer hover raises
 * the amber ring and grows it, so a remote, a keyboard and a mouse each get the "this is the thing
 * under me" cue. On television it is focusable too, though the transport bar stays the primary
 * control there. It reflects the transport state — a pause glyph while playing, a play glyph while
 * paused or ended — and its [Modifier.clickable] consumes the tap so pausing never leaks to the
 * toggle-controls gesture bed beneath it.
 */
@Composable
private fun CenterPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val highlighted = focused || hovered

    val focusScale by animateFloatAsState(
        targetValue = when {
            focused -> Layout.TELEVISION_FOCUS_SCALE
            hovered -> 1f + (Layout.TELEVISION_FOCUS_SCALE - 1f) * 0.5f
            else -> 1f
        },
        animationSpec = Motion.spring(),
        label = "center-play-pause-focus",
    )

    val diameter = if (isTv) 72.dp else 64.dp
    Box(
        modifier = Modifier
            .scale(focusScale)
            .pressBubble(pressed)
            .size(diameter)
            .border(
                width = if (highlighted) Layout.focusRingWidth else 0.dp,
                color = if (highlighted) colours.focusRing else Color.Transparent,
                shape = Radius.pill,
            )
            // The one warm accent as a solid fill, matching the transport play/pause; the dark
            // ground token tints the glyph so it reads on the amber. No hand-picked tint numbers.
            .background(colours.accent, Radius.pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlexIcon(
            kind = if (isPlaying) PlexIconKind.PAUSE else PlexIconKind.PLAY,
            tint = colours.background,
            size = if (isTv) 40.dp else 34.dp,
        )
    }
}

/** A glass control button carrying a single line icon. [prominent] sizes up the central control. */
@Composable
private fun GlassIconButton(
    kind: PlexIconKind,
    onClick: () -> Unit,
    accent: Boolean = false,
    prominent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision
    val iconSize = when {
        prominent && isTv -> 34.dp
        prominent -> 30.dp
        isTv -> 26.dp
        else -> 22.dp
    }
    GlassControlButton(onClick = onClick, modifier = modifier, accent = accent) {
        PlexIcon(
            kind = kind,
            tint = if (accent) colours.background else colours.textPrimary,
            size = iconSize,
        )
    }
}

/**
 * A glass control button carrying the skip-previous / skip-next episode glyph.
 *
 * Compose Multiplatform ships no icon pack and [PlexIconKind] carries no skip glyph, so — matching
 * how this client draws its own line icons — the glyph is drawn on a Canvas here in the same idiom
 * as [PlexIcon]'s filled play triangle. [forward] true is skip-next (two triangles then a bar),
 * false is skip-previous (a bar then two triangles).
 */
@Composable
private fun GlassGlyphButton(forward: Boolean, onClick: () -> Unit) {
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision
    val glyphSize = if (isTv) 26.dp else 22.dp
    GlassControlButton(onClick = onClick) {
        SkipGlyph(forward = forward, size = glyphSize, tint = colours.textPrimary)
    }
}

/** The filled skip-previous / skip-next glyph, in the same drawn idiom as the play triangle. */
@Composable
private fun SkipGlyph(forward: Boolean, size: Dp, tint: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val top = h * 0.28f
        val bottom = h * 0.72f
        val mid = h * 0.50f
        val barWidth = w * 0.09f
        if (forward) {
            drawPath(
                Path().apply {
                    moveTo(w * 0.16f, top); lineTo(w * 0.16f, bottom); lineTo(w * 0.45f, mid); close()
                },
                tint,
            )
            drawPath(
                Path().apply {
                    moveTo(w * 0.45f, top); lineTo(w * 0.45f, bottom); lineTo(w * 0.74f, mid); close()
                },
                tint,
            )
            drawRect(tint, Offset(w * 0.75f, top), Size(barWidth, bottom - top))
        } else {
            drawRect(tint, Offset(w * 0.16f, top), Size(barWidth, bottom - top))
            drawPath(
                Path().apply {
                    moveTo(w * 0.84f, top); lineTo(w * 0.84f, bottom); lineTo(w * 0.55f, mid); close()
                },
                tint,
            )
            drawPath(
                Path().apply {
                    moveTo(w * 0.55f, top); lineTo(w * 0.55f, bottom); lineTo(w * 0.26f, mid); close()
                },
                tint,
            )
        }
    }
}

/**
 * The shared glass-pill control. Owns its interaction source so a press can spring the bubble
 * ([Modifier.pressBubble]) and a focus or hover can raise the amber ring and grow — so a remote, a
 * keyboard and a mouse each get the same "this is the thing under me" cue. The quiet controls draw
 * their glass through the material-role engine ([GlassRole.CHIP]); a call-to-action ([accent], e.g.
 * Skip intro / Play now) takes the one warm accent token as a solid fill so it pops. Neither branch
 * hand-picks tint numbers.
 */
@Composable
private fun GlassControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Radius.pill,
    accent: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val highlighted = focused || hovered

    // Focus grows the control to the television focus scale; a pointer hover lifts it half as far,
    // so a remote target still reads as the stronger selection when both are true.
    val focusScale by animateFloatAsState(
        targetValue = when {
            focused -> Layout.TELEVISION_FOCUS_SCALE
            hovered -> 1f + (Layout.TELEVISION_FOCUS_SCALE - 1f) * 0.5f
            else -> 1f
        },
        animationSpec = Motion.spring(),
        label = "player-control-focus",
    )

    Row(
        modifier = modifier
            .scale(focusScale)
            .pressBubble(pressed)
            .border(
                width = if (highlighted) Layout.focusRingWidth else 0.dp,
                color = if (highlighted) colours.focusRing else Color.Transparent,
                shape = shape,
            )
            .then(
                if (accent) {
                    Modifier.background(colours.accent, shape)
                } else {
                    Modifier.material(GlassRole.CHIP, shape = shape)
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(
                horizontal = if (isTv) Spacing.md else Spacing.sm,
                vertical = if (isTv) Spacing.sm else Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        content = content,
    )
}

/** A track chooser sheet, dressed as a sheet of liquid glass. Sixteen radius, per section 12. */
@Composable
fun TrackSheet(
    title: String,
    tracks: List<PlayerTrack>,
    allowNone: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlayerColours

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colours.scrim)
            .plexFocusable(shape = Radius.sheet, onClick = onDismiss, scaleOnFocus = false),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .material(GlassRole.SHEET, shape = Radius.sheet)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PlexText(title, style = PlexTheme.type.title, colour = colours.textPrimary)

            if (allowNone) {
                TrackRow("Off", selected = tracks.none { it.selected }) { onSelect(null) }
            }
            tracks.forEach { track ->
                TrackRow(track.label, track.selected) { onSelect(track.id) }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colours = PlayerColours
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .then(
                if (selected) {
                    // The one warm accent token marks the selected track; no ad-hoc glass tint.
                    Modifier.background(colours.accent, Radius.card)
                } else {
                    Modifier
                },
            )
            .padding(Spacing.sm),
    ) {
        PlexText(
            text = label,
            colour = if (selected) colours.background else colours.textPrimary,
        )
    }
}

private val PREVIEW_WIDTH = 200.dp
private val PREVIEW_HEIGHT = 112.dp
