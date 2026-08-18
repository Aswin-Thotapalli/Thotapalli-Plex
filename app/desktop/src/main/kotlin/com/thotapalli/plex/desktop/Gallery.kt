package com.thotapalli.plex.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.design.TokenGallery
import com.thotapalli.plex.ui.shared.ComponentGallery
import com.thotapalli.plex.ui.shared.ResponsiveGridDemo
import com.thotapalli.plex.ui.shared.WithSizeClass
import com.thotapalli.plex.ui.shared.plexFocusable
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp

/**
 * The phase 4 verification window.
 *
 * Every token, every shared component and the responsive grid, with no server behind them.
 * Resizing this window is what demonstrates the section 13 column count changing, so it is
 * a real window rather than an IDE preview.
 *
 *   gradlew :app:desktop:gallery
 */
fun main(argv: Array<String>) = application {
    // Optional arguments so each tab and size class can be opened directly and captured,
    // rather than only by clicking through the window.
    val startTab = argv.getOrNull(0)
        ?.let { name -> GalleryTab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        ?: GalleryTab.TOKENS_DARK
    val startSizeClass = argv.getOrNull(1)
        ?.let { name -> SizeClass.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Thotapalli Plex — design gallery",
        state = rememberWindowState(width = 1100.dp, height = 900.dp),
    ) {
        GalleryWindow(startTab, startSizeClass)
    }
}

private enum class GalleryTab(val label: String) {
    TOKENS_DARK("Tokens, dark"),
    TOKENS_LIGHT("Tokens, light"),
    COMPONENTS("Components"),
    GRID("Responsive grid"),
}

@Composable
private fun GalleryWindow(startTab: GalleryTab, startSizeClass: SizeClass?) {
    var tab by remember { mutableStateOf(startTab) }
    var forcedSizeClass by remember { mutableStateOf(startSizeClass) }

    val dark = tab != GalleryTab.TOKENS_LIGHT

    WithSizeClass { measured ->
        val sizeClass = forcedSizeClass ?: measured

        ThotapalliTheme(sizeClass = sizeClass, darkTheme = dark) {
            Column(Modifier.fillMaxSize().background(PlexTheme.colours.background)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PlexTheme.colours.surface)
                        .padding(Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    GalleryTab.entries.forEach { entry ->
                        Chip(entry.label, entry == tab) { tab = entry }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PlexText(
                        "Size class:",
                        style = PlexTheme.type.caption,
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Chip("Measured (${measured.name})", forcedSizeClass == null) {
                        forcedSizeClass = null
                    }
                    SizeClass.entries.forEach { entry ->
                        Chip(entry.name, forcedSizeClass == entry) { forcedSizeClass = entry }
                    }
                }

                Box(Modifier.weight(1f)) {
                    when (tab) {
                        GalleryTab.TOKENS_DARK, GalleryTab.TOKENS_LIGHT -> TokenGallery()
                        GalleryTab.COMPONENTS -> ComponentGallery()
                        GalleryTab.GRID -> ResponsiveGridDemo()
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(if (selected) colours.accent else colours.surfaceElevated, Radius.pill)
            .border(1.dp, if (selected) colours.accent else colours.border, Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.caption,
            colour = when {
                selected && colours.isDark -> colours.background
                selected -> colours.surface
                else -> colours.textSecondary
            },
        )
    }
}
