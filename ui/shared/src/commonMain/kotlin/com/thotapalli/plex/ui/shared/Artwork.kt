package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Artwork with a shimmering placeholder while it loads and a title fallback when it is missing.
 *
 * The placeholder matters as much as the image: without it a fast scroll through a library shows only
 * the empty ground while each poster is still being fetched and transcoded, so the viewer scrolls
 * past content they never saw. A shimmer in the poster's shape reads as "loading here" and, once the
 * image (memory- or disk-cached by Coil after its first fetch) arrives, it simply replaces the
 * shimmer. A missing poster is common in any real library, so its fallback carries the title instead
 * of an empty rectangle. See CLAUDE.md sections 5 and 13.
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

    Box(modifier.background(colours.surfaceElevated)) {
        if (url.isNullOrBlank()) {
            ArtworkFallback(fallbackTitle)
        } else {
            val painter = rememberAsyncImagePainter(model = url, contentScale = contentScale)
            val state by painter.state.collectAsState()

            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )

            when (state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Empty ->
                    // A shimmer standing in for the poster until it resolves, so a fast scroll shows
                    // "loading" rather than bare ground.
                    Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp)))

                is AsyncImagePainter.State.Error -> ArtworkFallback(fallbackTitle)

                is AsyncImagePainter.State.Success -> Unit
            }
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
 * Warms Coil's cache for artwork just below the fold so a poster is decoded and resident before it
 * scrolls into view, rather than starting its fetch (and server-side transcode) only once the tile
 * appears. This is what turns a fast flick through a long library from a wall of shimmer into
 * already-loaded posters. See CLAUDE.md sections 5 and 13.
 *
 * [urls] is the artwork URL for each item in list/scroll order — index-aligned with the items the
 * [lazyState] scrolls. As the last visible index advances, the next [aheadBy] URLs are enqueued into
 * the process image loader ([installImageLoader]); the memory and disk caches absorb the results, so
 * requests already in cache are cheap no-ops and nothing is fetched twice.
 *
 * A no-op when [urls] is empty. Enqueued requests are fire-and-forget: their [coil3.request.Disposable]
 * is intentionally not held, because a poster warmed but scrolled past should still land in the cache
 * for the scroll back.
 */
@Composable
fun rememberArtworkPrefetch(
    urls: List<String?>,
    lazyState: LazyListState,
    aheadBy: Int = 12,
) {
    val ctx = LocalPlatformContext.current
    LaunchedEffect(lazyState, urls, aheadBy, ctx) {
        if (urls.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(ctx)
        snapshotFlow { lazyState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                for (i in (last + 1)..(last + aheadBy)) {
                    val url = urls.getOrNull(i) ?: continue
                    if (url.isBlank()) continue
                    loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
                }
            }
    }
}

/**
 * Grid variant of [rememberArtworkPrefetch], for the poster walls that scroll through a
 * [LazyGridState] (the library grid). Same warm-ahead behaviour, keyed to the last visible cell.
 */
@Composable
fun rememberArtworkPrefetch(
    urls: List<String?>,
    lazyState: LazyGridState,
    aheadBy: Int = 12,
) {
    val ctx = LocalPlatformContext.current
    LaunchedEffect(lazyState, urls, aheadBy, ctx) {
        if (urls.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(ctx)
        snapshotFlow { lazyState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                for (i in (last + 1)..(last + aheadBy)) {
                    val url = urls.getOrNull(i) ?: continue
                    if (url.isBlank()) continue
                    loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
                }
            }
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
