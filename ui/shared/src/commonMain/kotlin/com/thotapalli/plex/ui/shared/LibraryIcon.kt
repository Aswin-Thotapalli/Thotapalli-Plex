package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thotapalli.plex.core.model.LibraryKind

/**
 * The curated glyph a library is drawn with. Each is a clean, single-weight line/solid mark hand-drawn
 * on a Canvas to match the rest of the icon set (see [PlexIcon]), so a library reads at a glance from a
 * relevant picture rather than a generic film-reel-or-TV pair.
 */
private enum class LibraryGlyph {
    CLAPPERBOARD,   // movies / film / cinema — and the MOVIE-kind fallback
    ANIME,          // anime / cartoon / animated — a clapper crowned with a sparkle
    KIDS,           // kids / family / children — a balloon on a string
    DOCUMENTARY,    // documentary / docs — a globe
    TELEVISION,     // tv / show / series — and the SHOW-kind fallback
    ULTRA_HD,       // 4k / uhd — a widescreen with a play triangle
    SPORT,          // sport(s) — a trophy cup
    COMEDY,         // stand-up / comedy — a microphone
    MUSIC_VIDEO,    // concert / music video — an eighth note
    HOME,           // home / personal — a house
    HOLIDAY,        // holiday / christmas — a star-topped tree
    STACK,          // the collection/other fallback — a layered stack
}

/**
 * Picks a good-looking, relevant icon for a library automatically from its [name], falling back to the
 * library [kind] when no keyword matches. Draws the chosen glyph tinted by [tint], filling the given
 * [modifier] (the caller supplies the size). Single-weight strokes with rounded caps, matching the
 * app's hand-drawn icon set.
 *
 * The signature is a shared contract — other screens (the sidebar, the chooser cards, the tiles) all
 * call this exact form — so keep it stable.
 */
@Composable
fun LibraryAutoIcon(
    name: String,
    kind: LibraryKind,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val glyph = glyphFor(name, kind)
    Canvas(modifier) {
        val sw = (size.minDimension * 0.085f).coerceAtLeast(1f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            LibraryGlyph.CLAPPERBOARD -> drawClapperboard(tint, stroke)
            LibraryGlyph.ANIME -> {
                drawClapperboard(tint, stroke)
                drawSparkle(tint, stroke, cx = size.width * 0.82f, cy = size.height * 0.20f, r = size.width * 0.14f)
            }
            LibraryGlyph.KIDS -> drawBalloon(tint, stroke)
            LibraryGlyph.DOCUMENTARY -> drawGlobe(tint, stroke)
            LibraryGlyph.TELEVISION -> drawTelevision(tint, stroke)
            LibraryGlyph.ULTRA_HD -> drawUltraHd(tint, stroke)
            LibraryGlyph.SPORT -> drawTrophy(tint, stroke)
            LibraryGlyph.COMEDY -> drawMicrophone(tint, stroke)
            LibraryGlyph.MUSIC_VIDEO -> drawNote(tint, stroke)
            LibraryGlyph.HOME -> drawHouse(tint, stroke)
            LibraryGlyph.HOLIDAY -> drawTree(tint, stroke)
            LibraryGlyph.STACK -> drawStack(tint, stroke)
        }
    }
}

/**
 * Maps the lowercased library name to a curated glyph, most-specific keyword first, and falls back to
 * the library kind (MOVIE → clapperboard, SHOW → television, otherwise a stack).
 */
private fun glyphFor(name: String, kind: LibraryKind): LibraryGlyph {
    val n = name.lowercase()
    fun has(vararg keys: String) = keys.any { it in n }
    return when {
        has("4k", "uhd", "2160", "ultra hd", "ultrahd") -> LibraryGlyph.ULTRA_HD
        has("anime", "cartoon", "animated", "animation", "toon") -> LibraryGlyph.ANIME
        has("kid", "family", "children", "child", "toddler") -> LibraryGlyph.KIDS
        has("documentar", "docs", "docu", "nature") -> LibraryGlyph.DOCUMENTARY
        has("sport", "football", "soccer", "basketball", "wrestl") -> LibraryGlyph.SPORT
        has("stand-up", "stand up", "standup", "comedy", "comedian") -> LibraryGlyph.COMEDY
        has("concert", "music video", "music-video", "musicvideo", "live music") -> LibraryGlyph.MUSIC_VIDEO
        has("holiday", "christmas", "xmas", "festive") -> LibraryGlyph.HOLIDAY
        has("home video", "home movie", "personal", "family videos") -> LibraryGlyph.HOME
        has("movie", "film", "cinema", "flick", "feature") -> LibraryGlyph.CLAPPERBOARD
        has("tv", "show", "series", "television", "episode") -> LibraryGlyph.TELEVISION
        else -> when (kind) {
            LibraryKind.MOVIE -> LibraryGlyph.CLAPPERBOARD
            LibraryKind.SHOW -> LibraryGlyph.TELEVISION
            LibraryKind.UNSUPPORTED -> LibraryGlyph.STACK
        }
    }
}

// --- Glyphs. Coordinates are fractions of the canvas, so every mark scales with the given size. ---

/** A clapperboard: a body with a hinged, striped clapper strip across the top. */
private fun DrawScope.drawClapperboard(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.14f, h * 0.28f),
        size = Size(w * 0.72f, h * 0.54f),
        cornerRadius = CornerRadius(stroke.width, stroke.width),
        style = stroke,
    )
    // The strip that separates the clapper teeth from the board.
    drawLine(tint, Offset(w * 0.14f, h * 0.47f), Offset(w * 0.86f, h * 0.47f), stroke.width, stroke.cap)
    // Diagonal teeth in the top strip.
    listOf(0.28f, 0.44f, 0.60f, 0.76f).forEach { fx ->
        drawLine(
            tint,
            Offset(w * fx, h * 0.30f),
            Offset(w * (fx - 0.08f), h * 0.47f),
            stroke.width,
            stroke.cap,
        )
    }
}

/** A four-point sparkle centred on (cx, cy), reach [r], drawn as two tapered crossing strokes. */
private fun DrawScope.drawSparkle(tint: Color, stroke: Stroke, cx: Float, cy: Float, r: Float) {
    drawLine(tint, Offset(cx, cy - r), Offset(cx, cy + r), stroke.width, stroke.cap)
    drawLine(tint, Offset(cx - r, cy), Offset(cx + r, cy), stroke.width, stroke.cap)
}

/** A balloon: a round body with a knot and a curling string. */
private fun DrawScope.drawBalloon(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, w * 0.24f, Offset(w * 0.5f, h * 0.36f), style = stroke)
    // The knot at the balloon's base.
    val knot = Path().apply {
        moveTo(w * 0.45f, h * 0.60f)
        lineTo(w * 0.5f, h * 0.66f)
        lineTo(w * 0.55f, h * 0.60f)
    }
    drawPath(knot, tint, style = stroke)
    // A curling string.
    val string = Path().apply {
        moveTo(w * 0.5f, h * 0.66f)
        cubicTo(w * 0.62f, h * 0.74f, w * 0.40f, h * 0.80f, w * 0.52f, h * 0.88f)
    }
    drawPath(string, tint, style = stroke)
}

/** A globe: a circle with an equator and a meridian oval. */
private fun DrawScope.drawGlobe(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val r = w * 0.34f
    val c = Offset(w * 0.5f, h * 0.5f)
    drawCircle(tint, r, c, style = stroke)
    drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), stroke.width, stroke.cap)
    drawOval(
        color = tint,
        topLeft = Offset(w * 0.5f - w * 0.15f, c.y - r),
        size = Size(w * 0.30f, r * 2f),
        style = stroke,
    )
}

/** A television: a rounded screen over two splayed legs. */
private fun DrawScope.drawTelevision(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.12f, h * 0.22f),
        size = Size(w * 0.76f, h * 0.48f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.38f, h * 0.70f), Offset(w * 0.30f, h * 0.84f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.62f, h * 0.70f), Offset(w * 0.70f, h * 0.84f), stroke.width, stroke.cap)
}

/** A widescreen with a play triangle inside — a crisp high-definition frame for 4K/UHD. */
private fun DrawScope.drawUltraHd(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.10f, h * 0.26f),
        size = Size(w * 0.80f, h * 0.48f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = stroke,
    )
    val triangle = Path().apply {
        moveTo(w * 0.43f, h * 0.40f)
        lineTo(w * 0.43f, h * 0.60f)
        lineTo(w * 0.60f, h * 0.50f)
        close()
    }
    drawPath(triangle, tint)
}

/** A trophy: a cup with two handles over a stem and base. */
private fun DrawScope.drawTrophy(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val cup = Path().apply {
        moveTo(w * 0.34f, h * 0.22f)
        lineTo(w * 0.66f, h * 0.22f)
        lineTo(w * 0.60f, h * 0.50f)
        lineTo(w * 0.40f, h * 0.50f)
        close()
    }
    drawPath(cup, tint, style = stroke)
    // Handles, an arc each side.
    drawArc(tint, startAngle = 270f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.26f, h * 0.24f), size = Size(w * 0.12f, h * 0.16f), style = stroke)
    drawArc(tint, startAngle = 90f, sweepAngle = -180f, useCenter = false,
        topLeft = Offset(w * 0.62f, h * 0.24f), size = Size(w * 0.12f, h * 0.16f), style = stroke)
    // Stem and base.
    drawLine(tint, Offset(w * 0.5f, h * 0.50f), Offset(w * 0.5f, h * 0.68f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.36f, h * 0.80f), Offset(w * 0.64f, h * 0.80f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.44f, h * 0.68f), Offset(w * 0.56f, h * 0.68f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.5f, h * 0.68f), Offset(w * 0.5f, h * 0.80f), stroke.width, stroke.cap)
}

/** A microphone: a capsule head in a cradle over a stand. */
private fun DrawScope.drawMicrophone(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // The capsule head (full-radius rounded rect).
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.40f, h * 0.16f),
        size = Size(w * 0.20f, h * 0.36f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
        style = stroke,
    )
    // The cradle: a U-shaped arc beneath the head.
    drawArc(tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.30f, h * 0.32f), size = Size(w * 0.40f, h * 0.34f), style = stroke)
    // Stem and base.
    drawLine(tint, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.82f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.38f, h * 0.84f), Offset(w * 0.62f, h * 0.84f), stroke.width, stroke.cap)
}

/** An eighth note: a filled head with a stem and a flag. */
private fun DrawScope.drawNote(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // The note head.
    drawOval(tint, topLeft = Offset(w * 0.26f, h * 0.62f), size = Size(w * 0.22f, h * 0.18f))
    // The stem.
    drawLine(tint, Offset(w * 0.46f, h * 0.71f), Offset(w * 0.46f, h * 0.24f), stroke.width, stroke.cap)
    // The flag.
    val flag = Path().apply {
        moveTo(w * 0.46f, h * 0.24f)
        cubicTo(w * 0.60f, h * 0.28f, w * 0.66f, h * 0.36f, w * 0.62f, h * 0.46f)
    }
    drawPath(flag, tint, style = stroke)
}

/** A house: a roof over a body. Mirrors the app's home glyph. */
private fun DrawScope.drawHouse(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val roof = Path().apply {
        moveTo(w * 0.16f, h * 0.50f)
        lineTo(w * 0.50f, h * 0.20f)
        lineTo(w * 0.84f, h * 0.50f)
    }
    drawPath(roof, tint, style = stroke)
    val body = Path().apply {
        moveTo(w * 0.26f, h * 0.46f)
        lineTo(w * 0.26f, h * 0.80f)
        lineTo(w * 0.74f, h * 0.80f)
        lineTo(w * 0.74f, h * 0.46f)
    }
    drawPath(body, tint, style = stroke)
}

/** A christmas tree: two triangular tiers on a trunk, crowned by a small sparkle. */
private fun DrawScope.drawTree(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawSparkle(tint, stroke, cx = w * 0.5f, cy = h * 0.14f, r = w * 0.09f)
    val top = Path().apply {
        moveTo(w * 0.5f, h * 0.24f)
        lineTo(w * 0.32f, h * 0.52f)
        lineTo(w * 0.68f, h * 0.52f)
        close()
    }
    drawPath(top, tint, style = stroke)
    val bottom = Path().apply {
        moveTo(w * 0.5f, h * 0.42f)
        lineTo(w * 0.26f, h * 0.74f)
        lineTo(w * 0.74f, h * 0.74f)
        close()
    }
    drawPath(bottom, tint, style = stroke)
    // The trunk.
    drawLine(tint, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.84f), stroke.width, stroke.cap)
}

/** A layered stack: a top diamond over two chevron layers — the collection/other fallback. */
private fun DrawScope.drawStack(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val diamond = Path().apply {
        moveTo(w * 0.5f, h * 0.18f)
        lineTo(w * 0.80f, h * 0.36f)
        lineTo(w * 0.5f, h * 0.54f)
        lineTo(w * 0.20f, h * 0.36f)
        close()
    }
    drawPath(diamond, tint, style = stroke)
    val mid = Path().apply {
        moveTo(w * 0.20f, h * 0.52f)
        lineTo(w * 0.5f, h * 0.68f)
        lineTo(w * 0.80f, h * 0.52f)
    }
    drawPath(mid, tint, style = stroke)
    val low = Path().apply {
        moveTo(w * 0.20f, h * 0.64f)
        lineTo(w * 0.5f, h * 0.80f)
        lineTo(w * 0.80f, h * 0.64f)
    }
    drawPath(low, tint, style = stroke)
}
