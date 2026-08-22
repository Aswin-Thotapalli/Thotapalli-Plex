package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.thotapalli.plex.ui.design.liquidGlass

/**
 * Desktop keeps the Haze-based see-through frosted glass (the ambient backdrop is marked as the
 * Haze source elsewhere with `glassSource`, so the panel below frosts it, blurred and translucent).
 *
 * The Android path (Kyant AGSL) adds true optical refraction and a Fresnel edge; a Skia
 * `RuntimeShader` port of that for desktop needs Compose-Desktop backdrop-capture plumbing that the
 * framework does not expose cleanly, so it is tracked separately. The user accepted that the
 * Windows build may differ slightly from Android.
 */
actual class LiquidBackdrop

@Composable
actual fun rememberLiquidBackdrop(): LiquidBackdrop = LiquidBackdrop()

actual fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop): Modifier = this

actual fun Modifier.liquidGlassPanel(backdrop: LiquidBackdrop, shape: Shape, tint: Color): Modifier =
    this.liquidGlass(shape = shape, elevated = true, tint = tint)
