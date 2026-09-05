package com.thotapalli.plex.ui.shared.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.SearchState
import com.thotapalli.plex.ui.shared.rememberVoiceSearch

/**
 * Search from a remote: an on-screen keyboard on the left where every key is a target, the query
 * echoed above it, and the results on the right grouped as Movies, Shows and Episodes. The view
 * model already waits 300 ms of no typing and needs two characters. RIGHT off the keyboard reaches
 * the results; LEFT off the results returns to the keys. See CLAUDE.md section 14 item 6.
 */
@Composable
internal fun TvSearch(
    server: ActiveServer,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = rememberTvZone("search")
    val keysZone = rememberTvZone("search-keys")
    val voice = rememberVoiceSearch()
    TvFirstFocus(zone)

    val results = state.results
    val hasResults = state.query.length >= 2 && results != null && !results.isEmpty

    TvZone(zone) {
        Row(
            modifier
                .fillMaxSize()
                .background(TvPalette.ground)
                .tvZone(zone)
                .padding(start = TvDims.contentStart, end = TvDims.overscanX, top = TvDims.overscanY, bottom = TvDims.overscanY),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(Modifier.width(KEYBOARD_WIDTH), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                QueryField(query = state.query, searching = state.searching)
                TvZone(keysZone) {
                    Column(
                        Modifier.tvZone(keysZone),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        KEY_ROWS.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                row.forEach { ch ->
                                    KeyTile(label = ch.toString(), key = "k-$ch", onClick = { onQueryChange(state.query + ch) })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            KeyTile(label = "Space", key = "k-space", wide = 3, glyph = TvGlyph.SPACE, onClick = { onQueryChange(state.query + " ") })
                            KeyTile(label = "Delete", key = "k-del", wide = 2, glyph = TvGlyph.BACKSPACE, onClick = { onQueryChange(state.query.dropLast(1)) })
                            KeyTile(label = "Clear", key = "k-clear", wide = 2, onClick = { onQueryChange("") })
                            if (voice.available) {
                                KeyTile(label = "Speak", key = "k-mic", wide = 2, glyph = TvGlyph.MIC, onClick = { voice.start(onQueryChange) })
                            }
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                when {
                    hasResults && results != null -> {
                        ResultRail("Movies", results.movies, server, onItemClick, wide = false)
                        ResultRail("Shows", results.shows, server, onItemClick, wide = false)
                        ResultRail("Episodes", results.episodes, server, onItemClick, wide = true)
                    }
                    state.query.length >= 2 && results != null && !state.searching -> {
                        TvEmpty(title = "No results", body = "Nothing matched “${state.query}”.")
                    }
                    else -> {
                        Column(Modifier.padding(top = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            PlexIcon(kind = PlexIconKind.SEARCH, size = 40.dp, tint = TvPalette.textMuted)
                            PlexText("Search movies, shows and episodes", style = PlexTheme.type.title, colour = TvPalette.text)
                            PlexText("Type at least two characters.", style = PlexTheme.type.body, colour = TvPalette.textDim)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

private val KEYBOARD_WIDTH = 56.dp * 10 + 8.dp * 9
private val KEY_ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")

/** The echoed query. Not a target: there is nothing to do to it that the keys do not do. */
@Composable
private fun QueryField(query: String, searching: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TvShape.card)
            .background(TvPalette.surface)
            .border(1.dp, TvPalette.hairline, TvShape.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlexIcon(kind = PlexIconKind.SEARCH, size = 24.dp, tint = if (query.isEmpty()) TvPalette.textMuted else TvPalette.gold)
        PlexText(
            text = query.ifEmpty { "Search" },
            style = PlexTheme.type.title,
            colour = if (query.isEmpty()) TvPalette.textMuted else TvPalette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (searching) {
            PlexText("…", style = PlexTheme.type.title, colour = TvPalette.textMuted)
        }
    }
}

/** One key: a square tile, [wide] units across for the editing keys. */
@Composable
private fun KeyTile(
    label: String,
    key: String,
    onClick: () -> Unit,
    wide: Int = 1,
    glyph: TvGlyph? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-key")
    val width = 56.dp * wide + 8.dp * (wide - 1)
    Box(
        Modifier
            .scale(scale)
            .width(width)
            .height(56.dp)
            .clip(TvShape.button)
            .background(if (focused) TvPalette.chipFocus else TvPalette.surfaceRaised)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), TvShape.button) else Modifier)
            .tvInteractive(interaction, key = key, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (focused) TvPalette.ink else TvPalette.text
        if (glyph != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TvGlyphIcon(glyph, tint = tint, size = 22.dp)
                if (wide > 1) PlexText(label, style = PlexTheme.type.label, colour = tint, maxLines = 1)
            }
        } else {
            PlexText(text = label.uppercase(), style = PlexTheme.type.label, colour = tint, maxLines = 1)
        }
    }
}

@Composable
private fun ResultRail(
    title: String,
    items: List<MediaItem>,
    server: ActiveServer,
    onItemClick: (MediaItem) -> Unit,
    wide: Boolean,
) {
    if (items.isEmpty()) return
    TvRail(title = title, zone = rememberTvZone("search-$title"), startPadding = 0.dp) {
        items(items, key = { it.ratingKey }) { item ->
            if (wide) {
                TvWideCard(
                    item = item,
                    artworkUrl = server.urls.artwork(item.thumbPath, ArtworkSize.WIDE_WIDTH, ArtworkSize.WIDE_HEIGHT),
                    subLine = cardSubLine(item),
                    width = 260.dp,
                    onClick = { onItemClick(item) },
                )
            } else {
                TvPosterCard(
                    item = item,
                    artworkUrl = server.urls.artwork(item.thumbPath, ArtworkSize.POSTER_WIDTH, ArtworkSize.POSTER_HEIGHT),
                    width = 140.dp,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
}

@Suppress("unused")
private val UNUSED_SIZE = Modifier.size(0.dp)
