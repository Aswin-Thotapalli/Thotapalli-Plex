package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.liquidGlass
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Desktop liquid glass as a real Skia [RuntimeEffect] (SkSL) — the Liquid Glass optics Haze cannot
 * give: edge **refraction**, variable **blur**, a **Fresnel** rim and a soft specular.
 *
 * The featured backdrop is captured into a [GraphicsLayer] by [liquidBackdropSource] (the same
 * technique Haze/Kyant use). A panel then re-draws the region of that layer behind it into its own
 * layer whose [GraphicsLayer.renderEffect] is the refraction shader, applied via
 * [asComposeRenderEffect]. If the shader can't compile on a machine it degrades to the Haze frost.
 */
actual class LiquidBackdrop internal constructor(
    internal val layer: GraphicsLayer,
)

@Composable
actual fun rememberLiquidBackdrop(): LiquidBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidBackdrop(layer) }
}

actual fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop): Modifier =
    this.drawWithContent {
        backdrop.layer.record(
            this,
            layoutDirection,
            IntSize(size.width.toInt(), size.height.toInt()),
        ) { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

private val LIQUID_EFFECT: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(LIQUID_SKSL) }.getOrNull()
}

actual fun Modifier.liquidGlassPanel(
    backdrop: LiquidBackdrop,
    shape: Shape,
    tint: Color,
): Modifier = composed {
    val effect = LIQUID_EFFECT ?: return@composed this.liquidGlass(shape = shape, elevated = true, tint = tint)
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val cornerPx = with(density) { 28.dp.toPx() }
    val blurPx = with(density) { 5.dp.toPx() }
    val refractPx = with(density) { 16.dp.toPx() }

    var origin by remember { mutableStateOf(Offset.Zero) }

    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithCache {
            val panelSize = size
            val glass = obtainGraphicsLayer()
            val builder = RuntimeShaderBuilder(effect).apply {
                uniform("uSize", panelSize.width, panelSize.height)
                uniform("uRadius", cornerPx)
                uniform("uBlur", blurPx)
                uniform("uRefract", refractPx)
                uniform("uTint", tint.red, tint.green, tint.blue, tint.alpha)
            }
            val filter = ImageFilter.makeRuntimeShader(builder, "content", null)
            glass.renderEffect = filter.asComposeRenderEffect()
            // Record the region of the captured backdrop that sits behind this panel, by drawing
            // the whole backdrop layer shifted up-left by this panel's window position.
            glass.record(
                this,
                layoutDir,
                IntSize(panelSize.width.toInt(), panelSize.height.toInt()),
            ) {
                translate(-origin.x, -origin.y) {
                    drawLayer(backdrop.layer)
                }
            }
            onDrawBehind {
                drawLayer(glass)
            }
        }
}

// SkSL: rounded-rect SDF drives edge refraction (sample displaced outward near the edge — optical
// magnification), a 9-tap blur, a Fresnel rim just inside the edge and a soft top-left specular;
// the glass tint is mixed over the refracted backdrop.
private const val LIQUID_SKSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uBlur;
uniform float uRefract;
uniform float4 uTint;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 blur9(float2 s, float o) {
    half4 c = half4(0.0);
    c += content.eval(s);
    c += content.eval(s + float2(o, 0.0));
    c += content.eval(s + float2(-o, 0.0));
    c += content.eval(s + float2(0.0, o));
    c += content.eval(s + float2(0.0, -o));
    c += content.eval(s + float2(o, o));
    c += content.eval(s + float2(-o, o));
    c += content.eval(s + float2(o, -o));
    c += content.eval(s + float2(-o, -o));
    return c / 9.0;
}

half4 main(float2 coord) {
    float2 halfSize = uSize * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, uRadius);
    float inside = -d;
    float e = clamp(1.0 - inside / 26.0, 0.0, 1.0);

    float2 dir = normalize(p + float2(0.001, 0.001));
    float2 s = coord + dir * (e * e) * uRefract;

    half4 col = blur9(s, uBlur);

    float rim = smoothstep(0.0, 5.0, inside) * (1.0 - smoothstep(5.0, 13.0, inside));
    col.rgb += half3(rim) * 0.5;

    float spec = clamp(1.0 - (coord.x + coord.y) / (uSize.x + uSize.y), 0.0, 1.0);
    col.rgb += half3(spec) * 0.05;

    col.rgb = mix(col.rgb, half3(uTint.rgb), half(uTint.a));
    return col;
}
"""
