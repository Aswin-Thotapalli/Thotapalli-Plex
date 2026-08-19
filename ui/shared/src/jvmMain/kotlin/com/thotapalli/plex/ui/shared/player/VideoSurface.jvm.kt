@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.sun.jna.Native
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTracks
import com.thotapalli.plex.player.mpv.MpvPlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Canvas as AwtCanvas

/**
 * The Windows video surface, backed by libmpv rendering into this window.
 *
 * mpv draws straight to a native window handle. Rather than let it open a window of its own
 * — which stole focus, ignored the keyboard (input is disabled by the section 8 config) and
 * left the viewer with no way back — a heavyweight AWT [AwtCanvas] is embedded in the Compose
 * window and its HWND handed to mpv through the `wid` option. The picture is now inside the
 * application, the Compose window keeps focus so Escape leaves the player, and the Compose
 * overlay composites above the video. See CLAUDE.md section 8, libmpv setup.
 */
@Composable
actual fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    onPointerActivity: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf<PlayerEngine?>(null) }
    // Kept fresh so the mouse listener, added once, always calls the latest callback.
    val latestActivity = rememberUpdatedState(onPointerActivity)

    val canvas = remember {
        object : AwtCanvas() {
            override fun addNotify() {
                super.addNotify()
                // The peer, and therefore the HWND, exists only once the canvas is realised.
                val handle = runCatching { Native.getComponentID(this) }.getOrNull()
                engine = runCatching {
                    MpvPlayerEngine(scope).also { it.initialise(handle) }
                }.getOrElse { UnavailablePlayerEngine() }
            }
        }.apply {
            background = java.awt.Color.BLACK
            // Non-focusable so key events (Escape to leave) reach the Compose window rather
            // than being swallowed by this heavyweight component.
            isFocusable = false
            // The heavyweight canvas eats mouse events, so it reports movement itself to
            // reveal the auto-hiding controls.
            addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseMoved(e: java.awt.event.MouseEvent?) { latestActivity.value() }
                override fun mouseDragged(e: java.awt.event.MouseEvent?) { latestActivity.value() }
            })
        }
    }

    LaunchedEffect(engine) { engine?.let(bind) }

    SwingPanel(
        background = Color.Black,
        factory = { canvas },
        modifier = modifier,
    )
}

/**
 * A do-nothing engine for a desktop where libmpv could not load.
 *
 * It reports Idle forever and ignores every command, which keeps the player screen alive
 * instead of taking the application down when the native library is missing.
 */
private class UnavailablePlayerEngine : PlayerEngine {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _tracks = MutableStateFlow(PlayerTracks())
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    override fun load(source: PlaybackSource, startAtMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) {}
    override fun setScrubbing(active: Boolean) {}
    override fun selectAudioTrack(id: String) {}
    override fun selectSubtitleTrack(id: String?) {}
    override fun release() {}
}
