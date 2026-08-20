package com.thotapalli.plex.ui.shared.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.liquidGlass

/**
 * A frosted, translucent glass material for surfaces that float over content — a nav bar riding a
 * poster grid, the top controls over a hero, a chip over the player.
 *
 * This is a thin, signature-stable adapter over the design layer's [Modifier.liquidGlass]. The real
 * material — the Haze backdrop blur, the specular, the refractive rim, the cool outer glow and the
 * bubbly corner — lives in `ui/design` so every target and every primitive shares one glass. A
 * panel frosts real pixels wherever the screen has published a Haze source
 * ([com.thotapalli.plex.ui.design.glassSource] under a
 * [com.thotapalli.plex.ui.design.GlassScaffold]); with no source it degrades to a tuned translucent
 * fill that still reads as glass on any target.
 */

/**
 * Dress a surface as frosted glass, clipped to [shape]. Kept for the existing call sites; new code
 * can call [Modifier.liquidGlass] directly for the full set of knobs.
 *
 * @param shape the pane outline. Defaults to [Radius.glass], the bubbly continuous corner.
 * @param tint the frost colour. [Color.Unspecified] resolves to the theme glass tint.
 * @param highlight draws the top specular sheen.
 * @param border strokes the refractive rim.
 */
fun Modifier.glass(
    shape: Shape = Radius.glass,
    tint: Color = Color.Unspecified,
    highlight: Boolean = true,
    border: Boolean = true,
): Modifier = this.liquidGlass(
    shape = shape,
    tint = tint,
    specular = highlight,
    rim = border,
)

/**
 * A frosted glass panel hosting [content]. A thin convenience over [Modifier.glass] for the common
 * case of a floating bar or card: it frosts and frames the [shape], then lays the content inside a
 * [Box] with [contentPadding].
 *
 * @param tint fill colour; [Color.Unspecified] resolves to the theme glass tint.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.glass,
    tint: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glass(shape = shape, tint = tint).padding(contentPadding),
        content = content,
    )
}
