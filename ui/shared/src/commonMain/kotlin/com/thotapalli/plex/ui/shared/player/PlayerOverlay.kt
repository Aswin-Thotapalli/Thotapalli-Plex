package com.thotapalli.plex.ui.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerTrack
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
import com.thotapalli.plex.ui.shared.LoadingIndicator
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.formatPosition
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * The player overlay from CLAUDE.md section 12, dressed in the liquid-glass language.
 *
 * Idle is nothing on screen: no bar, no clock, no logo, no title. Active is a bottom gradient
 * scrim, the title, a progress bar and transport controls, and nothing else. The transport
 * controls float in a frosted glass bar, the one warm-amber accent marks only the active scrubber
 * fill and the call-to-action pills, and every control answers a press with a springy bubble.
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

    // A brief "10s" nudge shown after a double-tap seek: -1 on the left, +1 on the right, 0 none.
    var seekHint by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekHint) {
        if (seekHint != 0) {
            delay(650)
            seekHint = 0
        }
    }

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
                        onPress = { actions.onUserInput() },
                        onTap = {
                            actions.onPlayPause()
                            playPauseFlash.intValue += 1
                        },
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
                },
        )

        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(Motion.playerFade()),
            exit = fadeOut(Motion.playerFade()),
        ) {
            Box(
                Modifier.fillMaxSize().background(colours.background),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    label = if (state.playbackState is PlaybackState.Buffering) "Buffering" else "Loading",
                )
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

                    TransportBar(state = state, actions = actions)
                }
            }
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
    }
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
 * The floating transport bar: a sheet of frosted glass carrying the transport controls, left to
 * right per CLAUDE.md section 14 item 7 — play/pause, seek back, seek forward, then the audio and
 * subtitle selectors and the Windows full-screen toggle pushed to the right. Position, the progress
 * bar and duration sit on the scrubber row just above. The tint is a touch denser than the ambient
 * glass so white glyphs stay legible over bright footage.
 */
@Composable
private fun TransportBar(state: PlayerScreenState, actions: PlayerActions) {
    val colours = PlayerColours
    val isTv = PlexTheme.sizeClass.isTelevision

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = Radius.glass,
                elevated = true,
                tint = colours.surface.copy(alpha = 0.82f),
            )
            .padding(
                horizontal = if (isTv) Spacing.md else Spacing.sm,
                vertical = if (isTv) Spacing.sm else Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GlassIconButton(
            kind = if (state.isPlaying) PlexIconKind.PAUSE else PlexIconKind.PLAY,
            onClick = actions.onPlayPause,
            prominent = true,
        )
        GlassTextButton("-10s", actions.onSeekBack)
        GlassTextButton("+30s", actions.onSeekForward)

        Spacer(Modifier.weight(1f))

        if (state.audioTracks.size > 1) {
            GlassTextButton("Audio", actions.onOpenAudioTracks)
        }
        if (state.subtitleTracks.isNotEmpty()) {
            GlassTextButton("Subtitles", actions.onOpenSubtitleTracks)
        }
        if (state.showFullScreenToggle) {
            GlassIconButton(
                kind = if (state.isFullScreen) PlexIconKind.FULLSCREEN_EXIT else PlexIconKind.FULLSCREEN,
                onClick = actions.onToggleFullScreen,
            )
        }
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
            .liquidGlass(shape = Radius.glass, elevated = true)
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
 * The shared glass-pill control. Owns its interaction source so a press can both spring the bubble
 * ([Modifier.pressBubble]) and brighten the glass ([liquidGlass]'s `specularBoost`), and a focus or
 * hover can raise the amber ring and grow — so a remote, a keyboard and a mouse each get the same
 * "this is the thing under me" cue. Glow is off so pills inside the transport bar do not stack halos.
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
            .liquidGlass(
                shape = shape,
                glow = false,
                tint = if (accent) colours.accent else Color.Unspecified,
                specularBoost = if (pressed) 1f else 0f,
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
                .liquidGlass(shape = Radius.sheet, elevated = true)
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
                    Modifier.liquidGlass(shape = Radius.card, glow = false, tint = colours.accent)
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
