package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.core.model.watched
import com.thotapalli.plex.ui.design.Elevation
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing

/** Poster tiles round to 12dp in the refreshed look — a hair softer than the shared 10dp token. */
private val PosterShape = RoundedCornerShape(12.dp)

/**
 * A poster tile: 2:3 artwork rounded to 12dp with a soft drop shadow. By default a title and a
 * secondary line sit beneath it (the Library grid and Search rows use this), and a watched pill
 * marks finished titles. Passing [showCaption] `false` gives the art-only variant the Home rails
 * use, where the title lives in the item's own detail rather than under every tile. Passing
 * [showOverflow] `true` lays a small ⋮ control over the bottom-right of the art (the compact
 * Library grid uses this); it opens the same action menu the long-press gesture does, and is off
 * by default so existing callers are unaffected. See CLAUDE.md section 12.
 */
@Composable
fun PosterTile(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ItemActions? = null,
    isContinueWatching: Boolean = false,
    showCaption: Boolean = true,
    showOverflow: Boolean = false,
) {
    val colours = PlexTheme.colours

    ItemMenuHost(
        item = item,
        actions = actions,
        isContinueWatching = isContinueWatching,
        modifier = modifier,
    ) { openMenu ->
    Column(
        modifier = Modifier
            .plexFocusable(
                shape = PosterShape,
                onClick = onClick,
                // Press-and-hold opens the action menu on touch (§5). Only when actions exist.
                onLongClick = if (actions != null) openMenu else null,
            )
            .padding(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Layout.POSTER_ASPECT_RATIO)
                // A soft drop shadow lifts the poster off the ground so the wall of tiles reads
                // as physical cards rather than a flat collage.
                .shadow(
                    elevation = Elevation.tile,
                    shape = PosterShape,
                    ambientColor = colours.elevationShadow,
                    spotColor = colours.elevationShadow,
                )
                .clip(PosterShape),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = item.title,
                fallbackTitle = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            // A quiet bottom gradient so a badge or a progress bar keeps contrast over a
            // bright poster without dimming the art itself.
            PosterFooterScrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())

            if (item.watched) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
            }

            if (item.progress > 0f && !item.watched) {
                ProgressBar(
                    progress = item.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.xs),
                )
            }

            // An optional ⋮ over the bottom-right corner, on a scrim disc so it reads over any
            // still. Off by default; the compact Library grid turns it on.
            if (showOverflow && actions != null) {
                TileOverflowButton(
                    item = item,
                    actions = actions,
                    isContinueWatching = isContinueWatching,
                    onArt = true,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xxs),
                )
            }

            // A hairline lit lip around the card, drawn last so it sits above the artwork and
            // gives the poster a crisp edge against the ground.
            Box(Modifier.fillMaxSize().border(1.dp, colours.border, PosterShape))
        }

        if (showCaption) {
            Spacer(Modifier.height(Spacing.xs))

            // The title always occupies two lines and the subtitle always one, whether or not the
            // text fills them. Every tile's caption block is therefore the same height, so a wrapped
            // two-line title can never shove the row beneath it out of alignment.
            PlexText(text = item.title, style = PlexTheme.type.label, minLines = 2, maxLines = 2)
            PlexText(
                text = secondaryLine(item) ?: " ",
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                minLines = 1,
                maxLines = 1,
            )
        }
    }
    }
}

/**
 * A collection poster, with the stacked treatment that distinguishes it from a single
 * title at a glance. See CLAUDE.md section 14.
 */
@Composable
fun CollectionTile(
    collection: MediaCollection,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = modifier
            .plexFocusable(shape = Radius.poster, onClick = onClick)
            .padding(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Layout.POSTER_ASPECT_RATIO),
        ) {
            // Two offset plates behind the artwork read as a stack of posters.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = Spacing.xs, top = 0.dp, end = 0.dp, bottom = Spacing.xs)
                    .offset(x = Spacing.xs, y = (-6).dp)
                    .background(colours.surfaceElevated, Radius.poster)
                    .border(1.dp, colours.border, Radius.poster),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = Spacing.xxs, bottom = Spacing.xxs)
                    .offset(x = Spacing.xxs, y = (-3).dp)
                    .background(colours.surface, Radius.poster)
                    .border(1.dp, colours.border, Radius.poster),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(end = Spacing.xs, top = Spacing.xxs)
                    .clip(Radius.poster),
            ) {
                Artwork(
                    url = artworkUrl,
                    contentDescription = collection.title,
                    fallbackTitle = collection.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().border(1.dp, colours.glassRim, Radius.poster))
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        // Same fixed two-line title, one-line subtitle as a poster tile, so a collection sitting
        // in the same grid lines its caption block up with the titles around it.
        PlexText(text = collection.title, style = PlexTheme.type.label, minLines = 2, maxLines = 2)
        PlexText(
            text = "${collection.childCount} titles",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
            minLines = 1,
            maxLines = 1,
        )
    }
}

/**
 * The wide progress tile used by the Continue Watching rail: 16:9 artwork with a dark "Xm left"
 * badge in the top-right corner and a thin amber resume bar along the bottom edge of the art, then
 * the title and a compact subtitle ("Movie" or "S2 · E7") beneath the card. Passing [showOverflow]
 * `true` adds a trailing ⋮ beside the title that opens the item's action menu (the compact Home
 * rail uses this); it is off by default so existing callers are unaffected. See CLAUDE.md
 * section 14.
 */
@Composable
fun WideProgressTile(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ItemActions? = null,
    isContinueWatching: Boolean = false,
    showOverflow: Boolean = false,
) {
    val colours = PlexTheme.colours

    ItemMenuHost(
        item = item,
        actions = actions,
        isContinueWatching = isContinueWatching,
        modifier = modifier,
    ) { openMenu ->
    Column(
        modifier = Modifier
            .plexFocusable(
                shape = Radius.card,
                onClick = onClick,
                onLongClick = if (actions != null) openMenu else null,
            )
            .padding(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Layout.WIDE_ASPECT_RATIO)
                .shadow(
                    elevation = Elevation.wide,
                    shape = Radius.card,
                    ambientColor = colours.elevationShadow,
                    spotColor = colours.elevationShadow,
                )
                .clip(Radius.card),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = item.title,
                fallbackTitle = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            // A dark pill in the top-right corner naming how much is left, the way a streaming
            // rail badges an in-progress tile. Always dark tones, since its text is always light.
            RemainingBadge(
                item = item,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
            )

            // The resume bar rides the very bottom edge of the art, full width, no side inset,
            // so it reads as a scrubber the title picks up from.
            if (item.progress > 0f) {
                ProgressBar(
                    progress = item.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }

            Box(Modifier.fillMaxSize().border(1.dp, colours.border, Radius.card))
        }

        Spacer(Modifier.height(Spacing.xs))
        // Title and subtitle stack on the left; when asked, a trailing ⋮ rides beside them so the
        // action menu is reachable with a plain tap, not only a long-press.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                PlexText(
                    text = primaryLine(item),
                    style = PlexTheme.type.label,
                    colour = colours.textPrimary,
                    maxLines = 1,
                )
                PlexText(
                    text = wideSubtitle(item),
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 1,
                )
            }
            if (showOverflow && actions != null) {
                TileOverflowButton(
                    item = item,
                    actions = actions,
                    isContinueWatching = isContinueWatching,
                    onArt = false,
                )
            }
        }
    }
    }
}

/** A compact dark pill reading "Xm left", for the corner of a continue-watching tile. */
@Composable
internal fun RemainingBadge(item: MediaItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xCC000000), Radius.pill)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    ) {
        PlexText(
            text = remainingLabel(item),
            style = PlexTheme.type.caption,
            colour = Color(0xFFF2F3F5),
            maxLines = 1,
        )
    }
}

/** The line under a continue-watching tile: "Movie", "Series", or an episode's "S2 · E7". */
private fun wideSubtitle(item: MediaItem): String = when (item) {
    is Episode -> "S${item.seasonIndex} · E${item.episodeIndex}"
    is Movie -> "Movie"
    is Show -> "Series"
    else -> secondaryLine(item) ?: ""
}

/** A rounded square for a library chip's leading glyph, softer than a poster corner. */
private val ChipMarkShape = RoundedCornerShape(12.dp)

/**
 * A compact library chip-card for the mobile Home "Libraries" rail: a small solid [GlassRole.CARD]
 * about 132dp wide carrying a tinted rounded square with a per-kind glyph, the library name on one
 * line, and an optional caption ([caption], e.g. a count or the library kind). Tapping the whole
 * chip opens the library. See CLAUDE.md section 14.
 */
@Composable
fun LibraryChipCard(
    title: String,
    kind: LibraryKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colours = PlexTheme.colours
    Column(
        modifier = modifier
            .width(132.dp)
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .material(GlassRole.CARD, Radius.card)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(ChipMarkShape)
                .background(colours.accent.copy(alpha = if (colours.isDark) 0.20f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            LibraryAutoIcon(name = title, kind = kind, tint = colours.accent, modifier = Modifier.size(22.dp))
        }
        PlexText(
            text = title,
            style = PlexTheme.type.label,
            colour = colours.textPrimary,
            maxLines = 1,
        )
        if (!caption.isNullOrBlank()) {
            PlexText(
                text = caption,
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * A ⋮ overflow control that opens an item's action menu on a plain tap. The tiles are already
 * wrapped in [ItemMenuHost] for the long-press / right-click gesture; this is the explicit,
 * discoverable affordance the mobile mockups place on a tile. [onArt] `true` draws the dots white
 * on a scrim disc so they read over artwork; `false` draws them quietly beside a caption.
 *
 * The menu mirrors [ItemMenuHost]'s: a watched toggle, an optional remove-from-Continue-Watching
 * row, Download and Refresh, then a Delete that always confirms before it removes anything.
 */
@Composable
internal fun TileOverflowButton(
    item: MediaItem,
    actions: ItemActions,
    isContinueWatching: Boolean = false,
    onArt: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dotColour = if (onArt) Color.White else colours.textSecondary

    Box(modifier) {
        Box(
            modifier = Modifier
                .plexFocusable(shape = Radius.pill, onClick = { expanded = true }, scaleOnFocus = false)
                .size(32.dp)
                .then(
                    if (onArt) {
                        Modifier
                            .background(colours.scrimHeavy, Radius.pill)
                            .border(1.dp, colours.glassRim, Radius.pill)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(if (onArt) 16.dp else 18.dp)) {
                val r = size.minDimension * 0.11f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val gap = size.height * 0.30f
                drawCircle(dotColour, r, Offset(cx, cy - gap))
                drawCircle(dotColour, r, Offset(cx, cy))
                drawCircle(dotColour, r, Offset(cx, cy + gap))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.material(GlassRole.SHEET, Radius.card),
        ) {
            if (item.watched) {
                OverflowRow("Mark as Unwatched") { expanded = false; actions.onMarkUnwatched() }
            } else {
                OverflowRow("Mark as Watched") { expanded = false; actions.onMarkWatched() }
            }
            if (isContinueWatching && actions.onRemoveFromContinueWatching != null) {
                val remove = actions.onRemoveFromContinueWatching
                OverflowRow("Remove from Continue Watching") { expanded = false; remove() }
            }
            OverflowRow("Download") { expanded = false; actions.onDownload() }
            OverflowRow("Refresh Metadata") { expanded = false; actions.onRefreshMetadata() }
            HorizontalDivider(
                color = colours.border,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            )
            OverflowRow("Delete", tone = colours.error) { expanded = false; confirmDelete = true }
        }
    }

    if (confirmDelete) {
        TileDeleteConfirm(
            title = item.title,
            onCancel = { confirmDelete = false },
            onConfirm = { confirmDelete = false; actions.onDelete() },
        )
    }
}

@Composable
private fun OverflowRow(label: String, tone: Color? = null, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    DropdownMenuItem(
        text = {
            PlexText(
                text = label,
                style = PlexTheme.type.label,
                colour = tone ?: colours.textPrimary,
                maxLines = 1,
            )
        },
        onClick = onClick,
    )
}

/** The delete confirmation the tile ⋮ shows before removing media, matching the item menu's. */
@Composable
private fun TileDeleteConfirm(title: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colours = PlexTheme.colours
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.material(GlassRole.SHEET, Radius.card),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = colours.textPrimary,
        textContentColor = colours.textSecondary,
        shape = Radius.card,
        title = { PlexText(text = "Delete “$title”?", style = PlexTheme.type.title) },
        text = {
            PlexText(
                text = "This permanently removes the media from the server.",
                colour = colours.textSecondary,
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .plexFocusable(shape = Radius.pill, onClick = onConfirm)
                    .clip(Radius.pill)
                    .background(colours.error, Radius.pill)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                PlexText(text = "Delete", style = PlexTheme.type.label, colour = Color.White, maxLines = 1)
            }
        },
        dismissButton = { SecondaryButton(label = "Cancel", onClick = onCancel) },
    )
}

/**
 * A library card on the Home screen: one per library, tall enough to read as a place you enter
 * rather than a line in a list. A colour wash derived from the accent runs across a quiet
 * surface, a bold mark sits at the left, the title carries title weight, and a chevron on the
 * right names the card as a way in. Focus and hover raise the accent ring like everything else.
 */
@Composable
fun LibraryCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Box(
        modifier = modifier
            .plexFocusable(shape = Radius.card, onClick = onClick, scaleOnFocus = false)
            .height(Layout.libraryCardHeight)
            .shadow(
                elevation = Elevation.card,
                shape = Radius.card,
                ambientColor = colours.elevationShadow,
                spotColor = colours.elevationShadow,
            )
            .clip(Radius.card)
            // A wash that carries the accent across the card without ever reaching a solid fill,
            // so the interface stays quiet the way section 12 asks while the card still feels warm
            // and deliberate rather than empty.
            .background(
                Brush.linearGradient(
                    0f to colours.surfaceElevated,
                    0.55f to colours.surface,
                    1f to colours.accentDeep.copy(alpha = if (colours.isDark) 0.28f else 0.16f),
                ),
            )
            .border(1.dp, colours.glassRim, Radius.card),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryCardMark(title = title, artworkUrl = artworkUrl)

            Spacer(Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                PlexText(text = title, style = PlexTheme.type.title, maxLines = 1)
                PlexText(
                    text = subtitle,
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            // The back chevron mirrored to point inward, so the card reads as an entrance.
            PlexIcon(
                kind = PlexIconKind.BACK,
                size = 22.dp,
                tint = colours.textSecondary,
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

/**
 * The bold mark on a library card. Libraries carry no poster of their own, so the common case is
 * a rounded plate washed with the accent gradient and stamped with the library's initial — an
 * app-icon-like token rather than an empty poster slot. Real artwork, on the rare card handed
 * some, fills the same rounded square.
 */
@Composable
private fun LibraryCardMark(
    title: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val onAccent = if (colours.isDark) colours.background else Color.White
    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(
                elevation = Elevation.card,
                shape = Radius.card,
                ambientColor = colours.elevationShadow,
                spotColor = colours.elevationShadow,
            )
            .clip(Radius.card),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            Artwork(
                url = artworkUrl,
                contentDescription = null,
                fallbackTitle = title,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0f to colours.accentBright,
                            1f to colours.accentDeep,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PlexText(
                    text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = PlexTheme.type.display,
                    colour = onAccent,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxSize().border(1.dp, colours.glassRim, Radius.card))
    }
}

/**
 * An episode row: thumbnail, number, title, duration and a summary, with progress shown when the
 * episode is part watched.
 *
 * The row has two distinct targets. A circular play control laid over the thumbnail calls
 * [onPlay] and starts the episode; tapping anywhere else on the row calls [onSelect], which the
 * show detail screen uses to reveal the episode without committing to playback. When [selected]
 * is true the row raises onto an elevated surface and grows a left accent bar, so the chosen
 * episode is unmistakable in a long list.
 *
 * The older single-tap contract is preserved for callers that have not moved across: passing
 * [onClick] routes both play and select to it, reproducing the previous "tap anywhere plays"
 * behaviour, while new callers use [onPlay] and [onSelect] and leave [onClick] null.
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    thumbnailUrl: String?,
    onPlay: () -> Unit = {},
    onSelect: () -> Unit = {},
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actions: ItemActions? = null,
) {
    val colours = PlexTheme.colours
    val play = onClick ?: onPlay
    val select = onClick ?: onSelect

    ItemMenuHost(item = episode, actions = actions, modifier = modifier) { openMenu ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(
                shape = Radius.card,
                onClick = select,
                scaleOnFocus = false,
                onLongClick = if (actions != null) openMenu else null,
            )
            .clip(Radius.card)
            .background(if (selected) colours.surfaceElevated else Color.Transparent)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A left accent bar names the selected row. It always occupies its width so the thumbnail
        // and text keep the same position whether or not the row is selected.
        Box(
            Modifier
                .width(3.dp)
                .height(48.dp)
                .background(
                    if (selected) colours.accent else Color.Transparent,
                    Radius.pill,
                ),
        )
        Spacer(Modifier.width(Spacing.xs))

        Box(
            Modifier
                .width(142.dp)
                .aspectRatio(Layout.WIDE_ASPECT_RATIO)
                .shadow(
                    elevation = Elevation.card,
                    shape = Radius.poster,
                    ambientColor = colours.elevationShadow,
                    spotColor = colours.elevationShadow,
                )
                .clip(Radius.poster),
        ) {
            Artwork(
                url = thumbnailUrl,
                contentDescription = episode.title,
                fallbackTitle = episode.title,
                modifier = Modifier.fillMaxSize(),
            )

            PosterFooterScrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())

            // A real, separately focusable play control. Its own click consumes the tap, so
            // pressing it plays the episode while a tap anywhere else on the row selects it.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .plexFocusable(shape = Radius.pill, onClick = play)
                    .size(Layout.playToken * 0.82f)
                    .background(colours.scrimHeavy, Radius.pill)
                    .border(1.dp, colours.glassRim, Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                // The triangle sits a hair right of centre so it reads as balanced in the disc.
                PlexIcon(
                    PlexIconKind.PLAY,
                    size = 18.dp,
                    tint = Color.White,
                    modifier = Modifier.offset(x = 1.dp),
                )
            }

            if (episode.progress > 0f && !episode.watched) {
                ProgressBar(
                    progress = episode.progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.xxs),
                )
            }

            Box(Modifier.fillMaxSize().border(1.dp, colours.glassRim, Radius.poster))
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlexText(
                    text = pad(episode.episodeIndex),
                    style = PlexTheme.type.label,
                    colour = colours.accent,
                )
                Spacer(Modifier.width(Spacing.sm))
                PlexText(
                    text = episode.title,
                    style = PlexTheme.type.label,
                    colour = colours.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            PlexText(
                text = formatDuration(episode.durationMs),
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                maxLines = 1,
            )

            if (episode.summary.isNotBlank()) {
                PlexText(
                    text = episode.summary,
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 2,
                )
            }
        }

        if (episode.watched) {
            Spacer(Modifier.width(Spacing.xs))
            WatchedBadge()
        }
    }
    }
}

/**
 * A section header: a bold title on the left and, optionally, a "View all ›" text button on the
 * right. Pass [onViewAll] for the common rail case, or a custom [trailing] slot when a header needs
 * something else on its right. See CLAUDE.md section 14.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PlexText(text = title, style = PlexTheme.type.title, maxLines = 1)
        when {
            trailing != null -> trailing()
            onViewAll != null -> ViewAllButton(onClick = onViewAll)
        }
    }
}

/** The "View all ›" affordance that opens a rail's full library. Focusable like every control. */
@Composable
fun ViewAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    ) {
        PlexText(
            text = "View all ›",
            style = PlexTheme.type.label,
            colour = PlexTheme.colours.accent,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    // A rounded track with a rounded accent fill. Slightly taller than a hairline so it
    // registers as a deliberate element, clipped to a pill so the ends read as caps.
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(Radius.pill)
            .background(Color(0x59FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(Radius.pill)
                .background(colours.accent),
        )
    }
}

/**
 * The faint dark wash along the bottom of a piece of artwork. Just enough to hold a badge,
 * a progress bar, or a number against a bright still without touching the art above it.
 */
@Composable
internal fun PosterFooterScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight(0.4f)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0x99000000),
                ),
            ),
    )
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .background(colours.accent, Radius.pill)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    ) {
        PlexText(
            text = "Watched",
            style = PlexTheme.type.caption,
            colour = if (colours.isDark) colours.background else Color.White,
        )
    }
}

// --- labels ------------------------------------------------------------------------------

internal fun primaryLine(item: MediaItem): String = when (item) {
    is Episode -> item.showTitle.ifBlank { item.title }
    else -> item.title
}

internal fun secondaryLine(item: MediaItem): String? = when (item) {
    is Episode -> "S${pad(item.seasonIndex)}E${pad(item.episodeIndex)}  ${item.title}"
    is Movie -> item.year?.toString()
    is Show -> if (item.leafCount > 0) "${item.leafCount} episodes" else null
    is MediaCollection -> "${item.childCount} titles"
    else -> null
}

internal fun remainingLabel(item: MediaItem): String {
    val remaining = (item.durationMs - item.viewOffsetMs).coerceAtLeast(0)
    return if (item.viewOffsetMs <= 0L) formatDuration(item.durationMs)
    else "${formatDuration(remaining)} left"
}

internal fun pad(value: Int): String = value.toString().padStart(2, '0')

/** Durations read as "1 h 47 m" or "42 m", never as a raw millisecond count. */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/** A clock position for the player: 1:47:12 or 42:07. */
internal fun formatPosition(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${pad(minutes.toInt())}:${pad(seconds.toInt())}"
    else "$minutes:${pad(seconds.toInt())}"
}
