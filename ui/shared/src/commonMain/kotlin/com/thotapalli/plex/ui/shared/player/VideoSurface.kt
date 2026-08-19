package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thotapalli.plex.core.playback.PlayerEngine

/**
 * The platform video surface.
 *
 * Each target creates its own [PlayerEngine] here (Media3 on Android, libmpv on Windows),
 * hosts the video surface, and hands the engine back through [bind] exactly once so the
 * shared [PlayerScreen] can drive it through a [PlaybackController].
 *
 * On Android the surface is a SurfaceView, never a TextureView, and it sits beneath the
 * Compose overlay so showing or hiding the controls never redraws the video.
 * See CLAUDE.md section 8.
 */
@Composable
expect fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    onPointerActivity: () -> Unit,
    modifier: Modifier,
)
