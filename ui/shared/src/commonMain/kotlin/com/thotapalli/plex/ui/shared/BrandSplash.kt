package com.thotapalli.plex.ui.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.thotapalli.plex.ui.shared.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The launch animation: the brand's own logo build (from `brand/Files/..Animation_Pack`), sampled
 * into a short frame sequence bundled under `composeResources/files/splash` and flipped through
 * once on a cold start, then faded to reveal the app loading underneath.
 *
 * Runs once per process launch (the host stops composing it after [onFinished]). Playback is driven
 * off the real frame clock, measured from the first rendered frame, so the whole animation is seen
 * even after a slow cold start rather than being burned through during the invisible JVM warmup.
 */
@Composable
fun BrandSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val frames = remember { mutableStateListOf<ImageBitmap>() }
    val fade = remember { Animatable(1f) }
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Decode the sequence up front so playback never stutters mid-animation. Guarded so a
        // resource failure can never leave the splash stuck over the app — it just hands over.
        try {
            val loaded = (0 until FRAME_COUNT).map { i ->
                val name = "files/splash/f" + i.toString().padStart(2, '0') + ".png"
                Res.readBytes(name).decodeToImageBitmap()
            }
            frames.addAll(loaded)

            var start = -1L
            while (true) {
                val now = withFrameMillis { it }
                if (start < 0L) start = now
                val i = ((now - start) / FRAME_MS).toInt()
                index = i.coerceAtMost(FRAME_COUNT - 1)
                if (i >= FRAME_COUNT - 1) break
            }
            index = FRAME_COUNT - 1
            fade.animateTo(0f, tween(durationMillis = 300, delayMillis = 220))
        } catch (_: Throwable) {
            // Fall through to reveal the app regardless.
        } finally {
            onFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(fade.value)
            // Matched to the animation frames' own near-black ground so the letterbox is seamless.
            .background(SPLASH_GROUND),
        contentAlignment = Alignment.Center,
    ) {
        frames.getOrNull(index)?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = "Thotapalli Plex",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val FRAME_COUNT = 32
private const val FRAME_MS = 78L // 32 frames ≈ 2.5s — snappy for a launch intro
private val SPLASH_GROUND = Color(0xFF040406)
