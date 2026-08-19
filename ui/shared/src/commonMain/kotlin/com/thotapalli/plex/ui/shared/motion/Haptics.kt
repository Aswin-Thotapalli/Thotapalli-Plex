package com.thotapalli.plex.ui.shared.motion

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A tasteful pair of haptic cues, routed through Compose's common
 * [androidx.compose.ui.hapticfeedback.HapticFeedback] so a single call site works everywhere: it
 * buzzes on Android (the owner's Galaxy S26 phones) and is a silent no-op on desktop and
 * television, which expose no haptic hardware.
 */
interface Haptics {
    /** A light cue for focus or selection — the tick as attention lands on a tile or control. */
    fun tick()

    /** A firmer cue confirming a deliberate action, such as committing a tap. */
    fun press()
}

/**
 * Remember a [Haptics] bound to the current [LocalHapticFeedback]. The two cues map to the
 * lightest and firmest standard feedback constants; where a platform has no haptics the calls
 * simply do nothing.
 */
@Composable
fun rememberHaptics(): Haptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        object : Haptics {
            override fun tick() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            override fun press() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

/**
 * A [clickable] that fires a confirming haptic just before it runs [onClick]. Additive and safe to
 * stack onto any element; on a platform without haptics it degrades to an ordinary click.
 *
 * @param enabled when false the element is neither clickable nor haptic.
 * @param onClick invoked on tap, after the haptic.
 */
fun Modifier.hapticClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    clickable(enabled = enabled) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}
