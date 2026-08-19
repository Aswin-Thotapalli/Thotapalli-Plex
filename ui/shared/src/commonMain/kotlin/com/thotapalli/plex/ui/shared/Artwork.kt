package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing

/**
 * Artwork with a fallback that shows the title.
 *
 * A missing poster is common in any real library, and an empty grey rectangle tells the
 * viewer nothing about what it is, so the fallback carries the title instead.
 */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    fallbackTitle: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    val colours = PlexTheme.colours

    Box(modifier = modifier.background(colours.surfaceElevated)) {
        if (url.isNullOrBlank()) {
            ArtworkFallback(fallbackTitle)
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ArtworkFallback(title: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        PlexText(
            text = title,
            style = PlexTheme.type.caption,
            colour = PlexTheme.colours.textSecondary,
            maxLines = 3,
            modifier = Modifier,
        )
    }
}

/**
 * Ties artwork requests to the size class so the server transcodes to roughly what will be
 * shown rather than to full size. See CLAUDE.md section 5.
 */
object ArtworkSize {
    const val POSTER_WIDTH = 400
    const val POSTER_HEIGHT = 600
    const val WIDE_WIDTH = 640
    const val WIDE_HEIGHT = 360
    const val THUMB_WIDTH = 320
    const val THUMB_HEIGHT = 180
    const val BACKDROP_WIDTH = 1920
    const val BACKDROP_HEIGHT = 1080
}
