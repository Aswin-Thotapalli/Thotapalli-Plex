package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens from CLAUDE.md section 12.
 *
 * Poster artwork carries the colour. The interface stays quiet around it, and one accent
 * marks selection and focus and nothing else.
 */
@Immutable
data class PlexColours(
    val background: Color,
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
     * A faint light hairline traced around a raised card's edge. Poster art meets the near-black
     * ground with no natural boundary; a one-pixel inner highlight gives the card a crisp lip and
     * is the second half of the depth cue the drop shadow begins.
     */
    val edgeHighlight: Color,
    /**
     * The colour cast into a [androidx.compose.ui.draw.shadow] under a raised tile. Poster
     * art needs to lift off the background to read as a physical card, and a shadow is the
     * only depth cue that survives against arbitrary artwork. Stronger on dark, where the
     * plate and the ground are close in value, softer on light.
     */
    val elevationShadow: Color,
    /** The resting colour of a loading skeleton. */
    val skeletonBase: Color,
    /** The travelling highlight of a loading skeleton's shimmer sweep. */
    val skeletonSheen: Color,
    val isDark: Boolean,
)

val DarkColours = PlexColours(
    background = Color(0xFF0E0F12),
    surface = Color(0xFF16181C),
    surfaceElevated = Color(0xFF1E2126),
    border = Color(0xFF2A2E35),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFFA8AEB8),
    accent = Color(0xFFF5A623),
    focusRing = Color(0xFFF5A623),
    scrim = Color(0x99000000), // black at 60 percent
    error = Color(0xFFE5534B),
    accentBright = Color(0xFFFFC24D), // the mark's upper highlight
    accentDeep = Color(0xFFD4820C), // the mark's lower stop
    scrimHeavy = Color(0xB3000000), // black at 70 percent, the disc behind a floating control
    edgeHighlight = Color(0x1AFFFFFF), // white at ~10 percent, a card's lit lip
    elevationShadow = Color(0xCC000000), // black at 80 percent, so tiles lift off the near-black ground
    skeletonBase = Color(0xFF1E2126),
    skeletonSheen = Color(0xFF2A2E35),
    isDark = true,
)

val LightColours = PlexColours(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFE2E4E8),
    textPrimary = Color(0xFF16181C),
    textSecondary = Color(0xFF5A616B),
    accent = Color(0xFFC97C05),
    focusRing = Color(0xFFC97C05),
    scrim = Color(0x73000000), // black at 45 percent
    error = Color(0xFFC0392B),
    accentBright = Color(0xFFE0982E), // a warmer, lighter amber for the button highlight
    accentDeep = Color(0xFFA5650A), // the deep ground of a card wash
    // A floating control always sits over artwork, so its disc stays dark on the light theme too.
    scrimHeavy = Color(0xB3000000),
    edgeHighlight = Color(0x14FFFFFF),
    elevationShadow = Color(0x33000000), // black at 20 percent, a soft grounding shadow on white
    skeletonBase = Color(0xFFE8EAED),
    skeletonSheen = Color(0xFFF4F5F7),
    isDark = false,
)

/**
 * The player screen ignores the light theme and always renders on the dark tokens.
 * See CLAUDE.md section 12.
 */
val PlayerColours = DarkColours

internal val LocalPlexColours = staticCompositionLocalOf { DarkColours }
