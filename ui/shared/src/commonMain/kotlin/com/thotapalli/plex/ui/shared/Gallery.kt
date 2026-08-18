package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.ui.design.LocalSizeClass
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThotapalliTheme

/**
 * Every shared component, with sample data.
 *
 * This is the phase 4 step 2 output. Rendering the components with no server behind them
 * is also the only way to see how they behave with a missing poster, an empty summary and
 * a zero duration, all of which are ordinary in a real library.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    val colours = PlexTheme.colours

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colours.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            PlexTheme.sizeClass.screenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            PlexText(
                text = "${PlexTheme.sizeClass}  ${if (colours.isDark) "dark" else "light"}",
                style = PlexTheme.type.display,
            )
        }

        item {
            Column {
                SectionHeader("Wide progress tile")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(
                        listOf(sampleEpisode, samplePartWatchedMovie, sampleUnstartedMovie),
                    ) { item ->
                        WideProgressTile(item, null, onClick = {}, modifier = Modifier.width(260.dp))
                    }
                }
            }
        }

        item {
            Column {
                SectionHeader("Poster tile")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(
                        listOf(sampleWatchedMovie, samplePartWatchedMovie, sampleShow, sampleEmptyMovie),
                    ) { item ->
                        PosterTile(item, null, onClick = {}, modifier = Modifier.width(140.dp))
                    }
                }
            }
        }

        item {
            Column {
                SectionHeader("Collection tile")
                Row {
                    CollectionTile(sampleCollection, null, onClick = {}, modifier = Modifier.width(140.dp))
                }
            }
        }

        item {
            Column {
                SectionHeader("Library card")
                LibraryCard("Films", "Films", null, onClick = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.xs))
                LibraryCard("Television", "Series", null, onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Column {
                SectionHeader("Episode row")
                EpisodeRow(sampleEpisode, null, onClick = {})
                EpisodeRow(sampleWatchedEpisode, null, onClick = {})
            }
        }
    }
}

/**
 * The responsive grid, with enough sample posters that the column count is obvious.
 * Resizing the window changes the count. See CLAUDE.md section 13.
 */
@Composable
fun ResponsiveGridDemo(modifier: Modifier = Modifier) {
    val sizeClass = PlexTheme.sizeClass
    val items = remember { (1..40).map { sampleMovie(it) } }

    Column(modifier.fillMaxSize().background(PlexTheme.colours.background)) {
        PlexText(
            text = "${sizeClass.name}  minimum poster ${sizeClass.posterMinWidth}",
            style = PlexTheme.type.label,
            colour = PlexTheme.colours.textSecondary,
            modifier = Modifier.padding(Spacing.md),
        )
        PosterGrid {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Films")
            }
            items(items.size, key = { items[it].ratingKey }) { index ->
                PosterTile(items[index], null, onClick = {})
            }
        }
    }
}

// --- sample data --------------------------------------------------------------------------

private fun sampleMovie(index: Int) = Movie(
    ratingKey = "m$index",
    title = "Sample Title $index",
    year = 1990 + (index % 35),
    summary = "",
    thumbPath = null,
    artPath = null,
    durationMs = 6_000_000,
    viewOffsetMs = if (index % 4 == 0) 2_000_000 else 0,
    viewCount = if (index % 7 == 0) 1 else 0,
    titleSort = "Sample Title $index",
    libraryKey = "1",
)

private val sampleWatchedMovie = Movie(
    ratingKey = "w1", title = "A Watched Film", year = 2019, summary = "",
    thumbPath = null, artPath = null, durationMs = 7_241_000,
    viewOffsetMs = 0, viewCount = 1, titleSort = "Watched Film, A", libraryKey = "1",
)

private val samplePartWatchedMovie = Movie(
    ratingKey = "p1", title = "Half Way Through", year = 2021, summary = "",
    thumbPath = null, artPath = null, durationMs = 5_400_000,
    viewOffsetMs = 2_700_000, viewCount = 0, titleSort = "Half Way Through", libraryKey = "1",
)

private val sampleUnstartedMovie = Movie(
    ratingKey = "u1", title = "Not Started", year = 2024, summary = "",
    thumbPath = null, artPath = null, durationMs = 6_600_000,
    viewOffsetMs = 0, viewCount = 0, titleSort = "Not Started", libraryKey = "1",
)

/** No year, no summary, no duration. Ordinary in a real library. */
private val sampleEmptyMovie = Movie(
    ratingKey = "e1", title = "Missing Everything", year = null, summary = "",
    thumbPath = null, artPath = null, durationMs = 0,
    viewOffsetMs = 0, viewCount = 0, titleSort = "Missing Everything", libraryKey = "1",
)

private val sampleShow = Show(
    ratingKey = "s1", title = "A Long Running Series", year = 2014, summary = "",
    thumbPath = null, artPath = null, durationMs = 0, viewOffsetMs = 0, viewCount = 0,
    titleSort = "Long Running Series, A", libraryKey = "2",
    childCount = 8, leafCount = 92, viewedLeafCount = 61,
)

private val sampleCollection = MediaCollection(
    ratingKey = "c1", title = "The Trilogy", year = null, summary = "",
    thumbPath = null, artPath = null, durationMs = 0, viewOffsetMs = 0, viewCount = 0,
    titleSort = "Trilogy, The", libraryKey = "1", childCount = 3,
)

private val sampleEpisode = Episode(
    ratingKey = "ep1", title = "The One With The Markers", year = 2016,
    summary = "An episode summary long enough to wrap onto a second line in the row.",
    thumbPath = null, artPath = null, durationMs = 2_712_000,
    viewOffsetMs = 900_000, viewCount = 0,
    showRatingKey = "s1", seasonRatingKey = "se1", showTitle = "A Long Running Series",
    seasonIndex = 3, episodeIndex = 7,
)

private val sampleWatchedEpisode = Episode(
    ratingKey = "ep2", title = "The Next One Up", year = 2016, summary = "",
    thumbPath = null, artPath = null, durationMs = 2_700_000,
    viewOffsetMs = 0, viewCount = 1,
    showRatingKey = "s1", seasonRatingKey = "se1", showTitle = "A Long Running Series",
    seasonIndex = 3, episodeIndex = 8,
)

// --- previews, one per size class ----------------------------------------------------------

@Preview
@Composable
fun ComponentGalleryCompactPreview() = GalleryPreview(SizeClass.COMPACT)

@Preview
@Composable
fun ComponentGalleryMediumPreview() = GalleryPreview(SizeClass.MEDIUM)

@Preview
@Composable
fun ComponentGalleryExpandedPreview() = GalleryPreview(SizeClass.EXPANDED)

@Preview
@Composable
fun ComponentGalleryTelevisionPreview() = GalleryPreview(SizeClass.TELEVISION)

@Preview
@Composable
fun ComponentGalleryLightPreview() = GalleryPreview(SizeClass.COMPACT, dark = false)

@Composable
private fun GalleryPreview(sizeClass: SizeClass, dark: Boolean = true) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSizeClass provides sizeClass) {
        ThotapalliTheme(sizeClass = sizeClass, darkTheme = dark) {
            Box(Modifier.fillMaxSize()) { ComponentGallery() }
        }
    }
}
