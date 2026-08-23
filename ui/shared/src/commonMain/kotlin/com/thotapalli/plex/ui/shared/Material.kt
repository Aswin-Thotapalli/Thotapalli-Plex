package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.liquidGlass
import com.thotapalli.plex.ui.design.material

/**
 * Apply a [GlassRole] to this surface — the single styling entry point for the whole app. The
 * engine ([PlexColours.material][com.thotapalli.plex.ui.design.material]) resolves the role to a
 * calibrated blend for the active theme; this modifier renders it:
 *
 *  - [GlassRole.CHROME] uses the platform optical-refraction glass ([liquidGlassPanel]) when a
 *    [backdrop] is supplied (nav rail/bar), else the frosted fallback;
 *  - the quieter roles use the calibrated Haze frost, plus a contrast scrim where the role needs one;
 *  - [GlassRole.GROUND] paints the translucent veil over the ambient backdrop.
 *
 * Callers never pass tint/blur/rim numbers — only a role and a [shape]. That is what keeps every
 * screen consistent while each element still gets the right material.
 */
fun Modifier.material(
    role: GlassRole,
    shape: Shape = Radius.card,
    backdrop: LiquidBackdrop? = null,
): Modifier = composed {
    val spec = PlexTheme.colours.material(role)
    when (role) {
        GlassRole.GROUND -> this.background(spec.tint)
        GlassRole.CHROME ->
            if (backdrop != null) {
                this.liquidGlassPanel(backdrop, shape, spec.tint)
            } else {
                this.liquidGlass(shape = shape, elevated = spec.elevated, tint = spec.tint)
            }
        else -> {
            val glassed = this.liquidGlass(
                shape = shape,
                elevated = spec.elevated,
                tint = spec.tint,
                rim = spec.rim,
                glow = spec.glow,
            )
            if (spec.contentScrim.isSpecified && spec.contentScrim.alpha > 0f) {
                glassed.background(spec.contentScrim, shape)
            } else {
                glassed
            }
        }
    }
}
