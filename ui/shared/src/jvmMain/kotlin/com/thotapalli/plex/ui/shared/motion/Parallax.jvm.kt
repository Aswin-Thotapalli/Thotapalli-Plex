package com.thotapalli.plex.ui.shared.motion

import androidx.compose.ui.Modifier

/**
 * Desktop (Windows) has no tilt sensor, so gyroscopic parallax is a no-op: the receiver is
 * returned untouched and the decorated element sits still. Television builds run on this same JVM
 * player path where relevant and likewise get no motion, exactly as intended.
 */
actual fun Modifier.gyroParallax(
    depthX: Float,
    depthY: Float,
    enabled: Boolean,
): Modifier = this
