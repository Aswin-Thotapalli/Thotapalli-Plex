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

    "hr-seek" to "yes",
    "hr-seek-framedrop" to "no",
    "keep-open" to "yes",
    "cache" to "yes",
    "demuxer-max-bytes" to "256MiB",
    "demuxer-readahead-secs" to "20",

    // Section 8 asks for exclusive audio so bitstream passthrough reaches a receiver
    // untouched. In practice WASAPI exclusive mode fails or stalls on an ordinary desktop
    // whose output device is shared, which reads as "buffering forever". Shared mode is
    // used for reliable playback; passthrough for a receiver is a follow-up.
    "audio-exclusive" to "no",

    "sub-auto" to "no",
    "sub-ass-override" to "no",

    // Compose draws the interface. mpv renders video only.
    "osc" to "no",
    "osd-level" to "0",
    "input-default-bindings" to "no",
    "input-vo-keyboard" to "no",
)
