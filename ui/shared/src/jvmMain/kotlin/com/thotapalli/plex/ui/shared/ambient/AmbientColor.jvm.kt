package com.thotapalli.plex.ui.shared.ambient

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult

/**
 * Windows/JVM pixel read.
 *
 * Compose Desktop renders through Skia, so a decoded Coil image is a `BitmapImage` wrapping an
 * `org.jetbrains.skia.Bitmap`. `getColor` returns each pixel already packed as `0xAARRGGBB`, the
 * same layout the Android actual produces, so the shared quantiser sees identical input on both
 * platforms. Every failure path returns null and the caller falls back.
 */
internal actual suspend fun loadArgbPixels(
    context: PlatformContext,
    imageLoader: ImageLoader,
    url: String,
    targetPx: Int,
): IntArray? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(targetPx)
        .build()

    val image = (imageLoader.execute(request) as? SuccessResult)?.image ?: return null
    val bitmap = (image as? BitmapImage)?.bitmap ?: return null

    return runCatching {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return@runCatching null
        val pixels = IntArray(width * height)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[index++] = bitmap.getColor(x, y)
            }
        }
        pixels
    }.getOrNull()
}
