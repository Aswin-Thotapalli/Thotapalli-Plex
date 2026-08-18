package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

/** Phone and Windows. */
val CompactTypography = PlexTypography(
    display = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

/** Television. */
val TelevisionTypography = PlexTypography(
    display = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
    title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    caption = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
)

fun typographyFor(sizeClass: SizeClass): PlexTypography =
    if (sizeClass.isTelevision) TelevisionTypography else CompactTypography

internal val LocalPlexTypography = staticCompositionLocalOf { CompactTypography }
