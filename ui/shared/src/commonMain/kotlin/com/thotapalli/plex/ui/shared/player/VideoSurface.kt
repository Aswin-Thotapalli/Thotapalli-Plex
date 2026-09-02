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
 *
 * [overlay] is the transport controls layer. On Android and television it is empty here — the
 * caller draws the overlay in the same Compose window, above the SurfaceView. On Windows the
 * video is a heavyweight GL surface the outer Compose window cannot paint over, so the overlay
 * is handed in here and composited inside the GL surface itself (one window, the picture and the
 * controls in the same composited frame). See CLAUDE.md section 8, the "controls are a layer
 * above the video" rule — satisfied literally, in one window.
 */
@Composable
expect fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    onPointerActivity: () -> Unit,
    modifier: Modifier,
    overlay: @Composable () -> Unit = {},
)
