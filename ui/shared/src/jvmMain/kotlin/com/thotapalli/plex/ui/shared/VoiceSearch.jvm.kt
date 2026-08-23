package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable

/**
 * The JVM/Windows target has no built-in speech recognition, so this controller reports itself
 * unavailable and its [start] is a no-op. The television form factor — the only place the mic is
 * offered — is Android, so this actual exists purely to satisfy the JVM target's compile. Because
 * [available] is false, the shared search screen never draws the mic on desktop.
 */
@Composable
actual fun rememberVoiceSearch(): VoiceSearchController = JvmVoiceSearchController

private object JvmVoiceSearchController : VoiceSearchController {
    override val available: Boolean = false
    override fun start(onResult: (String) -> Unit) {
        // No speech recognition on this platform.
    }
}
