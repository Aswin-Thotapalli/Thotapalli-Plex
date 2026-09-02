package com.thotapalli.plex.ui.shared.player

import android.graphics.Color
import android.graphics.Typeface
import android.view.SurfaceView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.SubtitleStyle
import com.thotapalli.plex.player.exo.ExoPlayerEngine

/**
 * The Android video surface, backed by Media3.
 *
 * A SurfaceView goes beneath the Compose overlay so the decoder writes straight to a
 * hardware layer the compositor scans out, never through a TextureView. Showing or hiding
 * the controls never touches this view, and the player is never placed in a scrolling or
 * animating container. See CLAUDE.md section 8, surface rules.
 *
 * Media3 draws no text onto a bare SurfaceView, so a Media3 [SubtitleView] is layered above
 * the surface inside a [FrameLayout] to render cues. The SubtitleView is transparent except
 * where a cue sits, so it never obscures the picture, and it stays below the Compose transport
 * overlay in z-order. It renders both text cues and bitmap cues (PGS, VOBSUB). The engine
 * exposes cues and the chosen appearance as StateFlows; collecting them here feeds the
 * AndroidView update block, so this works identically for direct play and transcode.
 * See CLAUDE.md sections 8 and 12.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    // Android reveals the controls from the Compose overlay's own taps, so this is unused.
    onPointerActivity: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { ExoPlayerEngine(context, scope) }

    LaunchedEffect(engine) { bind(engine) }

    // Reading these as Compose state re-runs the update block below whenever a new cue group
    // arrives or the viewer changes the subtitle appearance. No manual coroutine collection,
    // so nothing to leak across recompositions.
    val cues by engine.cues.collectAsStateWithLifecycle()
    val subtitleStyle by engine.subtitleStyle.collectAsStateWithLifecycle()
    // Keep the screen awake while the video is actually running. A bare SurfaceView — unlike Media3's
    // PlayerView — does nothing on its own, so without this the phone dims and locks mid-playback and
    // playback stops. Bound to the play state so a long pause still lets the screen time out normally.
    val playbackState by engine.state.collectAsStateWithLifecycle()
    val keepAwake = playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val surface = SurfaceView(ctx).also(engine::attachSurface)
            val subtitles = SubtitleView(ctx)
            // Container holds the surface at the bottom and the subtitle layer above it. The
            // subtitle layer is transparent apart from cue boxes and receives no touches, so
            // it never blocks the video or the Compose overlay stacked over the whole view.
            FrameLayout(ctx).apply {
                addView(surface, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(subtitles, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
        update = { root ->
            val subtitles = root.getChildAt(1) as SubtitleView
            subtitles.setCues(cues)
            subtitles.applyStyle(subtitleStyle)
            // Setting it on the container keeps the whole window awake while it is attached.
            root.keepScreenOn = keepAwake
        },
        onRelease = { runCatching { engine.detachSurface() } },
    )
}

/**
 * Maps a [SubtitleStyle] onto the Media3 SubtitleView: white-on-outline captions by section 12,
 * a black background box whose alpha follows the chosen opacity, and a text size scaled off the
 * default fractional size by the chosen percentage.
 */
@OptIn(UnstableApi::class)
private fun SubtitleView.applyStyle(style: SubtitleStyle) {
    val backgroundAlpha = style.backgroundOpacityPercent.coerceIn(0, 100) * 255 / 100
    setStyle(
        CaptionStyleCompat(
            style.foregroundArgb.toInt(),
            Color.argb(backgroundAlpha, 0, 0, 0),
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            Color.BLACK,
            null as Typeface?,
        ),
    )
    // Ignore the system caption size and drive it from the viewer's chosen scale.
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.scalePercent / 100f)
}
