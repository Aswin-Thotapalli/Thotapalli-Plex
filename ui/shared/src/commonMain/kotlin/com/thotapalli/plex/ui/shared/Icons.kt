package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexTheme

/**
 * The line icons this client draws itself.
 *
 * Compose Multiplatform ships no stable icon pack, and pulling one in for six glyphs would
 * add a dependency for the sake of a back arrow. These are drawn with a stroke on a Canvas
 * instead, so they scale cleanly and take the accent colour on focus like everything else.
 */
enum class PlexIconKind { BACK, HOME, SEARCH, DOWNLOADS, SETTINGS, PLAY, PAUSE, CLOSE, INFO, CHECK, FULLSCREEN, FULLSCREEN_EXIT }

@Composable
fun PlexIcon(
    kind: PlexIconKind,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = PlexTheme.colours.textPrimary,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.083f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (kind) {
            PlexIconKind.BACK -> drawBack(tint, stroke)
            PlexIconKind.HOME -> drawHome(tint, stroke)
            PlexIconKind.SEARCH -> drawSearch(tint, stroke)
            PlexIconKind.DOWNLOADS -> drawDownloads(tint, stroke)
            PlexIconKind.SETTINGS -> drawSettings(tint, stroke)
            PlexIconKind.PLAY -> drawPlay(tint)
            PlexIconKind.PAUSE -> drawPause(tint)
            PlexIconKind.FULLSCREEN -> drawFullscreen(tint, stroke, false)
            PlexIconKind.FULLSCREEN_EXIT -> drawFullscreen(tint, stroke, true)
            PlexIconKind.CLOSE -> drawClose(tint, stroke)
            PlexIconKind.INFO -> drawInfo(tint, stroke)
            PlexIconKind.CHECK -> drawCheck(tint, stroke)
        }
    }
}

private fun DrawScope.drawBack(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A left-pointing chevron with a shaft, sitting on a 24 grid.
    val midY = h * 0.5f
    drawLine(tint, Offset(w * 0.78f, midY), Offset(w * 0.30f, midY), stroke.width, stroke.cap)
    val path = Path().apply {
        moveTo(w * 0.46f, h * 0.30f)
        lineTo(w * 0.28f, midY)
        lineTo(w * 0.46f, h * 0.70f)
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawClose(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), stroke.width, stroke.cap)
}

private fun DrawScope.drawHome(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val roof = Path().apply {
        moveTo(w * 0.18f, h * 0.50f)
        lineTo(w * 0.50f, h * 0.20f)
        lineTo(w * 0.82f, h * 0.50f)
    }
    drawPath(roof, tint, style = stroke)
    val body = Path().apply {
        moveTo(w * 0.27f, h * 0.46f)
        lineTo(w * 0.27f, h * 0.80f)
        lineTo(w * 0.73f, h * 0.80f)
        lineTo(w * 0.73f, h * 0.46f)
    }
    drawPath(body, tint, style = stroke)
}

private fun DrawScope.drawSearch(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val r = w * 0.22f
    drawCircle(tint, r, Offset(w * 0.44f, h * 0.44f), style = stroke)
    drawLine(tint, Offset(w * 0.60f, h * 0.60f), Offset(w * 0.78f, h * 0.78f), stroke.width, stroke.cap)
}

private fun DrawScope.drawDownloads(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.60f), stroke.width, stroke.cap)
    val head = Path().apply {
        moveTo(w * 0.34f, h * 0.46f)
        lineTo(w * 0.50f, h * 0.62f)
        lineTo(w * 0.66f, h * 0.46f)
    }
    drawPath(head, tint, style = stroke)
    drawLine(tint, Offset(w * 0.26f, h * 0.78f), Offset(w * 0.74f, h * 0.78f), stroke.width, stroke.cap)
}

private fun DrawScope.drawSettings(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // Three sliders, which read as settings without the fiddliness of a gear.
    val xs = w * 0.24f
    val xe = w * 0.76f
    val rows = listOf(h * 0.30f, h * 0.50f, h * 0.70f)
    val knobX = listOf(w * 0.60f, w * 0.38f, w * 0.64f)
    rows.forEachIndexed { i, y ->
        drawLine(tint, Offset(xs, y), Offset(xe, y), stroke.width, stroke.cap)
        drawCircle(tint, w * 0.075f, Offset(knobX[i], y), style = stroke)
    }
}

private fun DrawScope.drawInfo(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, w * 0.32f, Offset(w * 0.50f, h * 0.50f), style = stroke)
    // The dot of the i, then its stem.
    drawCircle(tint, w * 0.028f, Offset(w * 0.50f, h * 0.34f))
    drawLine(tint, Offset(w * 0.50f, h * 0.46f), Offset(w * 0.50f, h * 0.66f), stroke.width, stroke.cap)
}

private fun DrawScope.drawCheck(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.26f, h * 0.52f)
        lineTo(w * 0.44f, h * 0.70f)
        lineTo(w * 0.76f, h * 0.32f)
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawFullscreen(tint: Color, stroke: Stroke, exit: Boolean) {
    val w = size.width; val h = size.height
    val sw = stroke.width; val cap = stroke.cap
    // Four corner brackets; pointing inward for exit, outward for enter.
    val a = if (exit) 0.42f else 0.28f   // outer corner
    val b = if (exit) 0.28f else 0.42f   // arm end
    // top-left
    drawLine(tint, Offset(w*a, h*a), Offset(w*a, h*b), sw, cap)
    drawLine(tint, Offset(w*a, h*a), Offset(w*b, h*a), sw, cap)
    // top-right
    drawLine(tint, Offset(w*(1-a), h*a), Offset(w*(1-a), h*b), sw, cap)
    drawLine(tint, Offset(w*(1-a), h*a), Offset(w*(1-b), h*a), sw, cap)
    // bottom-left
    drawLine(tint, Offset(w*a, h*(1-a)), Offset(w*a, h*(1-b)), sw, cap)
    drawLine(tint, Offset(w*a, h*(1-a)), Offset(w*b, h*(1-a)), sw, cap)
    // bottom-right
    drawLine(tint, Offset(w*(1-a), h*(1-a)), Offset(w*(1-a), h*(1-b)), sw, cap)
    drawLine(tint, Offset(w*(1-a), h*(1-a)), Offset(w*(1-b), h*(1-a)), sw, cap)
}

private fun DrawScope.drawPause(tint: Color) {
    val w = size.width
    val h = size.height
    drawRect(tint, Offset(w * 0.36f, h * 0.28f), Size(w * 0.10f, h * 0.44f))
    drawRect(tint, Offset(w * 0.54f, h * 0.28f), Size(w * 0.10f, h * 0.44f))
}

private fun DrawScope.drawPlay(tint: Color) {
    val w = size.width
    val h = size.height
    val triangle = Path().apply {
        moveTo(w * 0.34f, h * 0.26f)
        lineTo(w * 0.34f, h * 0.74f)
        lineTo(w * 0.74f, h * 0.50f)
        close()
    }
    drawPath(triangle, tint)
}
