@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.thotapalli.plex.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sun.jna.Pointer
import com.thotapalli.plex.core.playback.PlaybackMode
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.player.mpv.LibMpv
import com.thotapalli.plex.player.mpv.MpvGetProcAddressFn
import com.thotapalli.plex.player.mpv.MpvPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Proof for the SHIPPED single-window path: the real [MpvPlayerEngine] in render mode drawing into an
 * [AWTGLCanvas] (the exact GL surface [com.thotapalli.plex.ui.shared.player.VideoSurface] embeds), with
 * a Compose scene composited over it and the result read straight back to a PNG. This is what
 * RenderProbe could not cover — that used GLFW; the app uses AWTGLCanvas, and its GL context and
 * function loader ([GL.getFunctionProvider]) are the one untested link.
 *
 *   gradlew :app:desktop:glCanvasProbe
 */
@Composable
private fun ProbeOverlay() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                .background(Color(0xCC101418)),
        )
        Box(
            Modifier.align(Alignment.BottomStart).padding(40.dp).size(220.dp, 64.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFD54F)),
        )
    }
}

fun main() {
    LibMpv.load(listOf(Path.of("app", "desktop", "native", "windows-x64"), Path.of("native", "windows-x64")))

    val scope = CoroutineScope(SupervisorJob())
    val engine = MpvPlayerEngine(scope).also { it.initialise(windowHandle = null, renderMode = true) }
    // Load BEFORE the render context exists — the app's worst-case ordering. The engine defers the
    // load and replays it when the context is created, so the picture must still appear.
    engine.load(
        PlaybackSource(
            uri = "av://lavfi:testsrc=size=1280x720:rate=30:duration=60",
            mode = PlaybackMode.DIRECT,
            headers = emptyMap(),
            frameRate = null,
        ),
        startAtMs = 0,
    )
    engine.play()

    var skia: DirectContext? = null
    var scene: ComposeScene? = null
    var ready = false
    var frame = 0
    var shot = false

    val canvas = object : AWTGLCanvas(GLData().apply {
        doubleBuffer = true
        alphaSize = 8
        depthSize = 0
        stencilSize = 8
        majorVersion = 3
        minorVersion = 3
        profile = GLData.Profile.CORE
        swapInterval = 1
    }) {
        override fun initGL() {
            GL.createCapabilities()
            skia = DirectContext.makeGL()
            val getProc = MpvGetProcAddressFn { _, name ->
                val a = if (name == null) 0L else runCatching { GL.getFunctionProvider()?.getFunctionAddress(name) ?: 0L }.getOrDefault(0L)
                if (a == 0L) null else Pointer(a)
            }
            ready = engine.createRenderContext(getProc)
            println("PROBE_RENDER_CONTEXT_READY=$ready")
        }

        override fun paintGL() {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            GL11.glViewport(0, 0, w, h)
            if (ready) engine.renderInto(fbo = 0, w = w, h = h, flipY = true)

            val sc = scene ?: CanvasLayersComposeScene(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                size = IntSize(w, h),
                coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined,
                invalidate = {},
            ).also { it.setContent { ProbeOverlay() }; scene = it }
            if (sc.size != IntSize(w, h)) sc.size = IntSize(w, h)

            skia?.let { ctx ->
                ctx.resetGLAll()
                val rt = BackendRenderTarget.makeGL(w, h, 0, 8, 0, 0x8058)
                val surface = Surface.makeFromBackendRenderTarget(
                    ctx, rt, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB,
                )
                surface?.let { s ->
                    sc.render(s.canvas.asComposeCanvas(), System.nanoTime())
                    s.flushAndSubmit()
                    s.close()
                }
                rt.close()
            }

            frame++
            if (!shot && frame > 120) {
                shot = true
                saveGl(w, h, System.getProperty("probe.out", "glcanvas_probe.png"))
                println("PROBE_FRAME_SAVED")
            }

            swapBuffers()
            if (ready) engine.reportSwap()
        }
    }

    val frameWindow = JFrame("GL canvas probe").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(1280, 720)
        add(canvas)
        isVisible = true
    }

    // Dedicated render thread, exactly like the shipped DesktopGlSurface: the GL context and every
    // draw live off the EDT so the vsync swap never blocks the UI.
    val renderThread = Thread {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 7000) {
            if (canvas.isValid) runCatching { canvas.render() }.onFailure { it.printStackTrace() }
            Thread.sleep(4)
        }
        println("PROBE_DONE")
        SwingUtilities.invokeLater { frameWindow.dispose() }
        runCatching { engine.release() }
        kotlin.system.exitProcess(0)
    }
    renderThread.isDaemon = true
    renderThread.start()

    // Keep main alive until the render thread ends the process.
    renderThread.join()
}

/** Reads the current GL framebuffer (RGBA) into a PNG, flipping GL's bottom-left origin. */
private fun saveGl(w: Int, h: Int, path: String) {
    val buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4)
    GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf)
    val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = ((h - 1 - y) * w + x) * 4
            val r = buf.get(i).toInt() and 0xFF
            val g = buf.get(i + 1).toInt() and 0xFF
            val b = buf.get(i + 2).toInt() and 0xFF
            img.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }
    val out = java.io.File(path)
    javax.imageio.ImageIO.write(img, "png", out)
    println("PROBE_OUT=${out.absolutePath}")
}
