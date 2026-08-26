package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SearchState
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.SkeletonBox
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.plexFocusable
import com.thotapalli.plex.ui.shared.rememberVoiceSearch
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus

/**
 * Search: one field, results grouped under Movies, Shows and Episodes.
 *
 * The field is a floating liquid-glass pill riding above the results, which scroll beneath it and
 * frost as they pass under. Each group of results sits in its own frosted panel, art-forward. The
 * 300 ms debounce and the two character minimum live in the view model, because they govern when a
 * request is made rather than how the field looks. See CLAUDE.md section 14.
 */
@Composable
fun SearchScreen(
    server: ActiveServer,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
) {
    // The television screen is a different shape entirely — a big field, an on-screen keyboard, a
    // mic and a recent-searches strip, all driven by the D-pad. It shares nothing of the phone and
    // desktop layout below, so it branches out here and returns. See CLAUDE.md sections 13 and 14.
    if (PlexTheme.sizeClass == SizeClass.TELEVISION) {
        TelevisionSearch(
            server = server,
            state = state,
            onQueryChange = onQueryChange,
            onItemClick = onItemClick,
            modifier = modifier,
            focusRequester = focusRequester,
        )
        return
    }

    val sizeClass = PlexTheme.sizeClass
    // On a television the search field takes first focus, so the remote has somewhere to
    // start typing. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = sizeClass.isTelevision)
    val pad = sizeClass.screenPadding

    // The field floats over the results, so the list is padded down to start clear of it while
    // still scrolling underneath the frosted glass.
    val fieldZone = if (sizeClass.isTelevision) 76.dp else 60.dp
    val posterWidth: Dp = if (sizeClass.isTelevision) 168.dp else 132.dp

    // Recent searches live only in memory for now — deliberately kept out of core/session, matching
    // the television branch. rememberSaveable survives configuration changes within the session.
    // TODO persist recent searches (a store in core/session, out of scope for this screen).
    val recents: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { emptyList<String>().toMutableStateList() }

    val results = state.results
    val hasResults = state.query.length >= 2 && results != null && !results.isEmpty

    // A typed query that lands results is a completed search worth remembering.
    LaunchedEffect(state.query, hasResults) {
        if (hasResults) recordRecent(recents, state.query)
    }

    ContentWidthCap(modifier) {
        // The translucent veil over the ambient backdrop, and the source every glass panel frosts.
        Box(Modifier.fillMaxSize().material(GlassRole.GROUND)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().glassSource(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(
                    start = pad,
                    end = pad,
                    top = pad + fieldZone + Spacing.sm,
                    bottom = Spacing.xl,
                ),
            ) {
                when {
                    // Before typing: a quiet invitation, and the viewer's own recent searches as
                    // re-runnable chips when there are any.
                    state.query.isEmpty() -> {
                        item {
                            SearchPlaceholder(
                                title = "Search movies, shows and episodes",
                                subtitle = "Find anything across your libraries.",
                            )
                        }
                        if (recents.isNotEmpty()) {
                            item(key = "recents") {
                                RecentSearches(
                                    recents = recents,
                                    onPick = { q -> onQueryChange(q); recordRecent(recents, q) },
                                    onClearAll = { recents.clear() },
                                )
                            }
                        }
                    }

                    // One character typed: the two-character minimum lives in the view model, so
                    // nothing is requested yet — say so rather than looking idle.
                    state.query.length < 2 -> item {
                        SearchPlaceholder(
                            title = "Keep typing",
                            subtitle = "Type at least two characters to search.",
                        )
                    }

                    // A request is in flight and nothing has come back yet.
                    state.searching && results == null -> searchSkeleton(posterWidth)

                    results == null || results.isEmpty -> item {
                        SearchPlaceholder(
                            title = "No results",
                            subtitle = "No results for “${state.query}”.",
                        )
                    }

                    else -> {
                        resultGroup("Movies", results.movies, server, onItemClick, posterWidth)
                        resultGroup("Shows", results.shows, server, onItemClick, posterWidth)
                        resultGroup("Episodes", results.episodes, server, onItemClick, posterWidth)
                    }
                }
            }

            // The floating field. Its focus requesters ride the text field itself so the remote
            // and the keyboard land in the input, not on the glass around it.
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                focusRequester = focusRequester,
                firstFocus = firstFocus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = pad, vertical = pad),
            )
        }
    }
}

/** The search input: a frosted glass pill that catches an amber rim and more light while focused. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .material(GlassRole.CHIP, shape = Radius.pill)
            // Selection and focus are the accent's only jobs: an amber rim on the focused field.
            .then(
                if (focused) Modifier.border(1.5.dp, colours.accent, Radius.pill) else Modifier,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlexIcon(
            kind = PlexIconKind.SEARCH,
            size = 20.dp,
            tint = if (focused) colours.accent else colours.textSecondary,
        )
        Spacer(Modifier.width(Spacing.sm))

        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                PlexText(
                    text = "Search",
                    style = PlexTheme.type.body,
                    colour = colours.textSecondary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = LocalTextStyle.current.merge(PlexTheme.type.body)
                    .copy(color = colours.textPrimary),
                cursorBrush = SolidColor(colours.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusRequester(firstFocus),
            )
        }

        // A clear affordance once there is something to clear.
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .plexFocusable(shape = Radius.pill, onClick = { onQueryChange("") })
                    .padding(Spacing.xxs),
            ) {
                PlexIcon(
                    kind = PlexIconKind.CLOSE,
                    size = 18.dp,
                    tint = colours.textSecondary,
                )
            }
        }
    }
}

/** One result group in its own frosted panel: a header, then an art-forward poster rail. */
private fun LazyListScope.resultGroup(
    title: String,
    items: List<MediaItem>,
    server: ActiveServer,
    onItemClick: (MediaItem) -> Unit,
    posterWidth: Dp,
) {
    if (items.isEmpty()) return

    // At most twenty per group. See CLAUDE.md section 14.
    val shown = items.take(20)

    item(key = "group-$title") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .material(GlassRole.CARD, shape = Radius.glass)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // The header carries a count so each group reads at a glance. It reflects how many are
            // shown, which is the true match count until it saturates at the twenty per group cap.
            SectionHeader(title, trailing = { CountBadge(shown.size) })
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(vertical = Spacing.xxs),
            ) {
                items(shown, key = { it.ratingKey }) { entry ->
                    PosterTile(
                        item = entry,
                        artworkUrl = server.urls.artwork(
                            entry.thumbPath,
                            ArtworkSize.POSTER_WIDTH,
                            ArtworkSize.POSTER_HEIGHT,
                        ),
                        onClick = { onItemClick(entry) },
                        modifier = Modifier.width(posterWidth),
                    )
                }
            }
        }
    }
}

/** A pair of shimmering panels while the first results are on their way in. */
private fun LazyListScope.searchSkeleton(posterWidth: Dp) {
    items(2) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .material(GlassRole.CARD, shape = Radius.glass)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBox(
                modifier = Modifier.width(120.dp).height(16.dp),
                shape = Radius.pill,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(4) {
                    SkeletonBox(
                        modifier = Modifier.width(posterWidth).height(posterWidth * 1.5f),
                        shape = Radius.poster,
                    )
                }
            }
        }
    }
}

/** A small frosted pill carrying a group's match count, sitting at the end of its header. */
@Composable
private fun CountBadge(count: Int) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .material(GlassRole.CHIP, shape = Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        PlexText(
            text = count.toString(),
            style = PlexTheme.type.label,
            colour = colours.textSecondary,
            maxLines = 1,
        )
    }
}

/** A quiet frosted placeholder for the empty, too-short and no-results states. */
@Composable
private fun SearchPlaceholder(title: String, subtitle: String) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .material(GlassRole.CARD, shape = Radius.glass)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PlexIcon(kind = PlexIconKind.SEARCH, size = 32.dp, tint = colours.textSecondary)
            Spacer(Modifier.height(Spacing.xxs))
            PlexText(text = title, style = PlexTheme.type.title, colour = colours.textPrimary)
            PlexText(
                text = subtitle,
                style = PlexTheme.type.body.copy(textAlign = TextAlign.Center),
                colour = colours.textSecondary,
            )
        }
    }
}

/**
 * Fold a completed query into the recent-searches list, newest first, capped at eight. Shared by
 * the phone/desktop body and the television branch so both dedupe and prefix-collapse identically:
 * an exact duplicate and any earlier partial the query grew out of are dropped, so typing a title
 * letter by letter collapses to a single recent rather than a ladder of prefixes.
 */
private fun recordRecent(recents: SnapshotStateList<String>, raw: String) {
    val q = raw.trim()
    if (q.length < 2) return
    val kept = recents.filterNot {
        it.equals(q, ignoreCase = true) || q.startsWith(it, ignoreCase = true)
    }
    recents.clear()
    recents.add(q)
    recents.addAll(kept)
    while (recents.size > 8) recents.removeAt(recents.lastIndex)
}

// ---------------------------------------------------------------------------------------------
// Television search
//
// A big display field, a D-pad-navigable on-screen keyboard with a mic beside it, and a strip of
// the viewer's own recent searches. Typing runs straight through the same onQueryChange the phone
// and desktop use, so the 300 ms debounce and two-character minimum in the view model apply
// unchanged. No trending or discovery surface — recent searches are the viewer's own history and
// nothing more. See CLAUDE.md sections 1, 13 and 14.
// ---------------------------------------------------------------------------------------------

/** The height of one keyboard key on a television. Large enough to read across a room. */
private val TV_KEY_HEIGHT: Dp = 64.dp

@Composable
private fun TelevisionSearch(
    server: ActiveServer,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester,
) {
    val pad = SizeClass.TELEVISION.screenPadding
    val posterWidth = 168.dp
    val voice = rememberVoiceSearch()
    // The remote lands on the first key on entry, so there is always somewhere to start typing.
    val firstFocus = rememberFirstFocus(enabled = true)

    // Recent searches live only in memory for now — the field is intentionally kept out of
    // core/session. rememberSaveable survives configuration changes within the session.
    // TODO persist recent searches (a store in core/session, out of scope for this screen).
    val recents: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { emptyList<String>().toMutableStateList() }

    val results = state.results
    val hasResults = state.query.length >= 2 && results != null && !results.isEmpty

    // A typed query that lands results is a completed search worth remembering.
    LaunchedEffect(state.query, hasResults) {
        if (hasResults) recordRecent(recents, state.query)
    }

    ContentWidthCap(modifier) {
        Box(Modifier.fillMaxSize().material(GlassRole.GROUND)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource()
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                TvSearchField(query = state.query)

                if (hasResults && results != null) {
                    // With results in hand the keyboard steps aside and the groups fill the screen.
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        contentPadding = PaddingValues(bottom = Spacing.xl),
                    ) {
                        resultGroup("Movies", results.movies, server, onItemClick, posterWidth)
                        resultGroup("Shows", results.shows, server, onItemClick, posterWidth)
                        resultGroup("Episodes", results.episodes, server, onItemClick, posterWidth)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        TvKeyboard(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            firstKeyRequesters = listOf(firstFocus, focusRequester),
                            modifier = Modifier.weight(1f),
                        )
                        // The mic only appears where the platform can actually transcribe speech,
                        // so it is simply absent on desktop/JVM.
                        if (voice.available) {
                            TvMic(
                                onStart = {
                                    voice.start { spoken ->
                                        onQueryChange(spoken)
                                        recordRecent(recents, spoken)
                                    }
                                },
                            )
                        }
                    }

                    RecentSearches(
                        recents = recents,
                        onPick = { q -> onQueryChange(q); recordRecent(recents, q) },
                        onClearAll = { recents.clear() },
                    )
                }
            }
        }
    }
}

/** The large display field at the top: a glyph and the query, or the placeholder. Not typed into. */
@Composable
private fun TvSearchField(query: String) {
    val colours = PlexTheme.colours
    val active = query.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .material(GlassRole.CARD, shape = Radius.pill)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlexIcon(
            kind = PlexIconKind.SEARCH,
            size = 28.dp,
            tint = if (active) colours.accent else colours.textSecondary,
        )
        Spacer(Modifier.width(Spacing.md))
        PlexText(
            text = query.ifEmpty { "Search for movies, shows, people…" },
            style = PlexTheme.type.title,
            colour = if (active) colours.textPrimary else colours.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The on-screen QWERTY keyboard. Every key is a focusable glass tile driven by the D-pad. */
@Composable
private fun TvKeyboard(
    query: String,
    onQueryChange: (String) -> Unit,
    firstKeyRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    var shift by remember { mutableStateOf(false) }

    fun type(ch: Char) {
        val c = if (shift) ch.uppercaseChar() else ch
        onQueryChange(query + c)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Digits, then a backspace.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            "1234567890".forEachIndexed { i, ch ->
                KeyTile(
                    label = ch.toString(),
                    onClick = { onQueryChange(query + ch) },
                    modifier = Modifier.weight(1f),
                    focusRequesters = if (i == 0) firstKeyRequesters else emptyList(),
                )
            }
            KeyTile(
                label = "",
                onClick = { onQueryChange(query.dropLast(1)) },
                modifier = Modifier.weight(1f),
                glyph = { BackspaceGlyph(tint = PlexTheme.colours.textPrimary, size = 28.dp) },
            )
        }
        // QWERTY.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            "QWERTYUIOP".forEach { ch ->
                KeyTile(ch.toString(), { type(ch.lowercaseChar()) }, Modifier.weight(1f))
            }
        }
        // The home row, inset a little for the classic stagger.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Spacer(Modifier.weight(0.5f))
            "ASDFGHJKL".forEach { ch ->
                KeyTile(ch.toString(), { type(ch.lowercaseChar()) }, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(0.5f))
        }
        // Shift, the bottom letters, and a hyphen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            KeyTile(
                label = "Shift",
                onClick = { shift = !shift },
                modifier = Modifier.weight(1.5f),
                accented = shift,
            )
            "ZXCVBNM".forEach { ch ->
                KeyTile(ch.toString(), { type(ch.lowercaseChar()) }, Modifier.weight(1f))
            }
            KeyTile("-", { onQueryChange(query + "-") }, Modifier.weight(1f))
        }
        // Symbols placeholder, a wide space, and clear.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            KeyTile(
                label = "?123",
                // The digit row already covers numbers; a full symbol layer is out of scope.
                onClick = {},
                modifier = Modifier.weight(1.5f),
            )
            KeyTile("Space", { onQueryChange(query + " ") }, Modifier.weight(5f))
            KeyTile("Clear", { onQueryChange("") }, Modifier.weight(1.5f))
        }
    }
}

/** One keyboard key: a glass chip that grows and rings in the accent when the D-pad lands on it. */
@Composable
private fun KeyTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequesters: List<FocusRequester> = emptyList(),
    accented: Boolean = false,
    glyph: (@Composable () -> Unit)? = null,
) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .height(TV_KEY_HEIGHT)
            .let { m -> focusRequesters.fold(m) { acc, r -> acc.focusRequester(r) } }
            .material(GlassRole.CHIP, shape = Radius.glassSmall)
            .plexFocusable(shape = Radius.glassSmall, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            glyph()
        } else {
            PlexText(
                text = label,
                style = PlexTheme.type.title,
                colour = if (accented) colours.accent else colours.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/** The circular voice button and its caption, sitting to the right of the keyboard. */
@Composable
private fun TvMic(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours
    Column(
        modifier = modifier.width(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .material(GlassRole.CHIP, shape = Radius.pill)
                .plexFocusable(shape = Radius.pill, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            MicGlyph(tint = colours.accent, size = 48.dp)
        }
        PlexText(
            text = "Press to speak",
            style = PlexTheme.type.caption.copy(textAlign = TextAlign.Center),
            colour = colours.textSecondary,
            maxLines = 2,
        )
    }
}

/** The viewer's own recent searches, as re-runnable chips, with a clear-all affordance. */
@Composable
private fun RecentSearches(
    recents: SnapshotStateList<String>,
    onPick: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recents.isEmpty()) return
    val colours = PlexTheme.colours
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(
            title = "Recent Searches",
            trailing = {
                Row(
                    modifier = Modifier
                        .plexFocusable(shape = Radius.pill, onClick = onClearAll)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    TrashGlyph(tint = colours.textSecondary, size = 18.dp)
                    PlexText(
                        text = "Clear All",
                        style = PlexTheme.type.label,
                        colour = colours.textSecondary,
                    )
                }
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(recents, key = { it }) { q ->
                Row(
                    modifier = Modifier
                        .material(GlassRole.CHIP, shape = Radius.pill)
                        .plexFocusable(shape = Radius.pill, onClick = { onPick(q) })
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    HistoryGlyph(tint = colours.textSecondary, size = 18.dp)
                    PlexText(
                        text = q,
                        style = PlexTheme.type.label,
                        colour = colours.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// --- Local line glyphs -----------------------------------------------------------------------
// The shared PlexIcon set has no mic, backspace, history or trash glyph, and its enum lives in
// another file. These four are drawn here in the same stroked-on-a-Canvas style so they scale
// cleanly and take a tint like the rest of the icon set.

private fun DrawScope.plexStroke(): Stroke =
    Stroke(width = size.minDimension * 0.083f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
private fun MicGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = plexStroke()
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.38f, h * 0.14f),
            size = Size(w * 0.24f, h * 0.40f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = s,
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.30f, h * 0.34f),
            size = Size(w * 0.40f, h * 0.34f),
            style = s,
        )
        drawLine(tint, Offset(w * 0.50f, h * 0.68f), Offset(w * 0.50f, h * 0.82f), s.width, s.cap)
        drawLine(tint, Offset(w * 0.38f, h * 0.82f), Offset(w * 0.62f, h * 0.82f), s.width, s.cap)
    }
}

@Composable
private fun BackspaceGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = plexStroke()
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.40f, h * 0.28f)
            lineTo(w * 0.80f, h * 0.28f)
            lineTo(w * 0.80f, h * 0.72f)
            lineTo(w * 0.40f, h * 0.72f)
            lineTo(w * 0.20f, h * 0.50f)
            close()
        }
        drawPath(body, tint, style = s)
        drawLine(tint, Offset(w * 0.52f, h * 0.42f), Offset(w * 0.68f, h * 0.58f), s.width, s.cap)
        drawLine(tint, Offset(w * 0.68f, h * 0.42f), Offset(w * 0.52f, h * 0.58f), s.width, s.cap)
    }
}

@Composable
private fun HistoryGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = plexStroke()
        val w = this.size.width
        val h = this.size.height
        drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = s)
        // The hands, reading as a clock and so as time past.
        drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.28f), s.width, s.cap)
        drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.66f, h * 0.56f), s.width, s.cap)
    }
}

@Composable
private fun TrashGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = plexStroke()
        val w = this.size.width
        val h = this.size.height
        drawLine(tint, Offset(w * 0.24f, h * 0.30f), Offset(w * 0.76f, h * 0.30f), s.width, s.cap)
        val handle = Path().apply {
            moveTo(w * 0.42f, h * 0.30f)
            lineTo(w * 0.42f, h * 0.22f)
            lineTo(w * 0.58f, h * 0.22f)
            lineTo(w * 0.58f, h * 0.30f)
        }
        drawPath(handle, tint, style = s)
        val can = Path().apply {
            moveTo(w * 0.30f, h * 0.30f)
            lineTo(w * 0.34f, h * 0.78f)
            lineTo(w * 0.66f, h * 0.78f)
            lineTo(w * 0.70f, h * 0.30f)
        }
        drawPath(can, tint, style = s)
    }
}
