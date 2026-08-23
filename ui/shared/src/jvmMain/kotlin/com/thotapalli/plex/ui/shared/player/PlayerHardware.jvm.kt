package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Windows has no per-window brightness the app should commandeer and its media keys already own
 * volume, so the desktop [PlayerHardware] is inert: [supportsBrightness] is false, which keeps the
 * overlay from ever arming the vertical brightness/volume drag on the pointer-driven desktop. See
 * CLAUDE.md section 18.
 */
@Composable
actual fun rememberPlayerHardware(): PlayerHardware = remember { JvmPlayerHardware }

private object JvmPlayerHardware : PlayerHardware {
    override val supportsBrightness: Boolean = false
    override fun setBrightness(fraction: Float) {}
    override fun nudgeVolume(up: Boolean) {}
}
