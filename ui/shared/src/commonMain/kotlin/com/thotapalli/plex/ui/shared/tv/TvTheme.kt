package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The ten-foot palette. Solid, opaque, high-contrast: it has to read from a sofa on a weak TV GPU,
 * so there is no glass, no blur and no dependence on what is behind a control. Focus is always the
 * same two cues — a firm scale and a gold ring — on every element in the app, so the eye learns it
 * once. See CLAUDE.md sections 12 and 13.
 */
internal object TvPalette {
    /** The ground every TV screen sits on. */
    val ground = Color(0xFF0A0D14)
    val surface = Color(0xFF151A24)
    val surfaceRaised = Color(0xFF1F2632)
    val hairline = Color(0xFF2C3442)

    /** The one accent: focus, the primary action, progress. Brighter than the phone's amber. */
    val gold = Color(0xFFFFD54F)
    val goldBright = Color(0xFFFFE082)
    val ink = Color(0xFF0A0D14)

    val text = Color(0xFFF3F5F9)
    val textDim = Color(0xFFB4BBC7)
    val textMuted = Color(0xFF7C8592)

    /** A quiet fill for a secondary control at rest. */
    val chipRest = Color(0x14FFFFFF)
    /** A secondary control under focus: solid light, dark glyph. */
    val chipFocus = Color(0xFFF3F5F9)
    /** A rail row under focus. */
    val railFocus = Color(0xFF232A38)

    val error = Color(0xFFE5534B)
    val scrim = Color(0xB3000000)
}

internal object TvShape {
    val button = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(12.dp)
    val poster = RoundedCornerShape(10.dp)
    val panel = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(999.dp)
}

internal object TvDims {
    /** The nav rail: an icon column at rest, a labelled drawer when focus is inside it. */
    val railCollapsed: Dp = 92.dp
    val railExpanded: Dp = 268.dp

    /** Interactive content keeps clear of the collapsed rail; backdrops still bleed behind it. */
    val contentStart: Dp = railCollapsed + 24.dp

    /** Five percent overscan on a 1080p canvas, as CLAUDE.md section 13 requires. */
    val overscanX: Dp = 48.dp
    val overscanY: Dp = 27.dp

    val posterWidth: Dp = 160.dp
    val wideWidth: Dp = 300.dp
    val railGap: Dp = 16.dp

    /** The focus grow from CLAUDE.md section 12, and a firmer one for small controls. */
    const val FOCUS_SCALE = 1.08f
    const val FOCUS_SCALE_CONTROL = 1.05f
    val focusRing: Dp = 3.dp
}
