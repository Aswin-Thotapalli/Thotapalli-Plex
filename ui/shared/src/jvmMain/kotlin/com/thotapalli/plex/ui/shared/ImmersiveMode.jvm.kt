package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable

/** Desktop has no system bars to hide; full screen is the Windows player's own toggle. */
@Composable
actual fun ImmersiveSystemBars(hidden: Boolean) {
    // No-op on the JVM/desktop target.
}
