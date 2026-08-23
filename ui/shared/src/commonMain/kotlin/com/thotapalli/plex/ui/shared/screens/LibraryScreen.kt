package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CollectionTile
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.LibraryState
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PosterGrid
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.SkeletonPosterGrid
import com.thotapalli.plex.ui.shared.TopBarIconButton
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.plexFocusable
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance

/**
 * Library: a poster grid sorted alphabetically. Collections first with a stacked poster
 * treatment, then individual titles. One filter, "Unwatched only". The sort is fixed and
 * has no control. See CLAUDE.md section 14.
 *
 * The screen is a clean, modern surface: a solid ground, a header carrying a circular back
 * control and the library title in the display weight, and a wall of poster tiles. The two
 * library actions — Scan and the Unwatched-only filter — sit as pills on the right of the
 * header, the filter turning solid amber when it is on. See CLAUDE.md section 12.
 *
 * [onBack] is the up action for the library root (back to Home). It is optional so the shell can
 * wire it without breaking the existing call site; inside an open collection the header back
 * control returns to the library through [onCloseCollection] instead.
 */
@Composable
fun LibraryScreen(
    server: ActiveServer,
    state: LibraryState,
    onItemClick: (MediaItem) -> Unit,
    onCollectionClick: (MediaCollection) -> Unit,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    onCloseCollection: () -> Unit,
    onScanLibrary: (Library) -> Unit,
    itemActions: (MediaItem) -> ItemActions,
    onBack: (() -> Unit)? = null,
    scanProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val insideCollection = state.openCollection != null
    // On a television the first tile in the grid takes focus on entry, so the remote lands
    // somewhere. Collections are emitted first, so the index-0 collection is the true first
    // tile; where no collections show, the index-0 poster is. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = PlexTheme.sizeClass.isTelevision)
    val collectionsShown = !insideCollection && state.collections.isNotEmpty()

    // The header's up control returns to the library while a collection is open, and up to Home
    // at the library root. The root action is optional, so a caller that has not wired it yet
    // simply leaves the control inert rather than failing to build.
    val backAction: () -> Unit = if (insideCollection) onCloseCollection else (onBack ?: {})

    val title = state.openCollection?.title ?: state.library.title
    val subtitle: String? = when {
        insideCollection -> state.library.title
        else -> state.items.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "title" else "titles"}" }
    }

    // A solid screen ground: the library reads as a clean, modern surface rather than a pane of
    // glass. See CLAUDE.md section 12.
    Column(modifier = modifier.fillMaxSize().material(GlassRole.GROUND)) {
        // The header: a circular back control, the library title beside it in the display weight,
        // and — at the library root — the Scan and Unwatched-only pills on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PlexTheme.sizeClass.screenPadding,
                    vertical = Spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TopBarIconButton(kind = PlexIconKind.BACK, onClick = backAction)
                Column {
                    PlexText(text = title, style = PlexTheme.type.display, maxLines = 1)
                    if (subtitle != null) {
                        PlexText(
                            text = subtitle,
                            style = PlexTheme.type.caption,
                            colour = PlexTheme.colours.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (!insideCollection) {
                Spacer(Modifier.width(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // A per-library scan, so the owner can pull in newly added files without
                    // leaving the app. See CLAUDE.md section 5.
                    IconChip(
                        label = "Scan",
                        glyph = ChipGlyph.SCAN,
                        selected = false,
                        onClick = { onScanLibrary(state.library) },
                    )
                    // The one filter. Solid amber with inverted content when it is on, so the
                    // active state is unmistakable and the accent marks it and nothing else.
                    IconChip(
                        label = "Unwatched only",
                        glyph = ChipGlyph.FILTER,
                        selected = state.unwatchedOnly,
                        onClick = { onUnwatchedOnlyChange(!state.unwatchedOnly) },
                    )
                }
            }
        }

        // A live scan of the library's files, driven by the server's running jobs: a slim amber
        // bar tracking the server's progress, directly under the header. See CLAUDE.md section 5.
        if (scanProgress != null && !insideCollection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlexTheme.sizeClass.screenPadding, vertical = Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                PlexText(
                    text = "Scanning…",
                    style = PlexTheme.type.caption,
                    colour = PlexTheme.colours.textSecondary,
                    maxLines = 1,
                )
                LinearProgressIndicator(
                    progress = { scanProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(Radius.pill),
                    color = PlexTheme.colours.accent,
                    trackColor = PlexTheme.colours.surface,
                )
            }
        }

        if (state.loading && state.items.isEmpty() && state.collections.isEmpty()) {
            SkeletonPosterGrid(Modifier.fillMaxSize())
            return@Column
        }

        // The scrolling grid is the source the frosted navigation samples.
        PosterGrid(modifier = Modifier.glassSource()) {
            if (!insideCollection && state.collections.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Collections", Modifier.padding(top = Spacing.xs))
                }
                items(state.collections.size, key = { state.collections[it].ratingKey }) { index ->
                    val collection = state.collections[index]
                    CollectionTile(
                        collection = collection,
                        artworkUrl = server.urls.artwork(
                            collection.thumbPath,
                            ArtworkSize.POSTER_WIDTH,
                            ArtworkSize.POSTER_HEIGHT,
                        ),
                        onClick = { onCollectionClick(collection) },
                        modifier = Modifier
                            .staggeredEntrance(index, key = collection.ratingKey)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(state.library.title, Modifier.padding(top = Spacing.md))
                }
            }

            items(state.items.size, key = { state.items[it].ratingKey }) { index ->
                val item = state.items[index]
                PosterTile(
                    item = item,
                    artworkUrl = server.urls.artwork(
                        item.thumbPath,
                        ArtworkSize.POSTER_WIDTH,
                        ArtworkSize.POSTER_HEIGHT,
                    ),
                    onClick = { onItemClick(item) },
                    actions = itemActions(item),
                    modifier = Modifier
                        .staggeredEntrance(index, key = item.ratingKey)
                        .then(
                            if (!collectionsShown && index == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            if (state.items.isEmpty() && state.collections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PlexText(
                        text = if (state.unwatchedOnly) {
                            "Everything in this library has been watched."
                        } else {
                            "This library is empty."
                        },
                        colour = PlexTheme.colours.textSecondary,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
    }
}

/** The two library-header pills carry a small line glyph drawn to match the app's own icon set. */
private enum class ChipGlyph { SCAN, FILTER }

/**
 * A header action pill: a leading line glyph and a label on the CHIP material, turning to a solid
 * amber pill with inverted content when [selected]. The accent marks selection and nothing else.
 * See CLAUDE.md section 12.
 */
@Composable
private fun IconChip(
    label: String,
    glyph: ChipGlyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val content = when {
        selected && colours.isDark -> colours.background
        selected -> colours.surface
        else -> colours.textSecondary
    }
    Row(
        modifier = modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick)
            .clip(Radius.pill)
            .then(
                if (selected) {
                    Modifier
                        .background(colours.accent, Radius.pill)
                        .border(1.dp, colours.accent, Radius.pill)
                } else {
                    Modifier.material(GlassRole.CHIP, shape = Radius.pill)
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ChipGlyphIcon(glyph = glyph, tint = content, size = 16.dp)
        PlexText(text = label, style = PlexTheme.type.label, colour = content)
    }
}

/** Draws the small line glyph for a [ChipGlyph] on a Canvas, so it takes the chip's content tint. */
@Composable
private fun ChipGlyphIcon(glyph: ChipGlyph, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.10f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            ChipGlyph.SCAN -> drawScanGlyph(tint, stroke)
            ChipGlyph.FILTER -> drawFilterGlyph(tint, stroke)
        }
    }
}

/** A scanner viewfinder: four corner brackets with a line sweeping across, reading as "scan". */
private fun DrawScope.drawScanGlyph(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val o = 0.22f // the outer corner, in from each edge
    val a = 0.42f // how far each bracket arm reaches
    // top-left
    drawLine(tint, Offset(w * o, h * o), Offset(w * o, h * a), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * o, h * o), Offset(w * a, h * o), stroke.width, stroke.cap)
    // top-right
    drawLine(tint, Offset(w * (1 - o), h * o), Offset(w * (1 - o), h * a), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * (1 - o), h * o), Offset(w * (1 - a), h * o), stroke.width, stroke.cap)
    // bottom-left
    drawLine(tint, Offset(w * o, h * (1 - o)), Offset(w * o, h * (1 - a)), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * o, h * (1 - o)), Offset(w * a, h * (1 - o)), stroke.width, stroke.cap)
    // bottom-right
    drawLine(tint, Offset(w * (1 - o), h * (1 - o)), Offset(w * (1 - o), h * (1 - a)), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * (1 - o), h * (1 - o)), Offset(w * (1 - a), h * (1 - o)), stroke.width, stroke.cap)
    // the sweeping scan line
    drawLine(tint, Offset(w * 0.14f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), stroke.width, stroke.cap)
}

/** A funnel: the conventional filter glyph. */
private fun DrawScope.drawFilterGlyph(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val funnel = Path().apply {
        moveTo(w * 0.20f, h * 0.26f)
        lineTo(w * 0.80f, h * 0.26f)
        lineTo(w * 0.56f, h * 0.54f)
        lineTo(w * 0.56f, h * 0.78f)
        lineTo(w * 0.44f, h * 0.70f)
        lineTo(w * 0.44f, h * 0.54f)
        close()
    }
    drawPath(funnel, tint, style = stroke)
}

/** A pill. The accent marks selection and nothing else. See CLAUDE.md section 12. */
@Composable
internal fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .clip(Radius.pill)
            // Selected is a solid amber pill; unselected takes the calibrated CHIP glass, so the chip
            // reads as chrome without competing with the poster art around it. See CLAUDE.md §12.
            .then(
                if (selected) {
                    Modifier
                        .background(colours.accent, Radius.pill)
                        .border(1.dp, colours.accent, Radius.pill)
                } else {
                    Modifier.material(GlassRole.CHIP, shape = Radius.pill)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = when {
                selected && colours.isDark -> colours.background
                selected -> colours.surface
                else -> colours.textSecondary
            },
        )
    }
}
