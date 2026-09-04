package com.thotapalli.plex.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A tiny in-memory diagnostics log for an app that ships no telemetry.
 *
 * Thotapalli Plex reports nothing home by design, so when playback falls back to a transcode, a
 * server connection is re-probed, the cache is rebuilt, or a download fails, the only trace is a
 * line in Logcat or stderr that vanishes. This records those moments into a small ring buffer the
 * viewer can read (and copy) from Settings, so a real-world problem across phone, TV or Windows is
 * observable without any network reporting.
 *
 * It is a process-wide singleton rather than an injected dependency on purpose: the record sites are
 * scattered across core:api, core:data, core:playback and core:download, and threading a recorder
 * through every constructor would be far more invasive than the value warrants for a diagnostic
 * sink. It holds no secrets — messages are short, human-readable, and never carry tokens or URLs
 * with credentials.
 *
 * The buffer lives only in memory: it is not persisted, so it is empty on a cold launch and reflects
 * the current session. That is the right scope for "what has gone wrong since I opened the app".
 */
object Diagnostics {

    /**
     * Wall-clock source, set once at startup (AppContainer) to the same clock the rest of the app
     * uses. Kept settable so core:model stays free of any platform time API; until it is set, events
     * carry a zero timestamp and the UI falls back to showing them in order.
     */
    var clock: () -> Long = { 0L }

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())

    /** The recorded events, oldest first, capped at [MAX_EVENTS]. */
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    /** Records one event. Safe to call from any thread; [MutableStateFlow.update] is atomic. */
    fun record(category: DiagnosticCategory, message: String) {
        val event = DiagnosticEvent(atMs = clock(), category = category, message = message)
        _events.update { current ->
            // Keep the newest MAX_EVENTS; drop the oldest so the buffer can never grow without bound.
            (current + event).let { if (it.size > MAX_EVENTS) it.takeLast(MAX_EVENTS) else it }
        }
    }

    fun clear() {
        _events.value = emptyList()
    }

    /** A plain-text dump of the whole log, newest last, for copying out of Settings. */
    fun exportText(): String = _events.value.joinToString("\n") { event ->
        "${event.atMs}  ${event.category.name}  ${event.message}"
    }

    /** A ring buffer this deep is plenty to see a session's problems without unbounded growth. */
    private const val MAX_EVENTS = 200
}

/** One recorded diagnostic moment. */
data class DiagnosticEvent(
    /** Wall-clock milliseconds when it was recorded, or 0 before [Diagnostics.clock] is set. */
    val atMs: Long,
    val category: DiagnosticCategory,
    val message: String,
)

/** The kinds of moment worth recording — the ones that explain a real-world hiccup. */
enum class DiagnosticCategory {
    /** A connection to the server was selected, or none answered. */
    CONNECTION,

    /** Playback fell back (direct → transcode), or failed with no recovery left. */
    PLAYBACK,

    /** The local cache was rebuilt because its schema epoch changed. */
    CACHE,

    /** A download failed after exhausting its retries. */
    DOWNLOAD,
}
