package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.LibraryAutoIcon
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.WideProgressTile
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * Libraries: the wide "chooser" landing screen for the Library tab on tablet, desktop and
 * television. A page heading, a grid of large library cards — one per library, each a place you
 * enter rather than a line in a list — and a Continue Watching rail beneath. This is a wide-only
 * screen; the compact Library tab uses its own path. See CLAUDE.md sections 12–14.
 *
 * A single [LazyVerticalGrid] is the whole-screen scroller: the heading and the Continue Watching
 * rail ride as full-width span items above and below the cards, so the page scrolls as one and the
 * grid never nests inside another vertical scroller.
 */
@Composable
fun LibrariesScreen(
    server: ActiveServer,
    libraries: List<Library>,
    continueWatching: List<MediaItem>,
    libraryPreviews: Map<String, List<MediaItem>>,
    onOpenLibrary: (Library) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val pad = sizeClass.screenPadding
    // A big chooser card is wider than a poster tile; the adaptive grid grows the column count from
    // this minimum and never stretches a card past it. A touch wider on a television.
    val cardMinWidth = if (sizeClass.isTelevision) 240.dp else 200.dp
    val wideWidth = if (sizeClass.isTelevision) 340.dp else 280.dp

    // A solid screen ground, matching the other wide screens; the scrolling grid is the Haze source
    // the frosted navigation samples on desktop. See CLAUDE.md section 12.
    Column(modifier = modifier.fillMaxSize().material(GlassRole.GROUND)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cardMinWidth),
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(
                start = pad,
                end = pad,
                top = pad,
                bottom = Spacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // The page heading: a tall amber rule beside the title in the display face, with a quiet
            // subtitle beneath, aligned under the title. Matches the wide-screen header treatment.
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader(
                    title = "Libraries",
                    subtitle = "Browse your collections",
                )
            }

            if (libraries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PlexText(
                        text = "This server has no film or series libraries.",
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(vertical = Spacing.lg),
                    )
                }
            }

            // One large card per library.
            items(libraries.size, key = { "lib-" + libraries[it].key }) { index ->
                LibraryBigCard(
                    library = libraries[index],
                    onClick = { onOpenLibrary(libraries[index]) },
                )
            }

            // Continue Watching, a rail of wide progress tiles. Hidden entirely when empty. The rail
            // is not reliably scoped to any one library, so it carries the plain label.
            if (continueWatching.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        SectionHeader(title = "Continue Watching")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            items(
                                continueWatching.size,
                                key = { "cw-" + continueWatching[it].ratingKey },
                            ) { i ->
                                val item = continueWatching[i]
                                WideProgressTile(
                                    item = item,
                                    artworkUrl = server.urls.artwork(
                                        item.artPath ?: item.thumbPath,
                                        ArtworkSize.WIDE_WIDTH,
                                        ArtworkSize.WIDE_HEIGHT,
                                    ),
                                    onClick = { onItemClick(item) },
                                    actions = itemActions(item),
                                    isContinueWatching = true,
                                    showOverflow = true,
                                    modifier = Modifier.width(wideWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The page heading: a tall amber rule beside the title in the display face, and a quiet subtitle
 * line directly beneath, aligned with the title text. Mirrors the wide-screen header treatment used
 * elsewhere in the app. See CLAUDE.md section 12.
 */
@Composable
private fun PageHeader(title: String, subtitle: String) {
    val colours = PlexTheme.colours
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .background(colours.accent, Radius.pill),
            )
            Spacer(Modifier.width(Spacing.sm))
            PlexText(text = title, style = PlexTheme.type.display, maxLines = 1)
        }
        PlexText(
            text = subtitle,
            style = PlexTheme.type.body,
            colour = colours.textSecondary,
            maxLines = 1,
            // Indent past the accent rule so the subtitle sits under the title, not the bar.
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.xxs),
        )
    }
}

/**
 * A large library card for the wide chooser: a rounded [GlassRole.CARD] surface carrying the
 * library's kind glyph in a tinted plate, the library title beneath in title weight, and the
 * accent focus ring on hover or television focus. Tapping the card opens the library.
 *
 * No item-count line is shown: the [Library] domain model carries no count, and [libraryPreviews]
 * holds only a preview slice rather than the full set, so any number drawn from it would be
 * misleading.
 */
@Composable
private fun LibraryBigCard(
    library: Library,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.card, onClick = onClick)
            // A landscape chooser tile — a place to enter, roomy enough for the glyph and title.
            .aspectRatio(4f / 3f)
            .material(GlassRole.CARD, Radius.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(Radius.card)
                .background(colours.accent.copy(alpha = if (colours.isDark) 0.20f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            LibraryAutoIcon(
                name = library.title,
                kind = library.kind,
                tint = colours.accent,
                modifier = Modifier.size(36.dp),
            )
        }
        PlexText(
            text = library.title,
            style = PlexTheme.type.title,
            colour = colours.textPrimary,
            maxLines = 1,
        )
        // TODO: real item count needs section totalSize from the API
    }
}

/**
 * A line glyph naming a library's kind: a television for a series library, a film frame for a film
 * library (and as the fallback). Drawn in the accent tint over the card's tinted plate.
 *
 * The app's existing kind-glyph draws are private to their own files, so this wide chooser keeps its
 * own copy rather than exposing theirs.
 */
@Composable
private fun LibraryKindGlyph(kind: LibraryKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.09f).coerceAtLeast(1f)
        when (kind) {
            LibraryKind.SHOW -> {
                // A television: a rounded screen over two splayed legs.
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 0.50f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = Stroke(width = stroke),
                )
                drawLine(tint, Offset(w * 0.38f, h * 0.68f), Offset(w * 0.30f, h * 0.86f), stroke)
                drawLine(tint, Offset(w * 0.62f, h * 0.68f), Offset(w * 0.70f, h * 0.86f), stroke)
            }
            else -> {
                // A film frame: a rounded rectangle with two columns of sprocket holes.
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = Size(w * 0.72f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = Stroke(width = stroke),
                )
                val hole = Size(w * 0.09f, h * 0.10f)
                for (row in 0..2) {
                    val y = h * 0.24f + row * h * 0.24f
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.19f, y),
                        size = hole,
                        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.72f, y),
                        size = hole,
                        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
                    )
                }
            }
        }
    }
}
