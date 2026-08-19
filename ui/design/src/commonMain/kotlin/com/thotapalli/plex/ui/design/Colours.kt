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
