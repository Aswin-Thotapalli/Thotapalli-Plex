package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Composable

/**
 * The device knobs the player overlay reaches for that Compose cannot touch itself: screen
 * brightness and the media volume. See CLAUDE.md section 18 (brightness/volume gestures).
 *
 * A vertical drag on the left half of the picture rides [setBrightness]; a vertical drag on the
 * right half nudges the volume through [nudgeVolume]. Only the touch platform exposes brightness —
 * [supportsBrightness] is the gate the overlay reads before it arms the gesture at all, so the
 * pointer-driven desktop never grabs a vertical drag. The desktop actual is inert.
 */
interface PlayerHardware {
    /** True only where the platform lets the app drive screen brightness, i.e. the touch device. */
    val supportsBrightness: Boolean

    /** Set the screen brightness for the current window, 0f (dimmest) to 1f (brightest). */
    fun setBrightness(fraction: Float)

    /** Nudge the media stream volume one step up or down, showing the system volume UI. */
    fun nudgeVolume(up: Boolean)
}

/** The platform's [PlayerHardware], remembered for the current composition. */
@Composable
expect fun rememberPlayerHardware(): PlayerHardware
