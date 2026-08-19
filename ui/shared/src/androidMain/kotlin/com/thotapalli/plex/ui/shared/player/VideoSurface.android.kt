package com.thotapalli.plex.ui.shared.player

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.player.exo.ExoPlayerEngine

/**
 * The Android video surface, backed by Media3.
 *
 * A SurfaceView goes beneath the Compose overlay so the decoder writes straight to a
 * hardware layer the compositor scans out, never through a TextureView. Showing or hiding
 * the controls never touches this view, and the player is never placed in a scrolling or
 * animating container. See CLAUDE.md section 8, surface rules.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { ExoPlayerEngine(context, scope) }

    LaunchedEffect(engine) { bind(engine) }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> SurfaceView(ctx).also(engine::attachSurface) },
        onRelease = { runCatching { engine.detachSurface() } },
    )
}
