package com.thotapalli.plex.ui.shared.material

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.random.Random

/**
 * Cinematic *materials*: reusable, additive draw modifiers that give a surface the texture of an
 * expensive title sequence. Nothing here changes layout or intercepts input — each is a pure
 * overlay drawn in the draw phase, so it can be dropped onto a backdrop, a hero or the player
 * shell without touching the composition beneath it.
 *
 * The rule for all of them is restraint. Grain must read as the faint organic tooth of film, never
 * as visible television static; the vignette must only just close the edges of the frame. They earn
 * their keep by being felt rather than seen. Everything is drawn on the dark tokens of
 * CLAUDE.md section 12 first, since the surfaces these dress — backdrop, hero, player — live on the
 * dark ground.
 */

// A small noise tile is generated once and tiled with a repeating shader, so the per-frame cost is
// a single translated rect draw rather than any allocation. The tile is deliberately tiny.
private const val GRAIN_TILE = 80

// Fraction of tile pixels that carry a fleck. Sparse, so the grain is a tooth rather than a wash.
private const val GRAIN_DENSITY = 0.30f

// Distinct phase offsets cycled through to animate the grain. Stepping between a handful of frames
// a dozen times a second reads as film grain; smoothly sliding the pattern would read as drifting
// dirt, which is the wrong texture.
private const val GRAIN_FRAMES = 24
private const val GRAIN_PERIOD_MS = 1_400

// A fixed seed keeps the pattern identical across recompositions and process restarts, so the
// texture never "pops" to a new arrangement when a screen is rebuilt.
private const val GRAIN_SEED = 0x5A17

private const val VIGNETTE_RADIUS_SCALE = 0.80f
private const val VIGNETTE_INNER_STOP = 0.55f

/**
 * A very subtle animated film-grain overlay. Cheap by construction: a single [GRAIN_TILE]-pixel
 * noise tile is baked once into a repeating [ImageShader] and then, each frame, drawn with one
 * translated rect at a low [intensity] alpha. No per-frame allocation.
 *
 * @param intensity overlay alpha of the grain, coerced into `0f..1f`. The `0.04f` default is
 *   barely-there; push toward `0.08f` for a grittier ground and no higher, or the flecks start to
 *   read as noise rather than texture.
 * @param animated when true the grain shimmers between [GRAIN_FRAMES] phases on a loop; when false a
 *   single static grain frame is drawn, which still gives tooth with zero ongoing work.
 */
fun Modifier.filmGrain(intensity: Float = 0.04f, animated: Boolean = true): Modifier = composed {
    // Read the animated phase as a State and sample it inside the draw block, so an advancing frame
    // invalidates only the draw phase and never recomposes anything.
    val transition = rememberInfiniteTransition(label = "film-grain")
    val phase: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GRAIN_PERIOD_MS, easing = LinearEasing),
        ),
        label = "film-grain-phase",
    )

    val alpha = intensity.coerceIn(0f, 1f)

    this
        .clipToBounds()
        .drawWithCache {
            val tile = buildNoiseTile()
            val brush = ShaderBrush(
                ImageShader(tile, TileMode.Repeated, TileMode.Repeated),
            )
            // Pre-rolled phase offsets. Because the shader repeats, translating by any offset shows
            // a fresh region of the endless field.
            val random = Random(GRAIN_SEED.toLong() xor 0x9E37L)
            val offsets = List(GRAIN_FRAMES) {
                Offset(
                    random.nextInt(GRAIN_TILE).toFloat(),
                    random.nextInt(GRAIN_TILE).toFloat(),
                )
            }
            val tileF = GRAIN_TILE.toFloat()
            val coverage = Size(size.width + tileF * 2f, size.height + tileF * 2f)

            onDrawWithContent {
                drawContent()
                if (alpha <= 0f) return@onDrawWithContent
                val index = if (animated) {
                    ((phase.value * GRAIN_FRAMES).toInt()).mod(GRAIN_FRAMES)
                } else {
                    0
                }
                val offset = offsets[index]
                // Overdraw by one tile on every side so the translated pattern leaves no seam at the
                // top-left edge; the surrounding clipToBounds trims the bleed.
                translate(offset.x - tileF, offset.y - tileF) {
                    drawRect(brush = brush, topLeft = Offset.Zero, size = coverage, alpha = alpha)
                }
            }
        }
}

/**
 * A soft radial darkening at the edges of the frame — the quiet vignette a cinema lens leaves. The
 * centre is untouched out to [VIGNETTE_INNER_STOP] of the radius, then black ramps in to
 * [strength] alpha at the corners.
 *
 * @param strength alpha of the darkening at the frame edge, coerced into `0f..1f`. The `0.35f`
 *   default closes the edges gently; it should never be strong enough to notice as a shape.
 */
fun Modifier.vignette(strength: Float = 0.35f): Modifier {
    val edge = strength.coerceIn(0f, 1f)
    return this.drawWithCache {
        val radius = maxOf(size.width, size.height) * VIGNETTE_RADIUS_SCALE
        val brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                VIGNETTE_INNER_STOP to Color.Transparent,
                1f to Color.Black.copy(alpha = edge),
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }
}

/**
 * A one-call cinematic dressing for a backdrop: a gentle [vignette] under a faint animated
 * [filmGrain]. The defaults are pulled a touch below each modifier's own so that layering the two
 * still reads as "expensive", never gaudy.
 */
fun Modifier.cinematicTexture(
    vignetteStrength: Float = 0.28f,
    grainIntensity: Float = 0.035f,
    animated: Boolean = true,
): Modifier = this
    .vignette(vignetteStrength)
    .filmGrain(intensity = grainIntensity, animated = animated)

/**
 * Bake the noise tile: scattered grey flecks of random value on a transparent field. Mixed greys
 * mean the overlay both lifts and deepens the surface beneath it in equal measure, which is what
 * gives film grain its neutral tooth rather than a one-sided haze.
 */
private fun buildNoiseTile(): ImageBitmap {
    val bitmap = ImageBitmap(GRAIN_TILE, GRAIN_TILE)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val random = Random(GRAIN_SEED.toLong())
    for (y in 0 until GRAIN_TILE) {
        for (x in 0 until GRAIN_TILE) {
            if (random.nextFloat() < GRAIN_DENSITY) {
                val value = random.nextFloat()
                paint.color = Color(red = value, green = value, blue = value, alpha = 1f)
                canvas.drawRect(
                    left = x.toFloat(),
                    top = y.toFloat(),
                    right = x + 1f,
                    bottom = y + 1f,
                    paint = paint,
                )
            }
        }
    }
    return bitmap
}
