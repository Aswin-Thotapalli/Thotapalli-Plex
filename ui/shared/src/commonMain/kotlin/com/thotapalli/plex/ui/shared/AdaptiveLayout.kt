package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.LocalSizeClass
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing

/**
 * The poster grid from CLAUDE.md section 13.
 *
 * Columns come from the available width and a minimum poster width, never from a device
 * list. Beyond 1600dp the grid keeps adding columns and the cap centres the content, so
 * posters never stretch.
 */
@Composable
fun PosterGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(PlexTheme.sizeClass.screenPadding),
    content: LazyGridScope.() -> Unit,
) {
    val sizeClass = PlexTheme.sizeClass

    ContentWidthCap(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = sizeClass.posterMinWidth),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(Layout.gridHorizontalGap),
            verticalArrangement = Arrangement.spacedBy(Layout.gridVerticalGap),
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

/**
 * Caps content at 1600dp and centres it. Above the cap the window keeps growing and the
 * content does not. See CLAUDE.md section 13.
 */
@Composable
fun ContentWidthCap(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Layout.maxContentWidth).fillMaxSize()) {
            content()
        }
    }
}

/**
 * Recomputes the size class from the measured width.
 *
 * On Windows this runs during the resize drag, which is why it measures rather than
 * reading a value captured at start up.
 */
@Composable
fun WithSizeClass(
    isTelevision: Boolean = false,
    content: @Composable (SizeClass) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizeClass = SizeClass.fromWidth(maxWidth, isTelevision)
        androidx.compose.runtime.CompositionLocalProvider(LocalSizeClass provides sizeClass) {
            content(sizeClass)
        }
    }
}

/**
 * Overscan margin for television. Five percent on all four edges of every screen.
 * See CLAUDE.md section 13.
 */
@Composable
fun overscanPadding(sizeClass: SizeClass, width: Dp, height: Dp): PaddingValues =
    if (!sizeClass.isTelevision) {
        PaddingValues(sizeClass.screenPadding)
    } else {
        PaddingValues(
            horizontal = width * Layout.TELEVISION_OVERSCAN_FRACTION,
            vertical = height * Layout.TELEVISION_OVERSCAN_FRACTION,
        )
    }

/**
 * A two pane detail layout: poster and metadata on the left at a fixed 380dp, the season
 * and episode list filling the remainder. Falls back to one column below Expanded.
 * See CLAUDE.md section 13.
 */
@Composable
fun DetailLayout(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    val sizeClass = PlexTheme.sizeClass

    if (sizeClass.twoPaneDetail) {
        androidx.compose.foundation.layout.Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(Modifier.widthIn(min = Layout.detailPaneWidth, max = Layout.detailPaneWidth)) {
                left()
            }
            Box(Modifier.weight(1f)) { right() }
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxWidth()) {
            item { left() }
            item { right() }
        }
    }
}
