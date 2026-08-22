package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * The platform liquid-glass material for the shell's navigation.
 *
 * Apple's Liquid Glass (see `brand/Files/Liquid_Glass_Design_Specification.docx`) needs true optical
 * refraction and Fresnel edge lighting, which a pure backdrop blur cannot give. Android has that
 * through Kyant's AGSL backdrop (API 33+); the desktop keeps the Haze-based frosted glass. This
 * expect/actual hides that split behind one contract:
 *
 *  1. [rememberLiquidBackdrop] creates the shared backdrop handle.
 *  2. the content to be refracted is marked with [Modifier.liquidBackdropSource].
 *  3. a floating panel samples it with [Modifier.liquidGlassPanel].
 */
expect class LiquidBackdrop

@Composable
expect fun rememberLiquidBackdrop(): LiquidBackdrop

/** Mark this node as the content the glass refracts/blurs (the featured backdrop behind the shell). */
expect fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop): Modifier

/** Dress this node as a floating sheet of liquid glass over [backdrop]. */
expect fun Modifier.liquidGlassPanel(backdrop: LiquidBackdrop, shape: Shape, tint: Color): Modifier
