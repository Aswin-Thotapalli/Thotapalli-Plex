package com.thotapalli.plex.player.mpv

import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * The libmpv **render API** — the part of libmpv that lets the host own the GPU surface and ask mpv
 * to render a frame into an OpenGL framebuffer we control, instead of mpv opening its own window.
 *
 * This is what makes the single-window desktop player possible: mpv renders the video into our GL
 * context and the Compose overlay is composited on top of it in the same window (see the desktop
 * app's GL player). See CLAUDE.md section 8 — the surface rule ("controls are a layer above the
 * video") is finally satisfied literally, in one window, rather than through a second overlay window.
 *
 * Bound as a second JNA interface over the same libmpv-2.dll as [LibMpv].
 */
interface LibMpvRender : com.sun.jna.Library {
    /** `int mpv_render_context_create(mpv_render_context**, mpv_handle*, mpv_render_param*)`. */
    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int

    /** `void mpv_render_context_set_update_callback(ctx, cb, cb_ctx)`. */
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: MpvRenderUpdateFn?, cbCtx: Pointer?)

    /** `int mpv_render_context_render(ctx, mpv_render_param*)`. */
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int

    /** `void mpv_render_context_report_swap(ctx)` — tell mpv the frame was presented (vsync timing). */
    fun mpv_render_context_report_swap(ctx: Pointer)

    /** `uint64_t mpv_render_context_update(ctx)` — bit 0 (MPV_RENDER_UPDATE_FRAME) means a new frame is ready. */
    fun mpv_render_context_update(ctx: Pointer): Long

    /** `void mpv_render_context_free(ctx)`. */
    fun mpv_render_context_free(ctx: Pointer)

    companion object {
        fun load(): LibMpvRender = com.sun.jna.Native.load(LibMpv.LIBRARY_NAME, LibMpvRender::class.java)

        // mpv_render_param_type
        const val MPV_RENDER_PARAM_INVALID = 0
        const val MPV_RENDER_PARAM_API_TYPE = 1
        const val MPV_RENDER_PARAM_OPENGL_INIT_PARAMS = 2
        const val MPV_RENDER_PARAM_OPENGL_FBO = 3
        const val MPV_RENDER_PARAM_FLIP_Y = 4
        const val MPV_RENDER_PARAM_ADVANCED_CONTROL = 10

        const val MPV_RENDER_API_TYPE_OPENGL = "opengl"

        /** mpv_render_context_update() returns this bit when a new video frame is ready to draw. */
        const val MPV_RENDER_UPDATE_FRAME = 1L
    }
}

/** `void *(*)(void *ctx, const char *name)` — resolves an OpenGL function by name for mpv. */
fun interface MpvGetProcAddressFn : Callback {
    fun invoke(ctx: Pointer?, name: String?): Pointer?
}

/** `void (*)(void *ctx)` — mpv calls this (from any thread) when a new frame should be rendered. */
fun interface MpvRenderUpdateFn : Callback {
    fun invoke(ctx: Pointer?)
}

/** `struct mpv_opengl_init_params { get_proc_address; get_proc_address_ctx; }`. */
@Structure.FieldOrder("get_proc_address", "get_proc_address_ctx")
class MpvOpenGLInitParams(
    @JvmField var get_proc_address: MpvGetProcAddressFn? = null,
    @JvmField var get_proc_address_ctx: Pointer? = null,
) : Structure()

/** `struct mpv_opengl_fbo { int fbo; int w; int h; int internal_format; }`. */
@Structure.FieldOrder("fbo", "w", "h", "internal_format")
class MpvOpenGLFbo(
    @JvmField var fbo: Int = 0,
    @JvmField var w: Int = 0,
    @JvmField var h: Int = 0,
    @JvmField var internal_format: Int = 0,
) : Structure()

/**
 * A `mpv_render_param[]` built into one contiguous block of native memory: each entry is
 * `{ int type; void *data; }` (8-byte aligned, so 16 bytes per entry on 64-bit), terminated by a
 * zero entry. The block, its terminator and every value it points at are held alive by this object
 * so the GC cannot free them out from under a native call — keep a reference for the call's lifetime.
 */
class MpvRenderParams(entries: List<Pair<Int, Pointer?>>) {
    private val stride = 16L
    private val backing: Memory = Memory(stride * (entries.size + 1))
    val pointer: Pointer get() = backing

    init {
        entries.forEachIndexed { i, (type, data) ->
            val base = i * stride
            backing.setInt(base, type)
            backing.setPointer(base + 8, data)
        }
        // Terminating { MPV_RENDER_PARAM_INVALID, NULL }.
        val last = entries.size * stride
        backing.setInt(last, LibMpvRender.MPV_RENDER_PARAM_INVALID)
        backing.setPointer(last + 8, null)
    }
}
