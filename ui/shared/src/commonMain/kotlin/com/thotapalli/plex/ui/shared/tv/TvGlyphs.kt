package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The glyphs the ten-foot player and keyboard need beyond [com.thotapalli.plex.ui.shared.PlexIcon]:
 * transport, tracks, keyboard editing. Drawn on a Canvas like the rest of the app's icons, so they
 * scale cleanly and take the focus tint. Compose Multiplatform ships no stable icon pack and one
 * would be a dependency for a dozen strokes.
 */
internal enum class TvGlyph {
    PLAY, PAUSE, SKIP_NEXT, SKIP_PREVIOUS, REPLAY_10, FORWARD_30, SUBTITLES, AUDIO,
    BACKSPACE, SPACE, MIC, CHECK, CHEVRON_RIGHT, CHEVRON_DOWN, MORE, CLOSE, GRID, BACK,
}

@Composable
internal fun TvGlyphIcon(
    glyph: TvGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            TvGlyph.PLAY -> play(tint)
            TvGlyph.PAUSE -> pause(tint)
            TvGlyph.SKIP_NEXT -> skip(tint, forward = true)
            TvGlyph.SKIP_PREVIOUS -> skip(tint, forward = false)
            TvGlyph.REPLAY_10 -> arc(tint, stroke, clockwise = false)
            TvGlyph.FORWARD_30 -> arc(tint, stroke, clockwise = true)
            TvGlyph.SUBTITLES -> subtitles(tint, stroke)
            TvGlyph.AUDIO -> audio(tint, stroke)
            TvGlyph.BACKSPACE -> backspace(tint, stroke)
            TvGlyph.SPACE -> space(tint, stroke)
            TvGlyph.MIC -> mic(tint, stroke)
            TvGlyph.CHECK -> check(tint, stroke)
            TvGlyph.CHEVRON_RIGHT -> chevron(tint, stroke, down = false)
            TvGlyph.CHEVRON_DOWN -> chevron(tint, stroke, down = true)
            TvGlyph.MORE -> more(tint)
            TvGlyph.CLOSE -> close(tint, stroke)
            TvGlyph.GRID -> grid(tint)
            TvGlyph.BACK -> back(tint, stroke)
        }
    }
}

private fun DrawScope.play(tint: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.30f, h * 0.20f)
        lineTo(w * 0.82f, h * 0.50f)
        lineTo(w * 0.30f, h * 0.80f)
        close()
    }
    drawPath(path, tint)
}

private fun DrawScope.pause(tint: Color) {
    val w = size.width
    val h = size.height
    val barW = w * 0.18f
    drawRect(tint, Offset(w * 0.24f, h * 0.20f), Size(barW, h * 0.60f))
    drawRect(tint, Offset(w * 0.58f, h * 0.20f), Size(barW, h * 0.60f))
}

private fun DrawScope.skip(tint: Color, forward: Boolean) {
    val w = size.width
    val h = size.height
    val tri = Path().apply {
        if (forward) {
            moveTo(w * 0.22f, h * 0.22f); lineTo(w * 0.66f, h * 0.50f); lineTo(w * 0.22f, h * 0.78f)
        } else {
            moveTo(w * 0.78f, h * 0.22f); lineTo(w * 0.34f, h * 0.50f); lineTo(w * 0.78f, h * 0.78f)
        }
        close()
    }
    drawPath(tri, tint)
    val barX = if (forward) w * 0.70f else w * 0.20f
    drawRect(tint, Offset(barX, h * 0.22f), Size(w * 0.10f, h * 0.56f))
}

/** A circular arrow with a small "10"/"30" implied by the caller's label; the arc alone here. */
private fun DrawScope.arc(tint: Color, stroke: Stroke, clockwise: Boolean) {
    val w = size.width
    val h = size.height
    val rect = androidx.compose.ui.geometry.Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
    val path = Path().apply {
        // Three quarters of a circle, open at the top, with the arrow head at the open end.
        if (clockwise) {
            addArc(rect, -60f, 300f)
        } else {
            addArc(rect, -120f, -300f)
        }
    }
    drawPath(path, tint, style = stroke)
    val angle = (if (clockwise) -60.0 else -120.0) * kotlin.math.PI / 180.0
    val headX = w * 0.50f + w * 0.32f * kotlin.math.cos(angle).toFloat()
    val headY = h * 0.50f + h * 0.32f * kotlin.math.sin(angle).toFloat()
    val d = w * 0.14f
    val head = Path().apply {
        if (clockwise) {
            moveTo(headX - d, headY - d * 0.2f); lineTo(headX, headY); lineTo(headX - d * 0.2f, headY + d)
        } else {
            moveTo(headX + d, headY - d * 0.2f); lineTo(headX, headY); lineTo(headX + d * 0.2f, headY + d)
        }
    }
    drawPath(head, tint, style = stroke)
}

private fun DrawScope.subtitles(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint, Offset(w * 0.12f, h * 0.22f), Size(w * 0.76f, h * 0.56f),
        androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = stroke,
    )
    drawLine(tint, Offset(w * 0.26f, h * 0.56f), Offset(w * 0.52f, h * 0.56f), stroke.width, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.58f, h * 0.56f), Offset(w * 0.74f, h * 0.56f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.audio(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val speaker = Path().apply {
        moveTo(w * 0.16f, h * 0.40f); lineTo(w * 0.30f, h * 0.40f); lineTo(w * 0.46f, h * 0.26f)
        lineTo(w * 0.46f, h * 0.74f); lineTo(w * 0.30f, h * 0.60f); lineTo(w * 0.16f, h * 0.60f); close()
    }
    drawPath(speaker, tint)
    val r1 = androidx.compose.ui.geometry.Rect(w * 0.40f, h * 0.34f, w * 0.72f, h * 0.66f)
    val r2 = androidx.compose.ui.geometry.Rect(w * 0.34f, h * 0.20f, w * 0.90f, h * 0.80f)
    drawPath(Path().apply { addArc(r1, -50f, 100f) }, tint, style = stroke)
    drawPath(Path().apply { addArc(r2, -50f, 100f) }, tint, style = stroke)
}

private fun DrawScope.backspace(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.34f, h * 0.26f); lineTo(w * 0.86f, h * 0.26f); lineTo(w * 0.86f, h * 0.74f)
        lineTo(w * 0.34f, h * 0.74f); lineTo(w * 0.12f, h * 0.50f); close()
    }
    drawPath(body, tint, style = stroke)
    drawLine(tint, Offset(w * 0.50f, h * 0.40f), Offset(w * 0.70f, h * 0.60f), stroke.width, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.70f, h * 0.40f), Offset(w * 0.50f, h * 0.60f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.space(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.16f, h * 0.44f); lineTo(w * 0.16f, h * 0.64f); lineTo(w * 0.84f, h * 0.64f); lineTo(w * 0.84f, h * 0.44f)
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.mic(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint, Offset(w * 0.38f, h * 0.14f), Size(w * 0.24f, h * 0.44f),
        androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
    )
    val bowl = androidx.compose.ui.geometry.Rect(w * 0.26f, h * 0.30f, w * 0.74f, h * 0.70f)
    drawPath(Path().apply { addArc(bowl, 0f, 180f) }, tint, style = stroke)
    drawLine(tint, Offset(w * 0.50f, h * 0.70f), Offset(w * 0.50f, h * 0.86f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.check(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.22f, h * 0.52f); lineTo(w * 0.42f, h * 0.72f); lineTo(w * 0.78f, h * 0.30f)
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.chevron(tint: Color, stroke: Stroke, down: Boolean) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        if (down) {
            moveTo(w * 0.28f, h * 0.40f); lineTo(w * 0.50f, h * 0.62f); lineTo(w * 0.72f, h * 0.40f)
        } else {
            moveTo(w * 0.40f, h * 0.28f); lineTo(w * 0.62f, h * 0.50f); lineTo(w * 0.40f, h * 0.72f)
        }
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.more(tint: Color) {
    val w = size.width
    val h = size.height
    val r = w * 0.07f
    listOf(0.26f, 0.50f, 0.74f).forEach { fx -> drawCircle(tint, r, Offset(w * fx, h * 0.5f)) }
}

private fun DrawScope.close(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), stroke.width, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.grid(tint: Color) {
    val w = size.width
    val h = size.height
    val cell = Size(w * 0.34f, h * 0.34f)
    val r = androidx.compose.ui.geometry.CornerRadius(w * 0.07f)
    drawRoundRect(tint, Offset(w * 0.12f, h * 0.12f), cell, r)
    drawRoundRect(tint, Offset(w * 0.54f, h * 0.12f), cell, r)
    drawRoundRect(tint, Offset(w * 0.12f, h * 0.54f), cell, r)
    drawRoundRect(tint, Offset(w * 0.54f, h * 0.54f), cell, r)
}

private fun DrawScope.back(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val midY = h * 0.5f
    drawLine(tint, Offset(w * 0.78f, midY), Offset(w * 0.30f, midY), stroke.width, StrokeCap.Round)
    val path = Path().apply {
        moveTo(w * 0.46f, h * 0.30f); lineTo(w * 0.28f, midY); lineTo(w * 0.46f, h * 0.70f)
    }
    drawPath(path, tint, style = stroke)
}
