package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable

/**
 * Voice search, one platform hook.
 *
 * The television search screen offers a microphone that speaks a query into the same field the
 * on-screen keyboard types into. Speech recognition is entirely a platform concern — Android has a
 * built-in recogniser, the JVM/Windows target has none — so the capability is expressed as an
 * expect/actual controller rather than baked into the shared screen.
 *
 * A call site does two things: it reads [VoiceSearchController.available] to decide whether to draw
 * the mic at all, and it calls [VoiceSearchController.start] to begin listening, receiving the top
 * transcription through the callback. The controller reports [available] as false on any platform
 * without recognition, so the mic simply never appears there (for example on desktop/JVM).
 */
interface VoiceSearchController {
    /** True only where the platform can actually transcribe speech right now. */
    val available: Boolean

    /**
     * Begin listening. On a successful transcription [onResult] is invoked once with the top
     * result on the main thread. A failure, a cancellation, or an unavailable recogniser leaves
     * [onResult] uncalled. Safe to call when [available] is false — it is then a no-op.
     */
    fun start(onResult: (String) -> Unit)
}

/** The platform's voice-search controller, remembered for the current composition. */
@Composable
expect fun rememberVoiceSearch(): VoiceSearchController
