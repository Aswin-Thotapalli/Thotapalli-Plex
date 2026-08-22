package com.thotapalli.plex.ui.shared

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/**
 * Android liquid glass via Kyant's AGSL backdrop — real optical refraction (lens) and Fresnel edge
 * lighting, per the Liquid Glass spec. Requires API 33+ for runtime shaders; below that it degrades
 * to a translucent tinted fill so it never crashes.
 */
actual class LiquidBackdrop internal constructor(internal val layer: LayerBackdrop?)

@Composable
actual fun rememberLiquidBackdrop(): LiquidBackdrop =
    if (Build.VERSION.SDK_INT >= 33) {
        LiquidBackdrop(rememberLayerBackdrop(rememberGraphicsLayer()))
    } else {
        LiquidBackdrop(null)
    }

actual fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop): Modifier =
    backdrop.layer?.let { this.layerBackdrop(it) } ?: this

actual fun Modifier.liquidGlassPanel(backdrop: LiquidBackdrop, shape: Shape, tint: Color): Modifier {
    val layer = backdrop.layer ?: return this.clip(shape).background(tint)
    return this.drawBackdrop(
        backdrop = layer,
        shape = { shape },
        effects = {
            blur(24f)
            lens(24f, 48f)
        },
    )
}
