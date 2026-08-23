package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The material-role theme engine.
 *
 * Every surface declares a [GlassRole] rather than raw numbers, which is what keeps the look
 * consistent while each element gets the right treatment. The reference design is a clean, modern
 * app: solid dark-navy / white surfaces lifted by a hairline border and a soft shadow — not a
 * translucent glass world. So each role resolves to a [MaterialSpec] of an (opaque) fill, an
 * optional border, an optional drop shadow, and — only for a control that floats over dark art —
 * a translucent fill instead.
 */
enum class GlassRole {
    /** The navigation rail / bar — a solid side panel. */
    CHROME,

    /** A content surface: cards, rows, panels, the hero card. Solid + border + soft shadow. */
    CARD,

    /** A secondary action (e.g. Details over the hero) — a translucent dark pill with a light rim,
     *  legible over any still. */
    SECONDARY,

    /** A modal sheet or dialog — a solid, elevated surface. */
    SHEET,

    /** A small pill: chips, filters, segments. */
    CHIP,

    /** A screen's base background. */
    GROUND,
}

/** The resolved look for a role in the current theme. */
@Immutable
data class MaterialSpec(
    /** The surface fill (opaque for solid roles; translucent only for [GlassRole.SECONDARY]). */
    val fill: Color,
    /** A hairline border, or [Color.Transparent] for none. */
    val border: Color,
    /** Drop-shadow elevation, or 0.dp for none. */
    val shadow: Dp,
    /** An extra scrim behind content for a translucent role; usually [Color.Transparent]. */
    val contentScrim: Color,
)

/** Resolve the [MaterialSpec] for [role] in this palette. The single source of truth for the look. */
fun PlexColours.material(role: GlassRole): MaterialSpec = when (role) {
    GlassRole.CHROME -> MaterialSpec(
        fill = surface,
        border = border,
        shadow = 0.dp,
        contentScrim = Color.Transparent,
    )
    GlassRole.CARD -> MaterialSpec(
        fill = surface,
        border = border,
        shadow = Elevation.card,
        contentScrim = Color.Transparent,
    )
    GlassRole.SECONDARY -> MaterialSpec(
        // A translucent dark pill so a secondary action stays legible over the always-dark hero,
        // with a light rim to give it an edge. (Hero controls render on the dark palette.)
        fill = if (isDark) Color(0x59121722) else Color(0x1F000000),
        border = if (isDark) Color(0x40FFFFFF) else Color(0x33000000),
        shadow = 0.dp,
        contentScrim = Color.Transparent,
    )
    GlassRole.SHEET -> MaterialSpec(
        fill = surfaceElevated,
        border = border,
        shadow = Elevation.floating,
        contentScrim = Color.Transparent,
    )
    GlassRole.CHIP -> MaterialSpec(
        fill = surfaceElevated,
        border = border,
        shadow = 0.dp,
        contentScrim = Color.Transparent,
    )
    GlassRole.GROUND -> MaterialSpec(
        fill = background,
        border = Color.Transparent,
        shadow = 0.dp,
        contentScrim = Color.Transparent,
    )
}
