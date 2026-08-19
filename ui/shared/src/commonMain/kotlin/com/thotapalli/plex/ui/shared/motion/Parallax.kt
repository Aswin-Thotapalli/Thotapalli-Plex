package com.thotapalli.plex.ui.shared.motion

import androidx.compose.ui.Modifier

/**
 * Gyroscopic parallax — a hero backdrop or poster leans in 3D as the phone is tilted.
 *
 * The element is shifted by a few dp through a [androidx.compose.ui.graphics.graphicsLayer]
 * translation driven by the device's tilt, so the picture appears to sit a little behind the
 * glass and slide against the motion of the hand. This is a purely additive modifier: it changes
 * nothing about layout, never redraws the content it decorates, and is meant to be stacked onto
 * an existing hero or tile.
 *
 * Only the owner's phones (Samsung Galaxy S26 / S26 Ultra, with a real rotation-vector sensor)
 * feel this. On a television, a desktop, or any device without a usable sensor the actual is a
 * no-op that returns the receiver untouched, so those targets pay nothing and behave normally.
 *
 * @param depthX the maximum horizontal shift, in dp, at full left/right tilt. The offset is
 *   clamped to ±[depthX].
 * @param depthY the maximum vertical shift, in dp, at full forward/back tilt. The offset is
 *   clamped to ±[depthY].
 * @param enabled when false the modifier is inert on every platform.
 */
expect fun Modifier.gyroParallax(
    depthX: Float = 12f,
    depthY: Float = 12f,
    enabled: Boolean = true,
): Modifier
