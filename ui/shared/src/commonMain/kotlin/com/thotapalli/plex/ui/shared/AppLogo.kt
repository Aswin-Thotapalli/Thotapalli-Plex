package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Thotapalli Plex brand mark from CLAUDE.md section 15 and `brand/logo.svg`, drawn with
 * a Canvas so it is resolution independent and needs no bundled raster.
 *
 * A rounded plate in the plate gradient carries a horizontal bar above a play triangle in
 * the amber mark gradient. The two forms read at once as the letter T and as a play control.
 * The geometry is the SVG's 512 grid, expressed as fractions of the drawn size, so the mark
 * is pixel-for-pixel the source artwork at any dimension.
 */
@Composable
fun AppLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawAppLogo() }
}

// Section 15 permits two colours only: the plate gradient and the amber mark gradient.
private val PlateTop = Color(0xFF262A30)
private val PlateBottom = Color(0xFF101215)
private val AmberLight = Color(0xFFFFCE63)
private val AmberMid = Color(0xFFF5A623)
private val AmberDeep = Color(0xFFD4820C)

private fun DrawScope.drawAppLogo() {
    val s = size.minDimension
    fun px(v: Float) = v / 512f * s

    // The plate: rect 16,16 480x480 rx112, gradient top toward lower-left.
    val plate = Brush.linearGradient(
        colors = listOf(PlateTop, PlateBottom),
        start = Offset(0f, 0f),
        end = Offset(px(180f), px(512f)),
    )
    drawRoundRect(
        brush = plate,
        topLeft = Offset(px(16f), px(16f)),
        size = Size(px(480f), px(480f)),
        cornerRadius = CornerRadius(px(112f), px(112f)),
    )

    // The amber gradient runs across the mark's bounding box (120..392, 140..396).
    val amber = Brush.linearGradient(
        colors = listOf(AmberLight, AmberMid, AmberDeep),
        start = Offset(px(120f), px(140f)),
        end = Offset(px(392f), px(396f)),
    )

    // The bar: rect 120,140 272x54 rx27.
    drawRoundRect(
        brush = amber,
        topLeft = Offset(px(120f), px(140f)),
        size = Size(px(272f), px(54f)),
        cornerRadius = CornerRadius(px(27f), px(27f)),
    )

    // The play triangle, with the SVG's softened tips.
    val triangle = Path().apply {
        moveTo(px(214f), px(224f))
        lineTo(px(214f), px(388f))
        quadraticTo(px(214f), px(400f), px(225f), px(394f))
        lineTo(px(378f), px(312f))
        quadraticTo(px(389f), px(306f), px(378f), px(300f))
        lineTo(px(225f), px(218f))
        quadraticTo(px(214f), px(212f), px(214f), px(224f))
        close()
    }
    drawPath(triangle, amber)
}
