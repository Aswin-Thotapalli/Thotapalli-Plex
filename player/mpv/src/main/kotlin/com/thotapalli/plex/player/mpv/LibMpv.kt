package com.thotapalli.plex.player.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.nio.file.Files
import java.nio.file.Path

/**
 * The libmpv C API, as much of it as this client uses.
 *
 * Bound through JNA rather than the NDK because the desktop target is a JVM application
 * and the DLL ships beside it. See CLAUDE.md section 4.
 */
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)

    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)

    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
    fun mpv_error_string(error: Int): String

    companion object {
        /**
         * Loads libmpv-2.dll from the bundled native directory.
         *
         * The directory is registered on the JNA search path rather than the DLL being
         * loaded by absolute path, so the same code works from a Gradle run and from an
         * installed MSI where the resources sit elsewhere. See CLAUDE.md section 4.
         */
        fun load(searchDirectories: List<Path> = defaultSearchDirectories()): LibMpv {
            searchDirectories
                .filter { Files.isDirectory(it) }
                .forEach { NativeLibrary.addSearchPath(LIBRARY_NAME, it.toAbsolutePath().toString()) }

            return Native.load(LIBRARY_NAME, LibMpv::class.java)
        }

        /**
         * Where libmpv-2.dll is looked for.
         *
         * compose.application.resources.dir is set by Compose Desktop in an installed
         * application. The project path is for running from Gradle during development.
         */
        fun defaultSearchDirectories(): List<Path> = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { Path.of(it) },
            System.getProperty("compose.application.resources.dir")?.let {
                Path.of(it, "windows-x64")
            },
            Path.of("app", "desktop", "native", "windows-x64"),
            Path.of("native", "windows-x64"),
        )

        const val LIBRARY_NAME = "libmpv-2"

        /** mpv_format values. */
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_STRING = 1
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5

        /** mpv_event_id values this client reacts to. */
        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_PROPERTY_CHANGE = 22
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val MPV_EVENT_VIDEO_RECONFIG = 17
    }
}

/**
 * The options from CLAUDE.md section 8, set before mpv_initialize.
 *
 * Every one of these is deliberate. The ordering matters only in that all of them must be
 * set before initialisation, since several cannot be changed afterwards.
 */
val MPV_OPTIONS: List<Pair<String, String>> = listOf(
    "vo" to "gpu-next",
    "gpu-api" to "d3d11",
    "hwdec" to "d3d11va",

    // The most important option in the list. Locks video presentation to the display clock
    // and resamples audio by the small difference, which removes clock drift permanently.
    "video-sync" to "display-resample",

    // Frames stay untouched. Judder is solved by section 9 instead.
    "interpolation" to "no",

    // Highest-quality scaling, driving the gpu-next renderer in both the windowed and the
    // single-window (render-API) paths. ewa_lanczossharp is the sharpest antiringing upscaler for
    // luma and chroma; mitchell downscales without the aliasing a plain lanczos leaves. This is the
    // quality ceiling section 8's "correct frame pacing" leaves room for, not a change to pacing.
    "scale" to "ewa_lanczossharp",
    "cscale" to "ewa_lanczossharp",
    "dscale" to "mitchell",

    "hr-seek" to "yes",
    "hr-seek-framedrop" to "no",
    "keep-open" to "yes",
    "cache" to "yes",
    "demuxer-max-bytes" to "256MiB",
    "demuxer-readahead-secs" to "20",

    // Section 8: exclusive audio plus spdif so Dolby/DTS bitstream reaches a receiver
    // untouched. The tradeoff: WASAPI exclusive mode can fail or stall on an ordinary
    // desktop whose output device is already held by another app, which earlier read as
    // "buffering forever". This is made safely reversible rather than disabled:
    //
    //   - audio-exclusive=yes and audio-spdif restore the spec-correct passthrough path.
    //   - audio-fallback-to-null=no means a device that will not open exclusively does NOT
    //     get silently routed to the null sink; mpv retries the ao chain and lands on the
    //     shared/PCM path instead of a null device that would look like a permanent stall.
    //   - The load path (MpvPlayerEngine.load) does not block waiting on the ao opening, so
    //     even a slow exclusive-mode negotiation cannot hang the caller.
    //
    // Net effect: passthrough by default per spec; on a device where exclusive/spdif cannot
    // open, mpv degrades to shared PCM and playback continues rather than hanging.
    "audio-exclusive" to "yes",
    "audio-spdif" to "ac3,eac3,dts-hd,truehd",
    "audio-fallback-to-null" to "no",

    "sub-auto" to "no",
    "sub-ass-override" to "no",

    // Compose draws the interface. mpv renders video only.
    "osc" to "no",
    "osd-level" to "0",
    "input-default-bindings" to "no",
    "input-vo-keyboard" to "no",
)
