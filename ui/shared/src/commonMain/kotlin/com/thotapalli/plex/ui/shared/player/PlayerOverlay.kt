package com.thotapalli.plex.ui.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.ui.design.Motion
import com.thotapalli.plex.ui.design.PlayerColours
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.formatPosition
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * The player overlay from CLAUDE.md section 12.
 *
 * Idle is nothing on screen: no bar, no clock, no logo, no title. Active is a bottom
 * gradient scrim, the title, a progress bar and transport controls, and nothing else.
 *
 * This is Compose content in a layer above the video surface. It never causes the surface
 * to be redrawn, and showing or hiding it never recreates the player or the surface.
 * See CLAUDE.md section 8.
 */
@Composable
fun PlayerOverlay(
    state: PlayerScreenState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    // The player screen ignores the light theme and always renders on the dark tokens.
    val colours = PlayerColours

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(Motion.playerFade()),
            exit = fadeOut(Motion.playerFade()),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to colours.scrim),
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PlexText(
                        text = state.title,
                        style = PlexTheme.type.title,
                        colour = colours.textPrimary,
                        maxLines = 1,
                    )
                    state.subtitle?.let {
                        PlexText(
                            text = it,
                            style = PlexTheme.type.caption,
                            colour = colours.textSecondary,
                            maxLines = 1,
                        )
                    }

                    Spacer(Modifier.height(Spacing.xs))

                    SeekBar(
                        positionMs = state.displayPositionMs,
                        durationMs = state.durationMs,
                        trickplayUrlAt = state.trickplayUrlAt,
                        onScrubStart = actions.onScrubStart,
                        onScrub = actions.onScrub,
                        onScrubEnd = actions.onScrubEnd,
                    )

                    TransportRow(state = state, actions = actions)
                }
            }
        }

        // The skip button shows only while the intro marker is active.
        AnimatedVisibility(
            visible = state.showSkipIntro,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl),
        ) {
            OverlayButton("Skip intro", onClick = actions.onSkipIntro, primary = true)
        }

        // The next episode prompt, lower right, with a ten second countdown.
        AnimatedVisibility(
            visible = state.showNextEpisodePrompt,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl),
        ) {
            NextEpisodePrompt(
                title = state.nextEpisodeTitle.orEmpty(),
                secondsRemaining = state.countdownSeconds,
                onPlayNow = actions.onPlayNext,
                onCancel = actions.onCancelAutoPlay,
            )
        }

        // The transcoding chip. Lower left, fades after 4000 ms, never blocks the picture
        // and never requires dismissal. See CLAUDE.md section 10.
        AnimatedVisibility(
            visible = state.showTranscodingChip,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xl),
        ) {
            Box(
                Modifier
                    .background(colours.surface.copy(alpha = 0.85f), Radius.pill)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            ) {
                PlexText("Transcoding", style = PlexTheme.type.caption, colour = colours.textSecondary)
            }
        }

        if (state.playbackState is PlaybackState.Buffering) {
            Box(Modifier.align(Alignment.Center)) {
                PlexText("Buffering", colour = colours.textSecondary)
            }
        }
    }
}

@Composable
private fun TransportRow(state: PlayerScreenState, actions: PlayerActions) {
    val colours = PlayerColours

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Left to right, exactly as CLAUDE.md section 14 item 7 lists them.
        OverlayButton(if (state.isPlaying) "Pause" else "Play", actions.onPlayPause, primary = true)
        OverlayButton("-10s", actions.onSeekBack)
        OverlayButton("+30s", actions.onSeekForward)

        PlexText(
            text = formatPosition(state.displayPositionMs),
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
        )

        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.weight(1f))

        PlexText(
            text = formatPosition(state.durationMs),
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
        )

        if (state.audioTracks.size > 1) {
            OverlayButton("Audio", actions.onOpenAudioTracks)
        }
        if (state.subtitleTracks.isNotEmpty()) {
            OverlayButton("Subtitles", actions.onOpenSubtitleTracks)
        }
        if (state.showFullScreenToggle) {
            OverlayButton(
                if (state.isFullScreen) "Exit full screen" else "Full screen",
                actions.onToggleFullScreen,
            )
        }
    }
}

/**
 * The seek bar, with the trickplay preview from CLAUDE.md section 12: a thumbnail above
 * the handle in a 12 radius card with no border.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    trickplayUrlAt: (Long) -> String?,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
) {
    val colours = PlayerColours
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (dragging) {
        dragFraction
    } else if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
        val trackWidth = maxWidth

        if (dragging) {
            val previewMs = (dragFraction * durationMs).toLong()
            TrickplayPreview(
                url = trickplayUrlAt(previewMs),
                positionMs = previewMs,
                offsetX = (trackWidth * fraction) - PREVIEW_WIDTH / 2,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
            contentAlignment = Alignment.Center,
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
        }
    }
}

@Composable
private fun TrickplayPreview(url: String?, positionMs: Long, offsetX: Dp) {
    val colours = PlayerColours

    Column(
        modifier = Modifier.offset(x = offsetX.coerceAtLeast(0.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(PREVIEW_WIDTH)
                .aspectRatio(16f / 9f)
                // A 12 radius card with no border. See CLAUDE.md section 12.
                .clip(Radius.card)
                .background(colours.surfaceElevated),
        ) {
            if (url != null) {
                com.thotapalli.plex.ui.shared.Artwork(
                    url = url,
                    contentDescription = null,
                    fallbackTitle = "",
                    modifier = Modifier.fillMaxSize(),
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
            .background(colours.surface.copy(alpha = 0.95f), Radius.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlexText("Up next", style = PlexTheme.type.caption, colour = colours.textSecondary)
        PlexText(title, style = PlexTheme.type.label, colour = colours.textPrimary, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OverlayButton("Play now ($secondsRemaining)", onPlayNow, primary = true)
            OverlayButton("Cancel", onCancel)
        }
    }
}

@Composable
private fun OverlayButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    val colours = PlayerColours
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(
                if (primary) colours.accent else colours.surface.copy(alpha = 0.7f),
                Radius.pill,
            )
            .border(
                1.dp,
                if (primary) colours.accent else colours.border,
                Radius.pill,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = if (primary) colours.background else colours.textPrimary,
        )
    }
}

/** A track chooser sheet. Sixteen radius, per section 12. */
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
                .background(colours.surface, Radius.sheet)
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
            .background(if (selected) colours.surfaceElevated else Color.Transparent, Radius.card)
            .padding(Spacing.sm),
    ) {
        PlexText(
            text = label,
            colour = if (selected) colours.accent else colours.textPrimary,
        )
    }
}

private val PREVIEW_WIDTH = 200.dp
