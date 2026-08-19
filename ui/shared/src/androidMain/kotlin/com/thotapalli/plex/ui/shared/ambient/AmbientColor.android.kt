package com.thotapalli.plex.ui.shared.ambient

import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult

/**
 * Android pixel read.
 *
 * The artwork is decoded small through the shared Coil loader, then its pixels are pulled out of
 * the `android.graphics.Bitmap`. A hardware bitmap cannot be read directly, so one is copied into
 * a software `ARGB_8888` buffer first. Every failure path returns null and the caller falls back.
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
    val source = (image as? BitmapImage)?.bitmap ?: return null

    return runCatching {
        val readable = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
        } else {
            source
        }
        val width = readable.width
        val height = readable.height
        if (width <= 0 || height <= 0) return@runCatching null
        IntArray(width * height).also { readable.getPixels(it, 0, width, 0, 0, width, height) }
    }.getOrNull()
}
