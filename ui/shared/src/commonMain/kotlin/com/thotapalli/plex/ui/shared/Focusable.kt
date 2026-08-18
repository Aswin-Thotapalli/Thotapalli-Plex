package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.Motion
import com.thotapalli.plex.ui.design.PlexTheme

/**
 * The television focus treatment from CLAUDE.md section 12: scale 1.08 over 120 ms with a
 * 3 dp accent focus ring.
 *
 * Applied on every target, not only television. A Windows window is driven by keyboard as
 * well as pointer, and a focus ring that existed only on television would leave the
 * desktop with no visible focus at all.
 */
fun Modifier.plexFocusable(
    shape: RoundedCornerShape,
    enabled: Boolean = true,
    scaleOnFocus: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colours = PlexTheme.colours

    val scale by animateFloatAsState(
        targetValue = if (focused && scaleOnFocus) Layout.TELEVISION_FOCUS_SCALE else 1f,
        animationSpec = Motion.focus(),
        label = "focus-scale",
    )

    this
        .scale(scale)
        .border(
            width = if (focused) Layout.focusRingWidth else 0.dp,
            color = if (focused) colours.focusRing else Color.Transparent,
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    // The focus ring above is the whole indication. A Material ripple on
                    // top of poster artwork reads as a smudge.
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
            } else {
                Modifier.focusable(enabled = enabled, interactionSource = interactionSource)
            },
        )
}
