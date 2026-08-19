package com.thotapalli.plex.ui.shared.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.thotapalli.plex.ui.design.PlexTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Ambient adaptive colour.
 *
 * A screen tints itself from the content's own artwork, the way Plex ambient mode and Apple
 * Music let the album art bleed into the surface around it. The poster carries the colour and
 * the interface stays quiet, which is exactly the design intent in CLAUDE.md section 12: this
 * pulls one honest accent out of the picture rather than inventing one.
 *
 * The work is done off the artwork: the image is fetched through the same Coil image loader the
 * rest of the app uses, downsampled to a handful of pixels, and reduced to a single vibrant
 * colour that is neither near-black, near-white, nor washed-out grey. The result eases into place
 * with [animateColorAsState] so a change of content is a gentle wash rather than a hard cut, and
 * every result is cached per URL so returning to a screen is instant.
 *
 * It never throws. A null URL, a failed load, an image with no usable colour, or a platform that
 * cannot read pixels all resolve to [fallback].
 */
@Composable
fun rememberDominantColor(
    url: String?,
    fallback: Color = PlexTheme.colours.accent,
): State<Color> {
    val context = LocalPlatformContext.current
    var target by remember { mutableStateOf(fallback) }

    LaunchedEffect(url, fallback, context) {
        if (url.isNullOrBlank()) {
            target = fallback
            return@LaunchedEffect
        }
        cachedColour(url)?.let {
            target = it
            return@LaunchedEffect
        }
        val resolved = runCatching { computeDominantColour(context, url) }.getOrNull() ?: fallback
        storeColour(url, resolved)
        target = resolved
    }

    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = AMBIENT_CROSSFADE_MS),
        label = "ambientColour",
    )
}

private const val AMBIENT_CROSSFADE_MS = 600

/**
 * Pixels are sampled from a tiny decode rather than the full poster. A 48 px longest edge is far
 * more colour than the single accent that comes out of it, and it keeps the read cheap enough to
 * run inline off the load.
 */
private const val SAMPLE_EDGE_PX = 48

// --- Per URL cache. A result never changes for a given URL, so once computed it is reused. ---

private val colourCache = mutableMapOf<String, Color>()
private val cacheMutex = Mutex()

private suspend fun cachedColour(url: String): Color? = cacheMutex.withLock { colourCache[url] }

private suspend fun storeColour(url: String, colour: Color) {
    cacheMutex.withLock { colourCache[url] = colour }
}

private suspend fun computeDominantColour(context: PlatformContext, url: String): Color {
    val loader: ImageLoader = SingletonImageLoader.get(context)
    val pixels = loadArgbPixels(context, loader, url, SAMPLE_EDGE_PX) ?: return NO_COLOUR
    return withContext(Dispatchers.Default) { pickVibrant(pixels) }
}

/**
 * Sentinel meaning "no usable colour was found". Callers treat it as a miss and fall back; it is
 * never returned to a screen because [computeDominantColour] only reaches a screen through the
 * `?: fallback` in [rememberDominantColor].
 */
private val NO_COLOUR = Color.Unspecified

/**
 * Reduce a bag of ARGB pixels to one vibrant colour.
 *
 * Pixels packed as `0xAARRGGBB`, matching both `android.graphics.Bitmap.getPixels` and skia's
 * `Bitmap.getColor`, so the two platform actuals feed identical input here.
 *
 * The method is a coarse quantise and vote. Boring pixels — transparent, near-black, near-white,
 * near-grey — are discarded outright. What remains is bucketed into a small colour cube and each
 * bucket scored by how much of the image it covers weighted by how colourful it is, so a small
 * splash of saturated colour can still win over a large dull field but noise cannot. The winning
 * bucket's average is then pulled into a pleasant range: dark enough to sit under light text,
 * bright and saturated enough to actually read as a colour.
 */
private fun pickVibrant(pixels: IntArray): Color {
    if (pixels.isEmpty()) return NO_COLOUR

    // Buckets keyed by the top 4 bits of each channel (16 levels per channel).
    val population = HashMap<Int, Int>()
    val sumR = HashMap<Int, Long>()
    val sumG = HashMap<Int, Long>()
    val sumB = HashMap<Int, Long>()
    val sumWeight = HashMap<Int, Double>()

    // Step across large images so the vote stays cheap; a 48 px decode is tiny but a caller could
    // hand in more.
    val step = if (pixels.size > 4096) pixels.size / 4096 else 1
    var i = 0
    while (i < pixels.size) {
        val argb = pixels[i]
        i += step

        val a = (argb ushr 24) and 0xFF
        if (a < 128) continue

        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        if (max < 30) continue           // near-black
        if (min > 225) continue          // near-white
        if (delta < 24) continue         // near-grey, no real hue

        // Saturation of an HSV-style cone, 0..1. Higher means more colourful.
        val saturation = delta.toDouble() / max.toDouble()
        // Vote weight favours colourful pixels; squared so a strong hue clearly outvotes a faint one.
        val weight = saturation * saturation

        val key = (r and 0xF0 shl 8) or (g and 0xF0) or (b shr 4)
        population[key] = (population[key] ?: 0) + 1
        sumR[key] = (sumR[key] ?: 0L) + r
        sumG[key] = (sumG[key] ?: 0L) + g
        sumB[key] = (sumB[key] ?: 0L) + b
        sumWeight[key] = (sumWeight[key] ?: 0.0) + weight
    }

    if (population.isEmpty()) return NO_COLOUR

    var bestKey = -1
    var bestScore = -1.0
    for ((key, count) in population) {
        // Coverage weighted by average colourfulness of the bucket.
        val score = (sumWeight[key] ?: 0.0) * count
        if (score > bestScore) {
            bestScore = score
            bestKey = key
        }
    }
    if (bestKey < 0) return NO_COLOUR

    val count = population[bestKey] ?: return NO_COLOUR
    val r = (sumR[bestKey]!! / count).toInt()
    val g = (sumG[bestKey]!! / count).toInt()
    val b = (sumB[bestKey]!! / count).toInt()

    return refine(r, g, b)
}

/**
 * Pull a raw average into a range that tints well: not so dark it disappears under the surface,
 * not so pale or grey it reads as no colour at all. Hue is preserved; only lightness and
 * saturation are nudged toward sensible floors and a ceiling.
 */
private fun refine(r: Int, g: Int, b: Int): Color {
    val (h, s, l) = rgbToHsl(r, g, b)
    val boundedL = l.coerceIn(0.32f, 0.62f)
    val boundedS = s.coerceIn(0.45f, 0.90f)
    val (rr, gg, bb) = hslToRgb(h, boundedS, boundedL)
    return Color(red = rr, green = gg, blue = bb)
}

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun rgbToHsl(r: Int, g: Int, b: Int): Hsl {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min
    val l = (max + min) / 2f

    if (delta == 0f) return Hsl(0f, 0f, l)

    val s = delta / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (max) {
        rf -> 60f * (((gf - bf) / delta) % 6f)
        gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }
    return Hsl(if (h < 0f) h + 360f else h, s, l)
}

private data class Rgb(val r: Float, val g: Float, val b: Float)

private fun hslToRgb(h: Float, s: Float, l: Float): Rgb {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Rgb((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

/**
 * Load the artwork through [imageLoader], downsampled so its longest edge is about [targetPx],
 * and return its pixels packed as `0xAARRGGBB`, or null on any failure.
 *
 * Platform split only because reading pixels out of a decoded image touches the platform bitmap:
 * `android.graphics.Bitmap` on Android, `org.jetbrains.skia.Bitmap` on the JVM desktop.
 */
internal expect suspend fun loadArgbPixels(
    context: PlatformContext,
    imageLoader: ImageLoader,
    url: String,
    targetPx: Int,
): IntArray?
