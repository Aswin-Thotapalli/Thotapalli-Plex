package com.thotapalli.plex.ui.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.player.mpv.MpvPlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Windows video surface, backed by libmpv.
 *
 * TODO(desktop): libmpv opens its own window when handed no HWND. Compositing the mpv
 * surface beneath the Compose overlay inside the same window (through the `wid` option and
 * an AWT/Skia native handle) is future work. For now the engine is still created and fully
 * driven — timeline reporting, markers, auto-play and the overlay controls all function —
 * so the desktop build compiles and runs; only in-window video compositing is outstanding.
 * Android is the priority target. See CLAUDE.md section 8, libmpv setup.
 */
@Composable
actual fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val engine = remember {
        // LibMpv.load() throws when the native library is absent (every non-Windows JVM),
        // so a missing DLL degrades to a dark screen rather than crashing the app.
        runCatching { MpvPlayerEngine(scope).also { it.initialise(null) } }
            .getOrElse { UnavailablePlayerEngine() }
    }

    LaunchedEffect(engine) { bind(engine) }

    Box(modifier.fillMaxSize().background(Color.Black))
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

    override fun load(source: PlaybackSource, startAtMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) {}
    override fun setScrubbing(active: Boolean) {}
    override fun selectAudioTrack(id: String) {}
    override fun selectSubtitleTrack(id: String?) {}
    override fun release() {}
}
