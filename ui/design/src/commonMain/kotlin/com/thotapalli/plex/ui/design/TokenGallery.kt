package com.thotapalli.plex.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

/**
 * Every token in CLAUDE.md section 12, rendered.
 *
 * This is the phase 4 step 1 output. It is a real composable rather than a comment so the
 * tokens can be looked at in both schemes side by side, which is the only way to catch a
 * value that reads wrong rather than one that is merely typed wrong.
 */
@Composable
fun TokenGallery(modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    val type = PlexTheme.type

    LazyColumn(
        modifier = modifier
            .background(colours.background)
            .fillMaxWidth()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            PlexText(
                text = if (colours.isDark) "Dark" else "Light",
                style = type.display,
            )
        }

        item {
            GallerySection("Colour") {
                Swatch("background", colours.background, colours)
                Swatch("surface", colours.surface, colours)
                Swatch("surfaceElevated", colours.surfaceElevated, colours)
                Swatch("border", colours.border, colours)
                Swatch("textPrimary", colours.textPrimary, colours)
                Swatch("textSecondary", colours.textSecondary, colours)
                Swatch("accent", colours.accent, colours)
                Swatch("focusRing", colours.focusRing, colours)
                Swatch("scrim", colours.scrim, colours)
                Swatch("error", colours.error, colours)
                Swatch("elevationShadow", colours.elevationShadow, colours)
                Swatch("skeletonBase", colours.skeletonBase, colours)
                Swatch("skeletonSheen", colours.skeletonSheen, colours)
            }
        }

        item {
            GallerySection("Type") {
                PlexText("display  28 / 40 semibold", style = type.display)
                PlexText("title  20 / 28 semibold", style = type.title)
                PlexText("body  16 / 22 regular", style = type.body)
                PlexText("label  14 / 18 medium", style = type.label)
                PlexText(
                    "caption  12 / 16 regular",
                    style = type.caption,
                    colour = colours.textSecondary,
                )
            }
        }

        item {
            GallerySection("Spacing") {
                SpacingBar("4", Spacing.xxs, colours)
                SpacingBar("8", Spacing.xs, colours)
                SpacingBar("12", Spacing.sm, colours)
                SpacingBar("16", Spacing.md, colours)
                SpacingBar("24", Spacing.lg, colours)
                SpacingBar("32", Spacing.xl, colours)
                SpacingBar("48", Spacing.xxl, colours)
            }
        }

        item {
            GallerySection("Radius") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    RadiusBox("8 poster", Radius.poster, colours)
                    RadiusBox("12 card", Radius.card, colours)
                    RadiusBox("16 sheet", Radius.sheet, colours)
                    RadiusBox("999 pill", Radius.pill, colours)
                }
            }
        }

        item {
            GallerySection("Focus ring") {
                Box(
                    modifier = Modifier
                        .size(120.dp, 80.dp)
                        .border(Layout.focusRingWidth, colours.focusRing, Radius.poster)
                        .background(colours.surfaceElevated, Radius.poster),
                    contentAlignment = Alignment.Center,
                ) {
                    PlexText("3dp accent", style = type.caption)
                }
            }
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    val colours = PlexTheme.colours
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlexText(title, style = PlexTheme.type.title, colour = colours.textSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colours.surface, Radius.card)
                .border(1.dp, colours.border, Radius.card)
                .padding(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content() }
        }
    }
}

@Composable
private fun Swatch(name: String, value: Color, colours: PlexColours) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp, 24.dp)
                .background(value, Radius.poster)
                .border(1.dp, colours.border, Radius.poster),
        )
        Spacer(Modifier.width(Spacing.sm))
        PlexText(name, style = PlexTheme.type.label)
    }
}

@Composable
private fun SpacingBar(name: String, size: Dp, colours: PlexColours) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(size).height(16.dp).background(colours.accent))
        Spacer(Modifier.width(Spacing.sm))
        PlexText("$name dp", style = PlexTheme.type.caption, colour = colours.textSecondary)
    }
}

@Composable
private fun RadiusBox(label: String, shape: RoundedCornerShape, colours: PlexColours) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp, 44.dp)
                .background(colours.surfaceElevated, shape)
                .border(1.dp, colours.border, shape),
        )
        Spacer(Modifier.height(Spacing.xxs))
        PlexText(label, style = PlexTheme.type.caption, colour = colours.textSecondary)
    }
}

// --- previews ---------------------------------------------------------------------------

@Preview
@Composable
fun TokenGalleryDarkPreview() {
    ThotapalliTheme(darkTheme = true) { TokenGallery() }
}

@Preview
@Composable
fun TokenGalleryLightPreview() {
    ThotapalliTheme(darkTheme = false) { TokenGallery() }
}

@Preview
@Composable
fun TokenGalleryTelevisionPreview() {
    ThotapalliTheme(sizeClass = SizeClass.TELEVISION, darkTheme = true) { TokenGallery() }
}
