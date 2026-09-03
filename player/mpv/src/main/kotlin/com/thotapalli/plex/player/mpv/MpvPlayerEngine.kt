package com.thotapalli.plex.player.mpv

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.thotapalli.plex.core.playback.PlaybackFailure
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.core.playback.PlayerTracks
import com.thotapalli.plex.core.playback.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The libmpv engine for Windows, set up as CLAUDE.md section 8 specifies.
 *
 * mpv renders video only. Compose draws every control above it, which is why the on screen
 * controller and the input bindings are switched off rather than merely unused.
 */
class MpvPlayerEngine(
    private val scope: CoroutineScope,
    private val lib: LibMpv = LibMpv.load(),
    private val render: LibMpvRender = LibMpvRender.load(),
) : PlayerEngine {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _tracks = MutableStateFlow(PlayerTracks())
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var handle: Pointer? = null
    private var pollJob: Job? = null
    private var eventJob: Job? = null
    private var scrubbing = false

    // Render-API state (single-window desktop player). Held as fields so the JNA callback and its
    // parameter memory are never garbage-collected while mpv still holds pointers to them.
    // renderCtx is @Volatile because [detachRenderContext] may null it from the teardown thread while
    // the GL thread is between reading it and calling into it; the volatile read gives that visibility.
    @Volatile private var renderCtx: Pointer? = null
    private var glInitParams: MpvOpenGLInitParams? = null
    private var glGetProc: MpvGetProcAddressFn? = null
    private var glUpdateCb: MpvRenderUpdateFn? = null
    private var glApiType: com.sun.jna.Memory? = null

    // Per-frame render parameters, allocated once and mutated each frame rather than rebuilt. renderInto
    // runs only on the GL thread, so these need no synchronisation; caching them keeps a 60 fps render
    // loop from churning native malloc/free (and JNA GC pins) every single frame. See [renderInto].
    private var fboStruct: MpvOpenGLFbo? = null
    private var flipMem: com.sun.jna.Memory? = null
    private var renderParams: MpvRenderParams? = null

    /** True when [initialise] ran with render mode, so [load] defers until the render context exists. */
    private var renderModeActive = false

    /**
     * Guards the render-context free so [detachRenderContext] (the GL thread) and [release] (the
     * screen's teardown, another thread) cannot double-free it or free it after the mpv handle is
     * gone. The render context must be freed before `mpv_terminate_destroy`.
     */
    private val renderLock = Any()

    /**
     * A load requested before the render context existed, replayed once it does. With `vo=libmpv` the
     * video output is bound at loadfile time, so loading before the framebuffer target is known leaves
     * the picture black; deferring guarantees the context is always in place first. See
     * [createRenderContext].
     */
    private var pendingLoad: Pair<PlaybackSource, Long>? = null

    /**
     * Whether refresh-rate matching has been attempted for the current file. Reset on every
     * [load] so auto-play-next, which reuses this engine, re-evaluates for the next episode.
     */
    private var rateMatchAttempted = false

    /** Logged once per file, the first time playback is actually running, so a silent fall back from
     *  hardware to software decode is visible in the logs rather than only felt as a hot CPU. */
    private var hwdecLogged = false

    /**
     * Creates the mpv instance and applies the section 8 options.
     *
     * @param windowHandle the HWND mpv renders into. mpv owns that surface entirely and
     *   Compose composites its own content above it, so the video is never redrawn for an
     *   interface change.
     */
    fun initialise(windowHandle: Long?, renderMode: Boolean = false) {
        check(handle == null) { "already initialised" }

        val created = lib.mpv_create() ?: error("mpv_create returned null")

        MPV_OPTIONS.forEach { (name, value) ->
            val result = lib.mpv_set_option_string(created, name, value)
            // A refused option is worth knowing about, but not worth refusing to play over:
            // a newer or older libmpv can drop one without the rest becoming wrong.
            if (result < 0) {
                System.err.println("libmpv rejected $name=$value: ${lib.mpv_error_string(result)}")
            }
        }

        // Render-API mode: mpv renders into the framebuffer we hand it via the render context rather
        // than owning a window, so vo becomes libmpv. hwdec also moves off the D3D11VA path from the
        // section 8 defaults, because that produces D3D11 textures the OpenGL renderer cannot map;
        // `auto` keeps hardware decode but through the GL-interop path (zero-copy via
        // WGL_NV_DX_interop where the GPU supports it, a copy otherwise). Sync and audio passthrough
        // are unchanged, so playback quality is identical — only the compositing moves into our window.
        if (renderMode) {
            lib.mpv_set_option_string(created, "vo", "libmpv")
            lib.mpv_set_option_string(created, "hwdec", "auto")
            // The render context is created with API type OpenGL, so the internal GPU context must be
            // OpenGL too. The section 8 default (gpu-api=d3d11) makes mpv build a D3D11 context that
            // the OpenGL render API cannot present into, and the result is a black picture with no
            // error — so it is overridden here to match the framebuffer we actually hand mpv.
            lib.mpv_set_option_string(created, "gpu-api", "opengl")
            lib.mpv_set_option_string(created, "gpu-context", "auto")
        }

        windowHandle?.let { lib.mpv_set_option_string(created, "wid", it.toString()) }

        val initResult = lib.mpv_initialize(created)
        if (initResult < 0) {
            lib.mpv_terminate_destroy(created)
            _state.value = PlaybackState.Failed(
                PlaybackFailure.DECODER_INITIALISATION,
                IllegalStateException("mpv_initialize: ${lib.mpv_error_string(initResult)}"),
            )
            return
        }

        handle = created
        renderModeActive = renderMode
        observeProperties(created)
        startEventLoop(created)
        startPositionPolling(created)
    }

    // --- render API (single-window desktop player) ------------------------------------
    //
    // The GL player owns the context and the swap; the engine owns mpv. These four methods are the
    // bridge: the player hands mpv its GL proc resolver, then each frame asks mpv to draw into the
    // framebuffer the player is about to present, and tells mpv when that present happened. Every
    // call runs on the player's render thread, which is the same thread the GL context is current on.

    /**
     * Creates mpv's render context bound to the caller's current OpenGL context. [getProc] resolves
     * GL function names against that context. Must be called once, after [initialise] with
     * `renderMode = true`, on the thread the GL context is current on. Returns false if mpv refuses.
     *
     * [onUpdate], if given, is mpv's "a new frame is ready" signal; it can fire from any thread, so
     * it must only wake the render thread, never render inline.
     */
    fun createRenderContext(getProc: MpvGetProcAddressFn, onUpdate: MpvRenderUpdateFn? = null): Boolean {
        val h = handle ?: return false
        if (renderCtx != null) return true

        val initParams = MpvOpenGLInitParams(get_proc_address = getProc).apply { write() }
        val apiType = com.sun.jna.Memory(("opengl".toByteArray().size + 1).toLong()).apply {
            setString(0, "opengl")
        }
        val createParams = MpvRenderParams(
            listOf(
                LibMpvRender.MPV_RENDER_PARAM_API_TYPE to apiType,
                LibMpvRender.MPV_RENDER_PARAM_OPENGL_INIT_PARAMS to initParams.pointer,
            ),
        )
        val ref = PointerByReference()
        val rc = render.mpv_render_context_create(ref, h, createParams.pointer)
        if (rc < 0) {
            System.err.println("mpv_render_context_create failed: ${lib.mpv_error_string(rc)}")
            return false
        }

        // Hold every native structure the context now points at, so the GC cannot collect the
        // callback or its parameter memory while mpv still holds the pointers.
        glGetProc = getProc
        glInitParams = initParams
        glApiType = apiType
        renderCtx = ref.value

        onUpdate?.let {
            glUpdateCb = it
            render.mpv_render_context_set_update_callback(ref.value!!, it, null)
        }

        // A load requested before the context existed was held back; now the framebuffer target is
        // known, so play it. See [load] and [pendingLoad].
        pendingLoad?.let { (source, startAtMs) ->
            pendingLoad = null
            load(source, startAtMs)
        }
        return true
    }

    /**
     * True when mpv has a new frame ready to draw since the last [renderInto]. Used to skip
     * redundant renders while still redrawing the overlay; safe to ignore and render every tick.
     */
    fun hasFrameReady(): Boolean {
        val ctx = renderCtx ?: return false
        return render.mpv_render_context_update(ctx) and LibMpvRender.MPV_RENDER_UPDATE_FRAME != 0L
    }

    /**
     * Draws the current video frame into the OpenGL framebuffer [fbo] at [w]×[h]. [flipY] should be
     * true when the target is a normal top-left-origin surface (as with an AWT/Skia framebuffer).
     * No-ops until [createRenderContext] has succeeded.
     */
    fun renderInto(fbo: Int, w: Int, h: Int, flipY: Boolean = true) {
        val ctx = renderCtx ?: return

        // Reuse the same native structures every frame, only updating the values. The pointers the
        // render-param block holds stay valid because the structs behind them are never reallocated.
        val fs = fboStruct ?: MpvOpenGLFbo().also { fboStruct = it }
        fs.fbo = fbo
        fs.w = w
        fs.h = h
        // Naming the framebuffer's real format (GL_RGBA8, matching the RGBA8 default framebuffer the
        // AWT canvas was created with) rather than 0 stops mpv from having to guess it, which on some
        // drivers it guesses wrong — producing banding or a washed-out picture.
        fs.internal_format = GL_RGBA8
        fs.write()

        val fm = flipMem ?: com.sun.jna.Memory(4).also { flipMem = it }
        fm.setInt(0, if (flipY) 1 else 0)

        val params = renderParams ?: MpvRenderParams(
            listOf(
                LibMpvRender.MPV_RENDER_PARAM_OPENGL_FBO to fs.pointer,
                LibMpvRender.MPV_RENDER_PARAM_FLIP_Y to fm,
            ),
        ).also { renderParams = it }
        render.mpv_render_context_render(ctx, params.pointer)
    }

    /** Tells mpv the last [renderInto] was presented to the display, for its vsync timing. */
    fun reportSwap() {
        renderCtx?.let { render.mpv_render_context_report_swap(it) }
    }

    /**
     * Frees the render context, on the caller's (GL) thread, before the GL context it was created
     * against goes away. Idempotent and safe to race with [release]: the shared [renderLock] and the
     * null-out ensure the context is freed exactly once. Call this from the GL thread's teardown, so
     * the free happens on the thread that owns the GL context and before [release] destroys mpv.
     */
    fun detachRenderContext() {
        synchronized(renderLock) {
            renderCtx?.let { render.mpv_render_context_free(it) }
            renderCtx = null
            glUpdateCb = null
            glGetProc = null
            glInitParams = null
            glApiType = null
        }
    }

    override fun load(source: PlaybackSource, startAtMs: Long) {
        val h = handle ?: run {
            _state.value = PlaybackState.Failed(PlaybackFailure.DECODER_INITIALISATION, null)
            return
        }

        // Render mode: hold the load until the GL render context is up, then replay it in
        // [createRenderContext]. loadfile before the context leaves the picture black (vo=libmpv binds
        // its output target at load time). A repeat load once the context exists proceeds normally.
        if (renderModeActive && renderCtx == null) {
            pendingLoad = source to startAtMs
            _state.value = PlaybackState.Buffering
            return
        }

        _state.value = PlaybackState.Buffering
        rateMatchAttempted = false
        hwdecLogged = false

        // Plex needs the token and the identity headers on the stream request too, since
        // mpv fetches it itself rather than through this client's HTTP stack.
        if (source.headers.isNotEmpty()) {
            val headerFields = source.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            lib.mpv_set_option_string(h, "http-header-fields", headerFields)
        }

        val startSeconds = (startAtMs / 1000.0).toString()
        lib.mpv_set_option_string(h, "start", startSeconds)

        // Preferred track languages, applied to the initial selection mpv makes on load.
        // These are per-file options set before loadfile so mpv picks the right tracks for
        // this source; the overlay can still override with selectAudioTrack/selectSubtitleTrack.
        source.preferredAudioLanguage?.let { lib.mpv_set_option_string(h, "alang", it) }
        source.preferredSubtitleLanguage?.let { lib.mpv_set_option_string(h, "slang", it) }

        // Subtitle default: with subtitlesOnByDefault, let mpv auto-select a track (honouring
        // slang above) and make it visible; otherwise start with subtitles off entirely.
        if (source.subtitlesOnByDefault) {
            lib.mpv_set_option_string(h, "sid", "auto")
            lib.mpv_set_option_string(h, "sub-visibility", "yes")
        } else {
            lib.mpv_set_option_string(h, "sid", "no")
            lib.mpv_set_option_string(h, "sub-visibility", "no")
        }

        command(h, "loadfile", source.uri, "replace")
    }

    override fun play() {
        handle?.let { lib.mpv_set_property_string(it, "pause", "no") }
    }

    override fun pause() {
        handle?.let { lib.mpv_set_property_string(it, "pause", "yes") }
    }

    override fun seekTo(ms: Long) {
        val h = handle ?: return
        command(h, "seek", (ms / 1000.0).toString(), "absolute")
        _positionMs.value = ms
    }

    /**
     * While the seek bar is being dragged, exact seeking is turned off so each intermediate
     * position lands on a keyframe rather than decoding forward to it. hr-seek goes back on
     * at drag end so the final position is the one the viewer chose.
     */
    override fun setScrubbing(active: Boolean) {
        val h = handle ?: return
        scrubbing = active
        lib.mpv_set_property_string(h, "hr-seek", if (active) "no" else "yes")
    }

    override fun selectAudioTrack(id: String) {
        handle?.let { lib.mpv_set_property_string(it, "aid", id) }
    }

    override fun selectSubtitleTrack(id: String?) {
        handle?.let { lib.mpv_set_property_string(it, "sid", id ?: "no") }
    }

    /** Sets mpv's `speed` property. Safe to call before load; no-ops until the handle exists. */
    override fun setPlaybackSpeed(speed: Float) {
        handle?.let { lib.mpv_set_property_string(it, "speed", speed.toDouble().toString()) }
    }

    /**
     * Applies subtitle appearance by mapping [SubtitleStyle] onto mpv's sub options:
     *   - sub-scale       scales the default size (scalePercent / 100).
     *   - sub-color       the text colour, "#RRGGBB" from the low 24 bits of foregroundArgb.
     *   - sub-back-color  a black box behind the text, "#AARRGGBB" with alpha from the opacity.
     *
     * No-ops until the handle is ready, so it is safe to call before load.
     */
    override fun setSubtitleStyle(style: SubtitleStyle) {
        val h = handle ?: return

        lib.mpv_set_property_string(h, "sub-scale", (style.scalePercent / 100.0).toString())

        val rgb = style.foregroundArgb and 0xFFFFFFL
        lib.mpv_set_property_string(h, "sub-color", "#%06X".format(rgb))

        val alpha = (style.backgroundOpacityPercent.coerceIn(0, 100) * 255 / 100)
        lib.mpv_set_property_string(h, "sub-back-color", "#%02X000000".format(alpha))
    }

    override fun release() {
        pollJob?.cancel()
        eventJob?.cancel()
        pollJob = null
        eventJob = null

        // The render context holds the GL objects and must be freed before mpv is destroyed. In the
        // single-window player the GL surface frees it on its own thread first (detachRenderContext),
        // so this usually finds it already gone; the shared lock and null-out make the two paths safe
        // to race — the context is freed exactly once, always before mpv_terminate_destroy.
        detachRenderContext()

        handle?.let { lib.mpv_terminate_destroy(it) }
        handle = null
        _state.value = PlaybackState.Idle

        // Put any refresh-rate change from this session back before the player leaves. Guarded
        // inside the matcher, so a failure here never obstructs teardown. See CLAUDE.md section 9.
        runCatching { DisplayRateMatcher.restore() }
    }

    /**
     * The content frame rate mpv measured, or null before the first frame.
     *
     * `estimated-vf-fps` is mpv's measured rate after the video filter chain, which is what the
     * display should be matched against; `container-fps` is the muxer's declared rate, used as a
     * fallback when the estimate is not yet available. See CLAUDE.md section 9.
     */
    fun contentFrameRate(): Double? {
        val h = handle ?: return null
        property(h, "estimated-vf-fps")?.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { return it }
        return property(h, "container-fps")?.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    // --- internals --------------------------------------------------------------------

    private fun command(h: Pointer, vararg args: String) {
        // The argument array is null terminated, which is how mpv finds its end.
        val result = lib.mpv_command(h, arrayOf(*args, null))
        if (result < 0) {
            System.err.println("libmpv command ${args.first()} failed: ${lib.mpv_error_string(result)}")
        }
    }

    private fun property(h: Pointer, name: String): String? {
        val pointer = lib.mpv_get_property_string(h, name) ?: return null
        return try {
            pointer.getString(0)
        } finally {
            lib.mpv_free(pointer)
        }
    }

    private fun observeProperties(h: Pointer) {
        listOf("pause", "eof-reached", "duration", "track-list").forEach {
            lib.mpv_observe_property(h, 0, it, LibMpv.MPV_FORMAT_STRING)
        }
    }

    private fun startEventLoop(h: Pointer) {
        eventJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                // A blocking wait on a background thread, rather than a poll, so an event
                // is acted on when it happens rather than up to an interval later.
                lib.mpv_wait_event(h, EVENT_WAIT_SECONDS)
                refreshState(h)
            }
        }
    }

    private fun startPositionPolling(h: Pointer) {
        pollJob = scope.launch {
            while (isActive) {
                property(h, "time-pos")?.toDoubleOrNull()?.let {
                    _positionMs.value = (it * 1000).toLong()
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun refreshState(h: Pointer) {
        property(h, "duration")?.toDoubleOrNull()?.let { _durationMs.value = (it * 1000).toLong() }
        property(h, "track-list")?.let { _tracks.value = parseTrackList(it) }

        val paused = property(h, "pause") == "yes"
        val eof = property(h, "eof-reached") == "yes"
        val idle = property(h, "core-idle") == "yes"

        _state.value = when {
            _state.value is PlaybackState.Failed -> _state.value
            eof -> PlaybackState.Ended
            paused -> PlaybackState.Paused
            idle -> PlaybackState.Buffering
            else -> PlaybackState.Playing
        }

        if (!hwdecLogged && _state.value is PlaybackState.Playing) {
            hwdecLogged = true
            val hwdec = property(h, "hwdec-current")
            System.err.println(
                "libmpv hwdec-current=$hwdec" +
                    if (hwdec.isNullOrBlank() || hwdec == "no") " (software decode — CPU-bound)" else "",
            )
        }

        maybeMatchDisplayRate(h)
    }

    /**
     * Once the first frame has been decoded and a frame rate is known, match the display rate to
     * it, once per file. The switch itself blanks the screen for a moment and can block, so it
     * runs off the event thread on the engine scope, and the matcher swallows every failure so
     * playback is never affected. See CLAUDE.md section 9.
     *
     * Not done in render mode. The single-window player composites the video into an embedded AWT
     * OpenGL surface, and a Windows refresh-rate switch (`ChangeDisplaySettingsEx`, `CDS_FULLSCREEN`)
     * destroys that surface's JAWT drawing surface below the GL layer — the picture goes black and
     * cannot be recovered while audio keeps playing. Drift is already handled by
     * `video-sync=display-resample` plus the vsync'd swap, so playback stays smooth without the
     * switch; only the judder optimisation for 25/50fps content on a non-divisible display is given
     * up, which section 9 makes optional and off by default on Windows anyway.
     */
    private fun maybeMatchDisplayRate(h: Pointer) {
        if (renderModeActive) return
        if (rateMatchAttempted) return
        val fps = contentFrameRate() ?: return
        rateMatchAttempted = true
        scope.launch(Dispatchers.IO) {
            runCatching { DisplayRateMatcher.matchForContentRate(fps) }
        }
    }

    private companion object {
        const val POSITION_POLL_MS = 250L
        const val EVENT_WAIT_SECONDS = 0.5

        /** GL_RGBA8, the internal format of the default framebuffer the AWT canvas creates. */
        const val GL_RGBA8 = 0x8058
    }
}

/**
 * Parses mpv's track-list property.
 *
 * The property comes back as JSON. It is read with a small hand written scan rather than a
 * JSON library so player/mpv keeps no dependency beyond JNA; the shape is fixed and shallow.
 */
internal fun parseTrackList(json: String): PlayerTracks {
    val audio = mutableListOf<PlayerTrack>()
    val subtitle = mutableListOf<PlayerTrack>()

    // Entries are flat objects, so splitting on braces is sufficient and unambiguous.
    json.split('{').drop(1).forEach { entry ->
        val body = entry.substringBefore('}')
        val type = body.field("type") ?: return@forEach
        val id = body.field("id") ?: return@forEach
        val language = body.field("lang")
        val title = body.field("title")
        val codec = body.field("codec")
        val selected = body.contains("\"selected\":true")

        val track = PlayerTrack(
            id = id,
            label = listOfNotNull(title, language, codec?.uppercase())
                .joinToString(" ")
                .ifBlank { "Track $id" },
            language = language,
            selected = selected,
        )

        when (type) {
            "audio" -> audio += track
            "sub" -> subtitle += track
        }
    }

    return PlayerTracks(audio = audio, subtitle = subtitle)
}

private fun String.field(name: String): String? {
    val key = "\"$name\":"
    val start = indexOf(key)
    if (start < 0) return null
    var cursor = start + key.length
    while (cursor < length && this[cursor] == ' ') cursor++
    if (cursor >= length) return null

    return if (this[cursor] == '"') {
        val end = indexOf('"', cursor + 1)
        if (end < 0) null else substring(cursor + 1, end)
    } else {
        val end = indexOfFirst(cursor) { it == ',' || it == '}' }
        substring(cursor, if (end < 0) length else end).trim().takeIf { it.isNotBlank() }
    }
}

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return -1
}
