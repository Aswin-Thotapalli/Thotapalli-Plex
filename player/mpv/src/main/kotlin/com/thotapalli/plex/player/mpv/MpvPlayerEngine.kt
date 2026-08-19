package com.thotapalli.plex.player.mpv

import com.sun.jna.Pointer
import com.thotapalli.plex.core.playback.PlaybackFailure
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlaybackState
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.core.playback.PlayerTrack
import com.thotapalli.plex.core.playback.PlayerTracks
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

    /**
     * Creates the mpv instance and applies the section 8 options.
     *
     * @param windowHandle the HWND mpv renders into. mpv owns that surface entirely and
     *   Compose composites its own content above it, so the video is never redrawn for an
     *   interface change.
     */
    fun initialise(windowHandle: Long?) {
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
        observeProperties(created)
        startEventLoop(created)
        startPositionPolling(created)
    }

    override fun load(source: PlaybackSource, startAtMs: Long) {
        val h = handle ?: run {
            _state.value = PlaybackState.Failed(PlaybackFailure.DECODER_INITIALISATION, null)
            return
        }

        _state.value = PlaybackState.Buffering

        // Plex needs the token and the identity headers on the stream request too, since
        // mpv fetches it itself rather than through this client's HTTP stack.
        if (source.headers.isNotEmpty()) {
            val headerFields = source.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            lib.mpv_set_option_string(h, "http-header-fields", headerFields)
        }

        val startSeconds = (startAtMs / 1000.0).toString()
        lib.mpv_set_option_string(h, "start", startSeconds)

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

    override fun release() {
        pollJob?.cancel()
        eventJob?.cancel()
        pollJob = null
        eventJob = null

        handle?.let { lib.mpv_terminate_destroy(it) }
        handle = null
        _state.value = PlaybackState.Idle
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
    }

    private companion object {
        const val POSITION_POLL_MS = 250L
        const val EVENT_WAIT_SECONDS = 0.5
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
