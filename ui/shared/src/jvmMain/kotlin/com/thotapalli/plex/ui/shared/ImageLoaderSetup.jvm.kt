package com.thotapalli.plex.ui.shared

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * On Windows the disk cache lives under %LOCALAPPDATA%\ThotapalliPlex\image_cache, matching where
 * the rest of the desktop app keeps its per-user data (see CLAUDE.md section 11). Falls back to the
 * JVM temp dir when LOCALAPPDATA is somehow unset.
 */
actual fun imageCacheDir(ctx: PlatformContext): Path =
    File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"),
        "ThotapalliPlex/image_cache",
    ).toOkioPath()
