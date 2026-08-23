package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The material-role theme engine.
 *
 * Every surface in the app declares a **role**, never raw glass numbers — that is what keeps the
 * look macro-consistent while letting each element get exactly the blend it needs. The engine maps a
 * [GlassRole] (+ the active [PlexColours]) to a calibrated [MaterialSpec], with the tint alpha of
 * every text-bearing role chosen so its label always clears a legibility floor (the Liquid Glass
 * spec's "adaptive opacity / contrast preservation"). Chrome gets true optical refraction; quieter
 * roles get a calibrated frost; primary actions stay opaque so they pop.
 *
 * The whole app floats over one ambient backdrop (the featured art). [GlassRole.GROUND] is the
 * translucent veil a screen paints over that backdrop so content stays readable while the art still
 * bleeds through — which is what makes the content area feel like the same glass world as the nav,
 * instead of an opaque panel bolted next to it.
 */
enum class GlassRole {
    /** Navigation rail / bar — the showcase glass: strong refraction, low tint, wide blur. */
    CHROME,

    /** A content surface floating on the ground: library cards, rows, metadata panels. */
    CARD,

    /** A secondary/tertiary action (e.g. Details): glass, but with a contrast floor so its label is
     *  always legible over any backdrop. */
    SECONDARY,

    /** A modal sheet or dialog: denser, elevated glass. */
    SHEET,

    /** A small pill: chips, filters, track selectors. */
    CHIP,

    /** The translucent veil a screen lays over the ambient backdrop so content reads while the art
     *  still shows through — the unifier that makes every screen one glass world. */
    GROUND,
}

/** The resolved parameters for a role in the current theme. */
@Immutable
data class MaterialSpec(
    /** The fill/frost colour, alpha pre-calibrated for this role + theme (legibility floor baked in). */
    val tint: Color,
    /** Backdrop blur radius; 0 for a role that does not blur. */
    val blurRadius: Dp,
    /** True only for [GlassRole.CHROME]: use the platform optical-refraction material. */
    val refraction: Boolean,
    val rim: Boolean,
    val glow: Boolean,
    val elevated: Boolean,
    /** An extra scrim painted behind content for a text-bearing translucent role; [Color.Transparent]
     *  when the tint alone already guarantees contrast. */
    val contentScrim: Color,
)

/** Resolve the [MaterialSpec] for [role] in this palette. The single source of truth for the look. */
fun PlexColours.material(role: GlassRole): MaterialSpec = when (role) {
    GlassRole.CHROME -> MaterialSpec(
        tint = surface.copy(alpha = if (isDark) 0.22f else 0.44f),
        blurRadius = 40.dp,
        refraction = true,
        rim = true,
        glow = true,
        elevated = true,
        contentScrim = Color.Transparent,
    )
    GlassRole.CARD -> MaterialSpec(
        // Translucent enough to read as glass over the ambient art, opaque enough to hold posters
        // and titles without the art fighting them.
        tint = surface.copy(alpha = if (isDark) 0.58f else 0.80f),
        blurRadius = 24.dp,
        refraction = false,
        rim = true,
        glow = false,
        elevated = true,
        contentScrim = Color.Transparent,
    )
    GlassRole.SECONDARY -> MaterialSpec(
        // Higher floor + a faint inner scrim so a glass action's label never washes out (the Details
        // button fix).
        tint = surface.copy(alpha = if (isDark) 0.66f else 0.82f),
        blurRadius = 18.dp,
        refraction = false,
        rim = true,
        glow = false,
        elevated = true,
        contentScrim = (if (isDark) Color.Black else Color.White).copy(alpha = 0.16f),
    )
    GlassRole.SHEET -> MaterialSpec(
        tint = surfaceElevated.copy(alpha = if (isDark) 0.90f else 0.94f),
        blurRadius = 32.dp,
        refraction = false,
        rim = true,
        glow = true,
        elevated = true,
        contentScrim = Color.Transparent,
    )
    GlassRole.CHIP -> MaterialSpec(
        tint = surface.copy(alpha = if (isDark) 0.52f else 0.74f),
        blurRadius = 14.dp,
        refraction = false,
        rim = true,
        glow = false,
        elevated = false,
        contentScrim = Color.Transparent,
    )
    GlassRole.GROUND -> MaterialSpec(
        // The veil over the ambient art. Dark, heavy enough that content reads, sheer enough that the
        // art bleeds through so the whole screen shares the nav's glass world.
        tint = background.copy(alpha = if (isDark) 0.78f else 0.86f),
        blurRadius = 0.dp,
        refraction = false,
        rim = false,
        glow = false,
        elevated = false,
        contentScrim = Color.Transparent,
    )
}
