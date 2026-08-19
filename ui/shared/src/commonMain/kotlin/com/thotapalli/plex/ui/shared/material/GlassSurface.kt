package com.thotapalli.plex.ui.shared.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius

/**
 * A frosted, translucent glass material for surfaces that float over content — a nav bar riding a
 * poster grid, the top controls over a hero, a chip over the player. It is built to sit *above*
 * the thing it dims, letting the colour of the artwork below leak through while keeping its own
 * content legible.
 *
 * ### On the backdrop blur
 * A true frosted glass samples and blurs the pixels *behind* it. That requires reading the
 * backdrop, which on this stack means a platform render effect (Android `RenderEffect`, Skia
 * `ImageFilter`) reachable only from platform code — and this material is `commonMain`, pure
 * Compose, no platform hooks. Rather than pretend, it takes the documented graceful-degradation
 * path: a layered translucent frost — a tuned fill, a top-lit sheen and a crisp hairline highlight
 * with a hairline border — that reads as glass on any target without touching a backdrop it cannot
 * see. A caller that already has the backdrop bitmap in hand can still blur *that* with
 * `Modifier.blur` (supported on Android 31+ and Compose Desktop) and drop this frost on top; the
 * two compose cleanly.
 */

// Translucent fill so the artwork below leaks through. Section 8: the frost is a veil, not a wall.
private const val GLASS_FILL_ALPHA = 0.6f

// A top-lit sheen and a crisp top hairline sell the glass. Brighter on light, where a white
// highlight against a pale ground needs more presence to register; whisper-quiet on dark.
private const val SHEEN_ALPHA_DARK = 0.05f
private const val SHEEN_ALPHA_LIGHT = 0.22f
private const val HAIRLINE_ALPHA_DARK = 0.10f
private const val HAIRLINE_ALPHA_LIGHT = 0.45f
private const val BORDER_ALPHA = 0.6f

/**
 * Dress a surface as frosted glass: a translucent [tint] fill under a soft top sheen, a bright top
 * hairline and a hairline [border], all clipped to [shape]. Additive — it draws the frost behind
 * whatever content the decorated node paints, and strokes the border back over the top so the pane
 * reads as a framed sheet of glass.
 *
 * @param shape the pane outline; the modifier clips to it. Defaults to [Radius.card].
 * @param tint the fill colour. Left [Color.Unspecified] it resolves to the theme surface at
 *   [GLASS_FILL_ALPHA], the translucent default; pass a colour to cast the glass warm or cold.
 * @param highlight draws the top sheen and hairline that give the pane its lit upper edge.
 * @param border strokes the hairline frame around the pane.
 */
fun Modifier.glass(
    shape: Shape = Radius.card,
    tint: Color = Color.Unspecified,
    highlight: Boolean = true,
    border: Boolean = true,
): Modifier = composed {
    val colours = PlexTheme.colours
    val fill = if (tint.isSpecified) tint else colours.surface.copy(alpha = GLASS_FILL_ALPHA)
    val borderColour = colours.border.copy(alpha = BORDER_ALPHA)
    val sheenAlpha = if (colours.isDark) SHEEN_ALPHA_DARK else SHEEN_ALPHA_LIGHT
    val hairlineAlpha = if (colours.isDark) HAIRLINE_ALPHA_DARK else HAIRLINE_ALPHA_LIGHT

    this
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val strokePx = 1.dp.toPx()
            // A soft glow down from the top edge, and a tight bright line right at it.
            val sheenBrush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = sheenAlpha), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.45f,
            )
            val hairlineBrush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = hairlineAlpha), Color.Transparent),
                startY = 0f,
                endY = strokePx * 1.5f,
            )

            onDrawWithContent {
                drawOutline(outline, color = fill)
                if (highlight) {
                    drawOutline(outline, brush = sheenBrush)
                    drawOutline(outline, brush = hairlineBrush)
                }
                drawContent()
                if (border) {
                    drawOutline(outline, color = borderColour, style = Stroke(width = strokePx))
                }
            }
        }
}

/**
 * A frosted glass panel hosting [content]. A thin convenience over [Modifier.glass] for the common
 * case of a floating bar or card: it clips, frosts and frames the [shape], then lays the content
 * inside a [Box] with [contentPadding]. See [Modifier.glass] for the note on backdrop blur.
 *
 * @param tint fill colour; [Color.Unspecified] resolves to the translucent theme surface.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.card,
    tint: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glass(shape = shape, tint = tint).padding(contentPadding),
        content = content,
    )
}
