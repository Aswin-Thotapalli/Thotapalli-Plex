package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.material

/**
 * Apply a [GlassRole] to this surface — the single styling entry point for the whole app.
 *
 * The engine ([PlexColours.material][com.thotapalli.plex.ui.design.material]) resolves the role to a
 * calibrated look for the active theme (a solid fill, a hairline border, a soft drop shadow — or a
 * translucent pill for a control over dark art); this modifier renders it. Callers pass only a role
 * and a [shape] — never tint/blur/alpha numbers — which is what keeps every screen consistent.
 *
 * [backdrop] is accepted for source-compatibility with the earlier glass API and ignored now that
 * surfaces are solid.
 */
fun Modifier.material(
    role: GlassRole,
    shape: Shape = Radius.card,
    @Suppress("UNUSED_PARAMETER") backdrop: LiquidBackdrop? = null,
): Modifier = composed {
    val colours = PlexTheme.colours
    val spec = colours.material(role)

    if (role == GlassRole.GROUND) {
        return@composed this.background(spec.fill)
    }

    var m = this
    if (spec.shadow > 0.dp) {
        m = m.shadow(
            elevation = spec.shadow,
            shape = shape,
            ambientColor = colours.elevationShadow,
            spotColor = colours.elevationShadow,
        )
    }
    m = m.clip(shape).background(spec.fill, shape)
    if (spec.border.isSpecified && spec.border.alpha > 0f) {
        m = m.border(1.dp, spec.border, shape)
    }
    if (spec.contentScrim.isSpecified && spec.contentScrim.alpha > 0f) {
        m = m.background(spec.contentScrim, shape)
    }
    m
}
