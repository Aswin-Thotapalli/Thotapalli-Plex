@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.thotapalli.plex.desktop

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.thotapalli.plex.player.mpv.LibMpv
import com.thotapalli.plex.player.mpv.LibMpvRender
import com.thotapalli.plex.player.mpv.MpvGetProcAddressFn
import com.thotapalli.plex.player.mpv.MpvOpenGLFbo
import com.thotapalli.plex.player.mpv.MpvOpenGLInitParams
import com.thotapalli.plex.player.mpv.MpvRenderParams
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
import androidx.compose.ui.unit.dp
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.file.Path

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

/**
 * A standalone proof of the single-window render pipeline: it opens a plain OpenGL window (GLFW) and
 * has mpv render its built-in test pattern into that window through the render API — no second
 * window, no Compose yet. If this shows the test pattern, the mpv → our-GL-context path is sound and
 * the real player can be built on it.
 *
 *   gradlew :app:desktop:renderProbe
 */
fun main() {
    // Load libmpv from the bundled native dir (dev path), same as the app.
    LibMpv.load(listOf(Path.of("app", "desktop", "native", "windows-x64"), Path.of("native", "windows-x64")))
    val mpv = LibMpv.load()
    val render = LibMpvRender.load()

    val handle = mpv.mpv_create() ?: error("mpv_create failed")
    // Render-API mode: mpv does not open a window; we hand it our GL framebuffer. hwdec stays on so
    // decode is still hardware — only the final compositing lives in our context.
    mpv.mpv_set_option_string(handle, "vo", "libmpv")
    mpv.mpv_set_option_string(handle, "hwdec", "auto")
    mpv.mpv_set_option_string(handle, "terminal", "no")
    check(mpv.mpv_initialize(handle) == 0) { "mpv_initialize failed" }

    check(GLFW.glfwInit()) { "glfwInit failed" }
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE)
    val win = GLFW.glfwCreateWindow(1280, 720, "Thotapalli render probe", NULL, NULL)
    check(win != NULL) { "glfwCreateWindow failed" }
    GLFW.glfwMakeContextCurrent(win)
    GLFW.glfwSwapInterval(1)
    GL.createCapabilities()

    // Skia bound to THIS GL context — the same context mpv renders into. This is what composites the
    // controls over the video in one surface.
    val skiaContext = org.jetbrains.skia.DirectContext.makeGL()

    // A Compose scene rendered straight into that Skia surface — the real overlay path. Proving a
    // Compose UI composites over the video is the last piece before wiring the actual PlayerOverlay.
    val scene = androidx.compose.ui.scene.CanvasLayersComposeScene(
        density = androidx.compose.ui.unit.Density(1f),
        size = androidx.compose.ui.unit.IntSize(1280, 720),
        coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined,
        invalidate = {},
    )
    scene.setContent { ProbeOverlay() }

    // The proc-address resolver mpv uses to find GL functions — routed through GLFW's loader, which
    // resolves against the exact context we just made current.
    val getProc = MpvGetProcAddressFn { _, name ->
        val addr = if (name == null) 0L else GLFW.glfwGetProcAddress(name)
        if (addr == 0L) null else Pointer(addr)
    }
    val initParams = MpvOpenGLInitParams(get_proc_address = getProc).apply { write() }
    val apiType = Memory(("opengl".toByteArray().size + 1).toLong()).apply {
        setString(0, "opengl")
    }
    val createParams = MpvRenderParams(
        listOf(
            LibMpvRender.MPV_RENDER_PARAM_API_TYPE to apiType,
            LibMpvRender.MPV_RENDER_PARAM_OPENGL_INIT_PARAMS to initParams.pointer,
        ),
    )
    val ctxRef = PointerByReference()
    val rc = render.mpv_render_context_create(ctxRef, handle, createParams.pointer)
    check(rc == 0) { "mpv_render_context_create failed: ${mpv.mpv_error_string(rc)}" }
    val renderCtx = ctxRef.value!!

    mpv.mpv_command(handle, arrayOf("loadfile", "av://lavfi:testsrc=size=1280x720:rate=30:duration=30", null))

    val flipY = Memory(4).apply { setInt(0, 1) }
    val start = System.currentTimeMillis()
    var shot = false
    while (!GLFW.glfwWindowShouldClose(win) && System.currentTimeMillis() - start < 6000) {
        val w = intArrayOf(0); val h = intArrayOf(0)
        GLFW.glfwGetFramebufferSize(win, w, h)
        GL11.glViewport(0, 0, w[0], h[0])

        val fbo = MpvOpenGLFbo(fbo = 0, w = w[0], h = h[0], internal_format = 0).apply { write() }
        val renderParams = MpvRenderParams(
            listOf(
                LibMpvRender.MPV_RENDER_PARAM_OPENGL_FBO to fbo.pointer,
                LibMpvRender.MPV_RENDER_PARAM_FLIP_Y to flipY,
            ),
        )
        render.mpv_render_context_render(renderCtx, renderParams.pointer)

        // Composite a Skia layer over the video, in the same GL surface — the compositing proof.
        skiaContext.resetGLAll()
        val rt = org.jetbrains.skia.BackendRenderTarget.makeGL(w[0], h[0], 0, 8, 0, 0x8058 /* GL_RGBA8 */)
        val surface = org.jetbrains.skia.Surface.makeFromBackendRenderTarget(
            skiaContext,
            rt,
            org.jetbrains.skia.SurfaceOrigin.BOTTOM_LEFT,
            org.jetbrains.skia.SurfaceColorFormat.RGBA_8888,
            org.jetbrains.skia.ColorSpace.sRGB,
        )
        surface?.let { s ->
            // The Compose overlay, rendered straight into the surface over the video.
            scene.render(s.canvas.asComposeCanvas(), System.nanoTime())
            s.flushAndSubmit()
            s.close()
        }
        rt.close()

        // Read the framebuffer straight back and save it — deterministic proof of what rendered,
        // independent of whether the window is visible or on top.
        if (!shot && System.currentTimeMillis() - start > 2500) {
            shot = true
            saveFramebuffer(w[0], h[0], System.getProperty("probe.out", "probe_render.png"))
            println("PROBE_FRAME_SAVED")
        }

        GLFW.glfwSwapBuffers(win)
        render.mpv_render_context_report_swap(renderCtx)
        GLFW.glfwPollEvents()
    }

    render.mpv_render_context_free(renderCtx)
    mpv.mpv_terminate_destroy(handle)
    GLFW.glfwDestroyWindow(win)
    GLFW.glfwTerminate()
    println("PROBE_DONE")
}

/** Reads the current GL framebuffer (RGBA) and writes it to a PNG, flipping GL's bottom-left origin. */
private fun saveFramebuffer(w: Int, h: Int, path: String) {
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
