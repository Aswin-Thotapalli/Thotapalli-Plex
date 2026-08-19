package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale from CLAUDE.md section 12.
 *
 * Two scales, not one scaled by a factor: a television is read from three metres away and
 * needs its own sizes, which is why the section gives both columns explicitly.
 */
@Immutable
data class PlexTypography(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

/**
 * Phone and Windows.
 *
 * Display and title carry Bold rather than SemiBold and a slight negative tracking. A poster
 * wall wants headline weight so a screen title anchors the page instead of floating; the
 * -1 percent tracking (section 15) draws the letters together into a mark rather than a row
 * of characters. Body and below keep their reading weight untouched.
 */
val CompactTypography = PlexTypography(
    display = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    title = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.005.em),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

/** Television. */
val TelevisionTypography = PlexTypography(
    display = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    title = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    body = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.005.em),
    caption = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
)

/**
 * The scale for a size class, with the bundled faces attached.
 *
 * The base scales above hold the sizes, weights and tracking from section 12; this attaches the
 * typefaces from [Fonts]. It is composable because the resource fonts load through a composable
 * accessor, and it is only ever read from inside [ThotapalliTheme]. Display and title take Sora,
 * the display face; body, label and caption take Inter, the reading face.
 */
@Composable
fun typographyFor(sizeClass: SizeClass): PlexTypography {
    val base = if (sizeClass.isTelevision) TelevisionTypography else CompactTypography
    return base.withFaces(display = soraFamily(), text = interFamily())
}

private fun PlexTypography.withFaces(display: FontFamily, text: FontFamily): PlexTypography =
    PlexTypography(
        display = this.display.copy(fontFamily = display),
        title = this.title.copy(fontFamily = display),
        body = this.body.copy(fontFamily = text),
        label = this.label.copy(fontFamily = text),
        caption = this.caption.copy(fontFamily = text),
    )

internal val LocalPlexTypography = staticCompositionLocalOf { CompactTypography }
