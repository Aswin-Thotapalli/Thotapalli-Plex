package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.GlassDefaults
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.Motion
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.shared.motion.rememberHaptics

/**
 * The television focus treatment from CLAUDE.md section 12: scale 1.08 over 120 ms with a
 * 3 dp accent focus ring.
 *
 * Applied on every target, not only television. A Windows window is driven by keyboard as
 * well as pointer, and a focus ring that existed only on television would leave the
 * desktop with no visible focus at all.
 *
 * Desktop pointer hover is treated as a lighter kind of focus: the same accent ring and the
 * same grow, so a mouse user gets the identical "this is the thing under the cursor" cue a
 * remote user gets, without disturbing the keyboard focus model. Signature is unchanged, so
 * every existing call site keeps working; the hover behaviour is entirely internal.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.plexFocusable(
    shape: RoundedCornerShape,
    enabled: Boolean = true,
    scaleOnFocus: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** A long-press (touch) on the same node that handles the click, so a poster's press-and-hold
     *  opens its menu. Folded into the click's own [combinedClickable] rather than a separate parent
     *  gesture layer, which a child clickable would swallow (that was the "long press does nothing"
     *  bug). See CLAUDE.md section 5. */
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val colours = PlexTheme.colours
    val haptics = rememberHaptics()

    val active = focused || hovered

    // Hover lifts a touch less than focus so a keyboard or remote target still reads as the
    // stronger selection when both happen to be true.
    val focusScale = when {
        !scaleOnFocus -> 1f
        focused -> Layout.TELEVISION_FOCUS_SCALE
        hovered -> 1f + (Layout.TELEVISION_FOCUS_SCALE - 1f) * 0.5f
        else -> 1f
    }

    // A press squashes the target down into a soft bubble, on top of whatever the focus grow is
    // doing, so every control in the app answers a tap with the same springy give. Applied even
    // where the focus grow is suppressed, since it is tap feedback rather than a selection cue.
    val targetScale =
        if (pressed && onClick != null) focusScale * GlassDefaults.pressedScale else focusScale

    // One snappy spring drives focus, hover and press alike, so nothing in the interface reads as
    // a mechanical tween. Bouncy on the press so the release pops; firmer for focus and hover.
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (pressed) Motion.springBouncy() else Motion.spring(),
        label = "focus-scale",
    )

    this
        .scale(scale)
        .border(
            width = if (active) Layout.focusRingWidth else 0.dp,
            color = if (active) colours.focusRing else Color.Transparent,
            shape = shape,
        )
        .hoverable(interactionSource = interactionSource, enabled = enabled)
        .then(
            when {
                onClick != null && onLongClick != null -> Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = { haptics.press(); onClick() },
                    onLongClick = { haptics.press(); onLongClick() },
                )

                onClick != null -> Modifier.clickable(
                    interactionSource = interactionSource,
                    // The focus ring above is the whole indication. A Material ripple on
                    // top of poster artwork reads as a smudge.
                    indication = null,
                    enabled = enabled,
                    onClick = { haptics.press(); onClick() },
                )

                else -> Modifier.focusable(enabled = enabled, interactionSource = interactionSource)
            },
        )
}
