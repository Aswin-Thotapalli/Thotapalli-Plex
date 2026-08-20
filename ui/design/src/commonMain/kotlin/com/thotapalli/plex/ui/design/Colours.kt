package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens for the liquid-glass redesign.
 *
 * The ground is a deep indigo, never black, so a frosted panel has a cool colour to refract
 * rather than a dead flat void. Poster artwork still carries the accent colour of a screen; the
 * chrome around it is quiet glass lit by one warm amber accent.
 *
 * The [PlexColours] field names are stable — every screen and primitive reads them — and the
 * glass fields ([glassTint], [glassHighlight], [glassRim], [glassGlow]) are added on top.
 */
@Immutable
data class PlexColours(
    val background: Color,
    /**
     * The far stop of the ground's vertical gradient. The base [background] sits at the top and
     * sinks toward this a touch darker at the bottom, so a full-bleed screen reads as lit from
     * above rather than as a single flat fill. See [backgroundBrush].
     */
    val backgroundGradientEnd: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val focusRing: Color,
    val scrim: Color,
    val error: Color,
    /**
     * A lighter shade of [accent], used as the top stop of a filled control's gradient so a
     * primary button reads as a lit convex surface rather than a flat fill. Derived from the
     * branding mark's upper highlight (section 15), never a Material tint.
     */
    val accentBright: Color,
    /**
     * A deeper shade of [accent], the lower stop of that same gradient and the ground of a
     * library card's colour wash. From the branding mark's lower stop.
     */
    val accentDeep: Color,
    /**
     * The dense dark disc behind a floating control (the back button over a bright backdrop).
     * Strong enough that a bright still cannot swallow the glyph, regardless of theme.
     */
    val scrimHeavy: Color,
    /**
     * A faint light hairline traced around a raised card's edge. Poster art meets the ground with
     * no natural boundary; a one-pixel inner highlight gives the card a crisp lip and is the
     * second half of the depth cue the drop shadow begins.
     */
    val edgeHighlight: Color,
    /**
     * The colour cast into a [androidx.compose.ui.draw.shadow] under a raised tile. Poster
     * art needs to lift off the background to read as a physical card, and a shadow is the
     * only depth cue that survives against arbitrary artwork.
     */
    val elevationShadow: Color,
    /** The resting colour of a loading skeleton. */
    val skeletonBase: Color,
    /** The travelling highlight of a loading skeleton's shimmer sweep. */
    val skeletonSheen: Color,
    /**
     * The translucent fill of a glass panel: the colour the frost casts over whatever it blurs.
     * A cool indigo on dark so the pane reads as tinted glass over the ground; a bright white on
     * light. Used both as the Haze tint and as the fallback fill when no backdrop is sampled.
     */
    val glassTint: Color,
    /**
     * The bright specular that runs across the top-left of a glass panel, selling it as a lit,
     * curved sheet rather than a flat card. White, stronger on light where a pale ground needs
     * more presence to register.
     */
    val glassHighlight: Color,
    /**
     * The one-pixel inner border of a glass panel — the crisp refractive edge where the glass
     * meets the air. White, near-opaque on light, a quiet lip on dark.
     */
    val glassRim: Color,
    /**
     * The soft outer glow bleeding out from a glass panel, a cool blue halo that lifts the pane
     * off the ground and gives the whole surface its bubbly, backlit quality.
     */
    val glassGlow: Color,
    val isDark: Boolean,
)

val DarkColours = PlexColours(
    // Deep indigo, never black, sinking a shade toward a bluer black at the foot of the screen.
    background = Color(0xFF0A0C17),
    backgroundGradientEnd = Color(0xFF0E1120),
    surface = Color(0xFF161A2E),
    surfaceElevated = Color(0xFF1E2440),
    border = Color(0xFF2C3358),
    textPrimary = Color(0xFFEEF0F8),
    textSecondary = Color(0xFF9AA0BE),
    accent = Color(0xFFF5A623),
    focusRing = Color(0xFFF5A623),
    scrim = Color(0x9E0A0C17), // the indigo ground at 62 percent, so a scrim stays in the palette
    error = Color(0xFFFF6B6B),
    accentBright = Color(0xFFFFCE63), // the mark's upper highlight
    accentDeep = Color(0xFFD4820C), // the mark's lower stop
    scrimHeavy = Color(0xB3000000), // black at 70 percent, the disc behind a floating control
    edgeHighlight = Color(0x29FFFFFF), // white at 16 percent, a card's lit lip
    elevationShadow = Color(0xA8000000), // black at 66 percent, so tiles lift off the indigo ground
    skeletonBase = Color(0xFF1E2440),
    skeletonSheen = Color(0xFF2C3358),
    glassTint = Color(0x8C1C2242), // indigo at 55 percent, the frost's own colour
    glassHighlight = Color(0xB3FFFFFF), // white at 70 percent, the top specular
    glassRim = Color(0x38FFFFFF), // white at 22 percent, the refractive edge
    glassGlow = Color(0x385B7CFF), // a cool blue at 22 percent, the backlit halo
    isDark = true,
)

val LightColours = PlexColours(
    // A cool off-white, lifting toward a paler blue-grey rather than pure white.
    background = Color(0xFFEDEFF6),
    backgroundGradientEnd = Color(0xFFE3E7F2),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFD3D8E8),
    textPrimary = Color(0xFF171A2B),
    textSecondary = Color(0xFF5A6182),
    accent = Color(0xFFE08A12),
    focusRing = Color(0xFFE08A12),
    scrim = Color(0x66000000), // black at 40 percent
    error = Color(0xFFD64545),
    accentBright = Color(0xFFF5A623), // a brighter amber for the button highlight
    accentDeep = Color(0xFFB36F0C), // the deep ground of a card wash
    // A floating control always sits over artwork, so its disc stays dark on the light theme too.
    scrimHeavy = Color(0xB3000000),
    edgeHighlight = Color(0xB3FFFFFF), // white at 70 percent, a bright lip on pale glass
    elevationShadow = Color(0x33000000), // black at 20 percent, a soft grounding shadow on white
    skeletonBase = Color(0xFFE8EAED),
    skeletonSheen = Color(0xFFF4F5F7),
    glassTint = Color(0x8CFFFFFF), // white at 55 percent, the frost over a pale ground
    glassHighlight = Color(0xE6FFFFFF), // white at 90 percent, a crisp specular on light glass
    glassRim = Color(0xD9FFFFFF), // white at 85 percent, a near-solid refractive edge
    glassGlow = Color(0x245B7CFF), // a cool blue at 14 percent, a quieter halo on light
    isDark = false,
)

/**
 * The full-bleed ground brush: [background] at the top sinking to [backgroundGradientEnd] at the
 * foot, so a screen reads as lit from above. A flat [background] fill remains correct anywhere a
 * gradient is not wanted; this is the richer default for a root surface.
 */
fun PlexColours.backgroundBrush(): Brush =
    Brush.verticalGradient(listOf(background, backgroundGradientEnd))

/**
 * The player screen ignores the light theme and always renders on the dark tokens.
 * See CLAUDE.md section 12.
 */
val PlayerColours = DarkColours

internal val LocalPlexColours = staticCompositionLocalOf { DarkColours }
