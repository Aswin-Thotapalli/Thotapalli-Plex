package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.thotapalli.plex.ui.design.liquidGlass

/**
 * Desktop keeps the Haze-based frosted glass (the ambient backdrop is marked as the Haze source
 * elsewhere with `glassSource`, so the panel below frosts it). A Skia `RuntimeShader` refraction
 * port is the next step for parity with the Android/Kyant path.
 */
actual class LiquidBackdrop

@Composable
actual fun rememberLiquidBackdrop(): LiquidBackdrop = LiquidBackdrop()

actual fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop): Modifier = this

actual fun Modifier.liquidGlassPanel(backdrop: LiquidBackdrop, shape: Shape, tint: Color): Modifier =
    this.liquidGlass(shape = shape, elevated = true, tint = tint)
