package com.thotapalli.plex.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle

/**
 * Light and dark, following the system setting. See CLAUDE.md section 2.
 *
 * Material 3 is present because the shared components sit on top of it, but the palette is
 * entirely the section 12 tokens. Nothing here uses a Material default colour.
 */
@Composable
fun ThotapalliTheme(
    sizeClass: SizeClass = LocalSizeClass.current,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** The player screen ignores the light theme entirely. See CLAUDE.md section 12. */
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colours = if (darkTheme || forceDark) DarkColours else LightColours
    val typography = typographyFor(sizeClass)

    val material = if (colours.isDark) {
        darkColorScheme(
            primary = colours.accent,
            onPrimary = colours.background,
            secondary = colours.accent,
            background = colours.background,
            onBackground = colours.textPrimary,
            surface = colours.surface,
            onSurface = colours.textPrimary,
            surfaceVariant = colours.surfaceElevated,
            onSurfaceVariant = colours.textSecondary,
            outline = colours.border,
            error = colours.error,
            scrim = colours.scrim,
        )
    } else {
        lightColorScheme(
            primary = colours.accent,
            onPrimary = colours.surface,
            secondary = colours.accent,
            background = colours.background,
            onBackground = colours.textPrimary,
            surface = colours.surface,
            onSurface = colours.textPrimary,
            surfaceVariant = colours.surfaceElevated,
            onSurfaceVariant = colours.textSecondary,
            outline = colours.border,
            error = colours.error,
            scrim = colours.scrim,
        )
    }

    CompositionLocalProvider(
        LocalPlexColours provides colours,
        LocalPlexTypography provides typography,
        LocalSizeClass provides sizeClass,
        LocalContentColor provides colours.textPrimary,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

/**
 * The section 12 tokens, read from anywhere below [ThotapalliTheme].
 *
 * Named rather than reached through MaterialTheme so a call site says which token it wants
 * instead of which Material role happens to carry it.
 */
object PlexTheme {
    val colours: PlexColours
        @Composable @ReadOnlyComposable get() = LocalPlexColours.current

    val type: PlexTypography
        @Composable @ReadOnlyComposable get() = LocalPlexTypography.current

    val sizeClass: SizeClass
        @Composable @ReadOnlyComposable get() = LocalSizeClass.current
}

/** Body text in the primary colour, which is by far the most common case. */
@Composable
fun PlexText(
    text: String,
    style: TextStyle = PlexTheme.type.body,
    colour: androidx.compose.ui.graphics.Color = PlexTheme.colours.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    /**
     * Reserve at least this many lines of height, even when the text is shorter. A poster wall
     * only stays aligned if every title block is the same height; setting [minLines] to 2 on a
     * title makes a one-line title hold the same two lines a wrapped title takes, so the row
     * beneath never shifts. Defaults to 1, which is the ordinary single-line case.
     */
    minLines: Int = 1,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    Text(
        text = text,
        style = style,
        color = colour,
        maxLines = maxLines,
        minLines = minLines,
        overflow = overflow,
        modifier = modifier,
    )
}

internal val localColoursForPreview: ProvidableCompositionLocal<PlexColours> = LocalPlexColours
