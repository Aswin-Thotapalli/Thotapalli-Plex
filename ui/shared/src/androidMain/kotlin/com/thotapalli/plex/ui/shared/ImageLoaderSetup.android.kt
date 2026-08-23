package com.thotapalli.plex.ui.shared

import android.content.Context
import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * On Android the Coil [PlatformContext] is an Android [Context], so the disk cache lives in the
 * app's private cache dir (cleared by the OS under storage pressure, exactly what a cache wants).
 */
actual fun imageCacheDir(ctx: PlatformContext): Path =
    (ctx as Context).cacheDir.resolve("thotapalli_images").toOkioPath()
