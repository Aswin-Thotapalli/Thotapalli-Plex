package com.thotapalli.plex.ui.shared

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path

/**
 * Installs the process-wide Coil image loader that the whole app draws artwork through.
 *
 * The default [SingletonImageLoader] Coil builds when no factory is set carries only a small memory
 * cache and no disk cache, so every poster is re-fetched (and re-transcoded server-side) on each
 * cold start and even on a scroll back up a long library. Fast, smooth artwork loading needs both a
 * generous memory cache to keep the visible wall resident and a large disk cache so a poster is
 * fetched from the server exactly once, ever. See CLAUDE.md sections 5 and 13.
 *
 * Must be called once at startup, before the first image load, on each platform (Android
 * Application.onCreate, desktop main before the Compose window).
 *
 * The network fetcher is preserved: [ImageLoader.Builder] keeps Coil's service-loader registration
 * enabled by default, so the ktor3 network fetcher published by coil-network-ktor3 is auto-added to
 * this custom loader without any explicit `.components { }` wiring.
 */
fun installImageLoader() {
    SingletonImageLoader.setSafe { ctx ->
        ImageLoader.Builder(ctx)
            // Keep the visible poster wall (and a screen or two either side) resident in memory so
            // scrolling back never re-decodes.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(ctx, 0.30)
                    .build()
            }
            // A 512 MB on-disk store so a poster is fetched from the server once and served from
            // disk on every later launch and scroll.
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir(ctx))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            // Posters swap straight in from cache; a crossfade only adds a frame of latency to the
            // very effect this is meant to make feel instant.
            .crossfade(false)
            .build()
    }
}

/**
 * The directory Coil's disk cache writes to, resolved per platform: the app cache dir on Android and
 * %LOCALAPPDATA%\ThotapalliPlex\image_cache on Windows (falling back to the JVM temp dir).
 */
expect fun imageCacheDir(ctx: PlatformContext): Path
