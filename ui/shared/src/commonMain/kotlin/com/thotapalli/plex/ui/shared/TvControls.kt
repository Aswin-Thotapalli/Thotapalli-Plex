package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.shared.motion.rememberHaptics

/**
 * The television control language, built for the ten-foot focus model.
 *
 * Everything here is deliberately the opposite of the phone's glass: solid, opaque fills, explicit
 * colours, and a focus state expressed by a clean brighten and a firm scale — no blur, no glow, no
 * dependence on ambient theme or a backdrop capture. That is what reads correctly across a room on
 * a weak TV GPU, the way Netflix, Plex and Disney+ do, and it is what fixes the washed-out,
 * label-less controls the glass buttons produced over the home backdrop.
 */

private val TvButtonShape = RoundedCornerShape(10.dp)

/** The ten-foot accent (per the TV UI reference): a bright gold that marks the primary action and
 *  focus, brighter than the phone's amber so it reads across a room. */
internal val TvGold = Color(0xFFFFD54F)
private val TvGoldBright = Color(0xFFFFE082)
internal val TvInk = Color(0xFF0A0D14)
private val TvLight = Color(0xFFF3F5F9)

/**
 * A television action control: a solid pill sized for a remote. [primary] is the amber call to
 * action (Play / Resume); the rest sit quiet as a translucent chip and flip to a solid light fill
 * when focused. Focus is a brighten + a firm 1.05 scale + a hairline light ring — never a halo.
 */
@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PlexIconKind? = null,
    primary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val haptics = rememberHaptics()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "tv-action-scale")

    // The primary is the one lit, filled, gold call to action. Secondaries stay quiet — a barely
    // there fill at rest, brightening to a solid light chip only on focus — so they never compete
    // with the primary (reference §20–21). Focus is a firm scale; only the primary/secondary-on-
    // focus carries a ring, so a quiet secondary reads as recessive.
    val background = when {
        primary && focused -> TvGoldBright
        primary -> TvGold
        focused -> TvLight
        else -> Color(0x12FFFFFF)
    }
    val content = when {
        primary || focused -> TvInk
        else -> Color(0xFFCBD1DB)
    }
    Row(
        modifier = modifier
            .scale(scale)
            .clip(TvButtonShape)
            .background(background)
            .then(
                if (focused || primary) Modifier.border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                    shape = TvButtonShape,
                ) else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { haptics.press(); onClick() },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = if (primary) 28.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            PlexIcon(kind = icon, size = if (primary) 24.dp else 20.dp, tint = content)
            Spacer(Modifier.width(10.dp))
        }
        PlexText(text = label, style = PlexTheme.type.label, colour = content, maxLines = 1)
    }
}

/**
 * A circular icon-only television control — the detail back affordance and other single-glyph
 * actions. Same focus language as [TvActionButton]: a solid light fill on focus with dark glyph,
 * a translucent chip at rest.
 */
@Composable
fun TvIconButton(
    icon: PlexIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val haptics = rememberHaptics()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tv-icon-scale")

    Box(
        modifier = modifier
            .scale(scale)
            .size(52.dp)
            .clip(CircleShape)
            .background(if (focused) TvLight else Color(0x33000000))
            .then(
                if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { haptics.press(); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlexIcon(
            kind = icon,
            size = 24.dp,
            tint = if (focused) TvInk else TvLight,
        )
    }
}
