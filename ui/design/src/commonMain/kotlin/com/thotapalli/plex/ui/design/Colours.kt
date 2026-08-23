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
    // A deep near-black navy ground with a faint vignette toward the foot, matching the reference
    // mockups: a clean, modern dark surface rather than a heavy tinted glass world.
    background = Color(0xFF0A0D14),
    backgroundGradientEnd = Color(0xFF0C1119),
    // Cards and the navigation panel: solid, a touch above the ground, lifted by a border + shadow.
    surface = Color(0xFF141925),
    surfaceElevated = Color(0xFF1B2230),
    border = Color(0xFF242C3B),
    textPrimary = Color(0xFFF3F5F9),
    textSecondary = Color(0xFF98A2B3),
    accent = Color(0xFFF5A623),
    focusRing = Color(0xFFF5A623),
    scrim = Color(0xA60A0D14),
    error = Color(0xFFFF6B6B),
    accentBright = Color(0xFFFFC46B),
    accentDeep = Color(0xFFD4820C),
    scrimHeavy = Color(0xB3000000),
    edgeHighlight = Color(0x1FFFFFFF), // white at 12 percent, a card's quiet top lip
    elevationShadow = Color(0x8C000000), // soft black, so a card lifts off the navy ground
    skeletonBase = Color(0xFF1B2230),
    skeletonSheen = Color(0xFF283040),
    // The glass fields remain for the player overlay (which floats over motion video); the main
    // chrome no longer uses them — surfaces are solid now, matching the mockups.
    glassTint = Color(0xCC141925), // near-solid surface, a quiet frost for the player only
    glassHighlight = Color(0x40FFFFFF),
    glassRim = Color(0x24FFFFFF),
    glassGlow = Color(0x2E5B7CFF),
    isDark = true,
)

val LightColours = PlexColours(
    // A cool blue-grey ground, deep enough that a white card reads as a lifted panel rather than
    // dissolving into a flat near-white void. The gradient sinks a further shade at the foot.
    // A soft off-white ground; white cards lift off it with a light border + soft shadow.
    background = Color(0xFFF5F7FB),
    backgroundGradientEnd = Color(0xFFEDF1F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFE4E9F1),
    textPrimary = Color(0xFF1B2130),
    textSecondary = Color(0xFF616B7D),
    accent = Color(0xFFF5911E),
    focusRing = Color(0xFFF5911E),
    scrim = Color(0x66000000),
    error = Color(0xFFD64545),
    accentBright = Color(0xFFFBA94A),
    accentDeep = Color(0xFFC2740E),
    scrimHeavy = Color(0xB3000000),
    edgeHighlight = Color(0x99FFFFFF),
    elevationShadow = Color(0x1A1B2740), // a soft cool shadow grounding white cards on the pale ground
    skeletonBase = Color(0xFFE7EBF2),
    skeletonSheen = Color(0xFFF3F5F9),
    // A near-solid white frost: on a pale ground a translucent pane vanishes, so light glass reads
    // as a crisp white panel with a faint refracted tint rather than a washed-out ghost.
    glassTint = Color(0x73FFFFFF), // white at ~45 percent — translucent frost, real see-through glass
    glassHighlight = Color(0xE6FFFFFF), // white at 90 percent, a crisp specular on light glass
    glassRim = Color(0x66FFFFFF), // white at 40 percent, a soft lip that no longer disappears on white
    glassGlow = Color(0x33243A66), // a cool shadow-tinted halo so light panels cast real depth
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
