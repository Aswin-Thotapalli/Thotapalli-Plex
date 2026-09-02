@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.InternalComposeUiApi::class,
)

package com.thotapalli.plex.ui.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.sun.jna.Pointer
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTracks
import com.thotapalli.plex.core.playback.SubtitleStyle
import com.thotapalli.plex.player.mpv.MpvGetProcAddressFn
import com.thotapalli.plex.player.mpv.MpvPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Windows video surface — the true single-window player.
 *
 * mpv renders the video into an OpenGL framebuffer this component owns (libmpv render API), and the
 * Compose overlay is composited on top of it, in the same framebuffer, through a Skia surface bound
 * to the same GL context. The picture and the controls end up in one composited frame in one window,
 * hardware throughout — which is why [overlay] is drawn here rather than in the outer Compose window.
 *
 * This replaces the earlier two-window arrangement (mpv on a native HWND with a separate always-on-top
 * controls window), whose overlay window sat over the rest of the desktop and knocked the compositor
 * off its fast full-screen path. Here nothing floats over anything: it is literally the surface rule
 * from CLAUDE.md section 8, "controls are a layer above the video", satisfied in one window.
 *
 * The GL context, mpv's render calls, the Skia composite and the Compose scene all live on one
 * dedicated thread (never the EDT), so the buffer swap blocking on vsync never freezes the UI.
 */
@Composable
actual fun VideoSurface(
    bind: (PlayerEngine) -> Unit,
    onPointerActivity: () -> Unit,
    modifier: Modifier,
    overlay: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestActivity = rememberUpdatedState(onPointerActivity)
    val latestOverlay = rememberUpdatedState(overlay)

    // The engine is created in render mode (vo=libmpv, no window handle). If libmpv cannot load the
    // player screen still stays alive on the unavailable engine, exactly as the old surface did.
    val engine = remember {
        runCatching {
            MpvPlayerEngine(scope).also { it.initialise(windowHandle = null, renderMode = true) }
        }.getOrElse { UnavailablePlayerEngine() }
    }
    LaunchedEffect(engine) { bind(engine) }

    if (engine !is MpvPlayerEngine) {
        // No libmpv: black ground with the controls drawn in the ordinary Compose window. There is no
        // GL surface to composite into, so the airspace problem does not arise.
        Box(modifier.fillMaxSize().background(Color.Black)) { latestOverlay.value() }
        return
    }

    val glSurface = remember(engine) { DesktopGlSurface(engine, scope, latestActivity, latestOverlay) }
    DisposableEffect(glSurface) {
        glSurface.start()
        onDispose { glSurface.stop() }
    }

    SwingPanel(
        background = Color.Black,
        factory = { glSurface.canvas },
        modifier = modifier,
    )
}

/**
 * Owns the GL canvas, the mpv render context, the Skia device and the Compose scene, and drives the
 * render loop. Everything that touches GL or the scene runs on [glDispatcher]'s single thread.
 */
private class DesktopGlSurface(
    private val engine: MpvPlayerEngine,
    private val scope: CoroutineScope,
    private val onActivity: State<() -> Unit>,
    private val overlay: State<@Composable () -> Unit>,
) {
    private val glExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "thotapalli-gl").apply { isDaemon = true }
    }
    private val glDispatcher = glExecutor.asCoroutineDispatcher()

    private var loopJob: Job? = null
    @Volatile private var running = false

    // GL-thread state, created lazily in initGL / the first paint.
    private var skia: DirectContext? = null
    private var scene: ComposeScene? = null
    private var renderReady = false
    private var sceneScale = 0.0
    private var sceneW = 0
    private var sceneH = 0

    /** The display scale seen on the last paint, read by the (EDT) pointer listeners. */
    @Volatile private var lastScale = 1.0

    val canvas: AWTGLCanvas = object : AWTGLCanvas(glData()) {
        override fun initGL() = onInitGL()
        override fun paintGL() = onPaintGL()
    }.apply {
        background = java.awt.Color.BLACK
        // Non-focusable so keyboard shortcuts (Escape to leave, space to pause) reach the main
        // Compose window, which owns them; the overlay is driven by the mouse only.
        isFocusable = false
        installPointerForwarding(this)
    }

    fun start() {
        running = true
        loopJob = scope.launch(glDispatcher) {
            while (isActive && running) {
                val startNs = System.nanoTime()
                if (canvas.isValid) runCatching { canvas.render() }
                    .onFailure { it.printStackTrace() }
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                // Let queued input and recomposition effects run on this thread between frames.
                yield()
                // With vsync the swap already paced the frame (~16ms); only add sleep when a driver
                // ignored vsync and the frame came back fast, so the loop never spins the GPU.
                if (elapsedMs < FRAME_CAP_MS) delay(FRAME_CAP_MS - elapsedMs)
            }
        }
    }

    fun stop() {
        running = false
        loopJob?.cancel()
        // Tear the GL-thread resources down on the GL thread, then shut it down. The engine itself is
        // released by PlayerScreen (stopAndRelease), which also frees mpv's render context.
        runCatching {
            glExecutor.execute {
                runCatching { scene?.close() }
                runCatching { skia?.close() }
                scene = null
                skia = null
            }
            glExecutor.shutdown()
        }
    }

    private fun onInitGL() {
        GL.createCapabilities()
        skia = DirectContext.makeGL()

        // Resolve GL functions for mpv against the context now current on this thread.
        val getProc = MpvGetProcAddressFn { _, name ->
            val address = if (name == null) 0L else {
                runCatching { GL.getFunctionProvider()?.getFunctionAddress(name) ?: 0L }.getOrDefault(0L)
            }
            if (address == 0L) null else Pointer(address)
        }
        renderReady = engine.createRenderContext(getProc)
    }

    private fun onPaintGL() {
        val transform = canvas.graphicsConfiguration?.defaultTransform
        val scale = transform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
        lastScale = scale
        val fbW = max(1, (canvas.width * scale).roundToInt())
        val fbH = max(1, (canvas.height * scale).roundToInt())

        GL11.glViewport(0, 0, fbW, fbH)

        // The video first. mpv fills the framebuffer (with black bars for the aspect), FLIP_Y so its
        // bottom-left origin lands right-side up for the Skia surface below.
        if (renderReady) engine.renderInto(fbo = 0, w = fbW, h = fbH, flipY = true)

        // Then the overlay, composited straight over the video in the same framebuffer.
        val scene = ensureScene(fbW, fbH, scale)
        skia?.let { context ->
            // mpv left GL state changed; resync Skia's view of it before it draws.
            context.resetGLAll()
            val renderTarget = BackendRenderTarget.makeGL(fbW, fbH, 0, STENCIL_BITS, 0, GL_RGBA8)
            val surface = Surface.makeFromBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.sRGB,
            )
            if (surface != null) {
                scene.render(surface.canvas.asComposeCanvas(), System.nanoTime())
                surface.flushAndSubmit()
                surface.close()
            }
            renderTarget.close()
        }

        canvas.swapBuffers()
        if (renderReady) engine.reportSwap()
    }

    /**
     * Returns the scene, creating it on first use and recreating it if the framebuffer size or the
     * display scale changed (a resize, or the window moved to a differently-scaled monitor).
     */
    private fun ensureScene(width: Int, height: Int, scale: Double): ComposeScene {
        val existing = scene
        if (existing != null && sceneScale == scale) {
            if (sceneW != width || sceneH != height) {
                existing.size = IntSize(width, height)
                sceneW = width
                sceneH = height
            }
            return existing
        }
        existing?.close()
        val created = CanvasLayersComposeScene(
            density = Density(scale.toFloat()),
            layoutDirection = LayoutDirection.Ltr,
            size = IntSize(width, height),
            coroutineContext = glDispatcher,
            invalidate = {},
        )
        created.setContent { overlay.value() }
        scene = created
        sceneScale = scale
        sceneW = width
        sceneH = height
        return created
    }

    // --- input ------------------------------------------------------------------------
    //
    // AWT mouse events arrive on the EDT; each is forwarded to the scene on the GL thread (where the
    // scene lives) as a Compose pointer event, in the scene's pixel space (logical AWT coordinates
    // times the display scale). Keyboard stays with the main window — the canvas is non-focusable.

    private fun installPointerForwarding(component: java.awt.Canvas) {
        component.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                onActivity.value()
                forward(PointerEventType.Press, e, button = PointerButton.Primary)
            }
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                forward(PointerEventType.Release, e, button = PointerButton.Primary)
            }
            override fun mouseEntered(e: java.awt.event.MouseEvent) = forward(PointerEventType.Enter, e)
            override fun mouseExited(e: java.awt.event.MouseEvent) = forward(PointerEventType.Exit, e)
        })
        component.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                onActivity.value()
                forward(PointerEventType.Move, e)
            }
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                onActivity.value()
                forward(PointerEventType.Move, e)
            }
        })
        component.addMouseWheelListener { e ->
            forward(PointerEventType.Scroll, e, scroll = Offset(0f, e.preciseWheelRotation.toFloat()))
        }
    }

    private fun forward(
        type: PointerEventType,
        e: java.awt.event.MouseEvent,
        scroll: Offset? = null,
        button: PointerButton? = null,
    ) {
        val scale = lastScale
        val position = Offset((e.x * scale).toFloat(), (e.y * scale).toFloat())
        scope.launch(glDispatcher) {
            val target = scene ?: return@launch
            runCatching {
                if (scroll != null) {
                    target.sendPointerEvent(eventType = type, position = position, scrollDelta = scroll)
                } else {
                    target.sendPointerEvent(eventType = type, position = position, button = button)
                }
            }
        }
    }

    private fun glData() = GLData().apply {
        doubleBuffer = true
        alphaSize = 8
        depthSize = 0
        // Skia's GL backend renders through a stencil buffer, so the default framebuffer must have one.
        stencilSize = STENCIL_BITS
        majorVersion = 3
        minorVersion = 3
        profile = GLData.Profile.CORE
        // vsync: pace the swap to the display so the frame the display shows is the one mpv timed.
        swapInterval = 1
    }

    private companion object {
        const val STENCIL_BITS = 8
        const val GL_RGBA8 = 0x8058
        // Frame-time floor in ms (~70fps) applied only when vsync did not pace the frame.
        const val FRAME_CAP_MS = 14L
    }
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
    override fun setPlaybackSpeed(speed: Float) {}
    override fun setSubtitleStyle(style: SubtitleStyle) {}
    override fun release() {}
}
