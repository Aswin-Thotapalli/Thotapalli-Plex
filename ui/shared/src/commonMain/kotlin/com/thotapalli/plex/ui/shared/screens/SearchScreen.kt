package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.backgroundBrush
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.design.liquidGlass
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PosterTile
import com.thotapalli.plex.ui.shared.SearchState
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.SkeletonBox
import com.thotapalli.plex.ui.shared.plexFocusable
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
    val colours = PlexTheme.colours
    val sizeClass = PlexTheme.sizeClass
    // On a television the search field takes first focus, so the remote has somewhere to
    // start typing. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = sizeClass.isTelevision)
    val pad = sizeClass.screenPadding

    // The field floats over the results, so the list is padded down to start clear of it while
    // still scrolling underneath the frosted glass.
    val fieldZone = if (sizeClass.isTelevision) 76.dp else 60.dp
    val posterWidth: Dp = if (sizeClass.isTelevision) 168.dp else 132.dp

    ContentWidthCap(modifier) {
        // The deep-indigo ground, and the source every glass panel above it frosts.
        Box(Modifier.fillMaxSize().background(colours.backgroundBrush())) {
            val results = state.results

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
                    state.query.length < 2 -> item {
                        SearchPlaceholder(
                            title = "Search your libraries",
                            subtitle = "Type at least two characters to begin.",
                        )
                    }

                    state.searching && results == null -> searchSkeleton(posterWidth)

                    results == null || results.isEmpty -> item {
                        SearchPlaceholder(
                            title = "No results",
                            subtitle = "Nothing matches “${state.query}”.",
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
            .liquidGlass(
                shape = Radius.pill,
                elevated = true,
                specularBoost = if (focused) 0.7f else 0f,
            )
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
                .liquidGlass(shape = Radius.glass)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SectionHeader(title)
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
                .liquidGlass(shape = Radius.glass)
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
                .liquidGlass(shape = Radius.glass)
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
