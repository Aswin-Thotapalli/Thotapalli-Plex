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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thotapalli.plex.core.model.LibraryKind

/**
 * The curated glyph a library is drawn with. Each is a bold, filled silhouette (not a thin outline)
 * so it reads instantly at the small sizes the sidebar and chooser cards use, and so two libraries
 * never look like the same faint mark. See [LibraryAutoIcon].
 */
private enum class LibraryGlyph {
    CLAPPERBOARD,   // movies / film / cinema — and the MOVIE-kind fallback
    ANIME,          // anime / cartoon / animated — a screen crowned with a sparkle
    KIDS,           // kids / family / children — a balloon
    DOCUMENTARY,    // documentary / docs / nature — a globe
    TELEVISION,     // tv / show / series — and the SHOW-kind fallback
    ULTRA_HD,       // 4k / uhd — a widescreen with a play triangle
    SPORT,          // sport(s) — a trophy cup
    COMEDY,         // stand-up / comedy — a microphone
    MUSIC_VIDEO,    // concert / music video — an eighth note
    HOME,           // home / personal — a house
    HOLIDAY,        // holiday / christmas — a tree
    STACK,          // the collection/other fallback — a layered stack
}

/**
 * Picks a good-looking, relevant icon for a library automatically from its [name], falling back to the
 * library [kind] when no keyword matches. Draws the chosen glyph as a solid mark tinted by [tint],
 * filling the given [modifier] (the caller supplies the size).
 *
 * The signature is a shared contract — the sidebar, the chooser cards and the tiles all call this
 * exact form — so keep it stable.
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
        when (glyph) {
            LibraryGlyph.CLAPPERBOARD -> drawClapperboard(tint)
            LibraryGlyph.ANIME -> drawAnime(tint)
            LibraryGlyph.KIDS -> drawBalloon(tint)
            LibraryGlyph.DOCUMENTARY -> drawGlobe(tint)
            LibraryGlyph.TELEVISION -> drawTelevision(tint)
            LibraryGlyph.ULTRA_HD -> drawUltraHd(tint)
            LibraryGlyph.SPORT -> drawTrophy(tint)
            LibraryGlyph.COMEDY -> drawMicrophone(tint)
            LibraryGlyph.MUSIC_VIDEO -> drawNote(tint)
            LibraryGlyph.HOME -> drawHouse(tint)
            LibraryGlyph.HOLIDAY -> drawTree(tint)
            LibraryGlyph.STACK -> drawStack(tint)
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

// --- Glyphs. Bold filled silhouettes; coordinates are fractions of the canvas so every mark scales
//     with the given size. Small details (a balloon string, a trophy stem) are drawn as thick round
//     strokes so they still read at 18–22dp. ---

/** A clapperboard: a solid body under an angled hinged clapper with cut teeth. */
private fun DrawScope.drawClapperboard(tint: Color) {
    val w = size.width
    val h = size.height
    // The board body.
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.13f, h * 0.45f),
        size = Size(w * 0.74f, h * 0.38f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
    )
    // The angled clapper strip on top, with three triangular teeth notched out of its lower edge.
    val clapper = Path().apply {
        moveTo(w * 0.13f, h * 0.31f)
        lineTo(w * 0.87f, h * 0.23f)
        lineTo(w * 0.87f, h * 0.37f)
        // zigzag teeth back to the left
        lineTo(w * 0.72f, h * 0.395f)
        lineTo(w * 0.66f, h * 0.335f)
        lineTo(w * 0.53f, h * 0.415f)
        lineTo(w * 0.47f, h * 0.355f)
        lineTo(w * 0.34f, h * 0.435f)
        lineTo(w * 0.28f, h * 0.375f)
        lineTo(w * 0.13f, h * 0.45f)
        close()
    }
    drawPath(clapper, tint)
}

/** A television screen over two splayed legs — the SHOW mark. */
private fun DrawScope.drawTelevision(tint: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.12f, h * 0.26f),
        size = Size(w * 0.76f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
    )
    // Two short legs.
    drawPath(
        Path().apply {
            moveTo(w * 0.32f, h * 0.70f); lineTo(w * 0.26f, h * 0.82f)
            lineTo(w * 0.38f, h * 0.72f); close()
        },
        tint,
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.68f, h * 0.70f); lineTo(w * 0.74f, h * 0.82f)
            lineTo(w * 0.62f, h * 0.72f); close()
        },
        tint,
    )
}

/** A screen crowned with a four-point sparkle — animated / anime. */
private fun DrawScope.drawAnime(tint: Color) {
    drawTelevision(tint)
    drawSparkle(tint, cx = size.width * 0.83f, cy = size.height * 0.19f, r = size.width * 0.15f)
}

/** A solid four-point sparkle centred on (cx, cy) with the given reach [r]. */
private fun DrawScope.drawSparkle(tint: Color, cx: Float, cy: Float, r: Float) {
    val waist = r * 0.30f
    drawPath(
        Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + waist, cy - waist); lineTo(cx + r, cy)
            lineTo(cx + waist, cy + waist); lineTo(cx, cy + r)
            lineTo(cx - waist, cy + waist); lineTo(cx - r, cy)
            lineTo(cx - waist, cy - waist); close()
        },
        tint,
    )
}

/** A balloon: a solid round body, a knot and a curling string. */
private fun DrawScope.drawBalloon(tint: Color) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.23f, center = Offset(w * 0.5f, h * 0.35f))
    drawPath(
        Path().apply {
            moveTo(w * 0.45f, h * 0.56f); lineTo(w * 0.5f, h * 0.62f)
            lineTo(w * 0.55f, h * 0.56f); close()
        },
        tint,
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, h * 0.62f)
            cubicTo(w * 0.62f, h * 0.72f, w * 0.40f, h * 0.79f, w * 0.52f, h * 0.88f)
        },
        tint,
        style = Stroke(width = w * 0.035f, cap = StrokeCap.Round),
    )
}

/** A globe: a solid disc with a lighter equator and meridian carved by thick strokes. */
private fun DrawScope.drawGlobe(tint: Color) {
    val w = size.width
    val h = size.height
    val r = w * 0.34f
    val c = Offset(w * 0.5f, h * 0.5f)
    // A ring rather than a filled disc reads clearly as a globe at small sizes.
    drawCircle(tint, radius = r, center = c, style = Stroke(width = w * 0.11f))
    drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    drawOval(
        color = tint,
        topLeft = Offset(w * 0.5f - w * 0.14f, c.y - r),
        size = Size(w * 0.28f, r * 2f),
        style = Stroke(width = w * 0.09f),
    )
}

/** A widescreen frame with a solid play triangle — 4K / UHD. */
private fun DrawScope.drawUltraHd(tint: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.10f, h * 0.28f),
        size = Size(w * 0.80f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
        style = Stroke(width = w * 0.10f),
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.43f, h * 0.39f); lineTo(w * 0.43f, h * 0.61f)
            lineTo(w * 0.61f, h * 0.50f); close()
        },
        tint,
    )
}

/** A trophy: a solid cup with two handles over a stem and base. */
private fun DrawScope.drawTrophy(tint: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.33f, h * 0.20f); lineTo(w * 0.67f, h * 0.20f)
            lineTo(w * 0.60f, h * 0.52f); lineTo(w * 0.40f, h * 0.52f); close()
        },
        tint,
    )
    drawArc(
        tint, startAngle = 270f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.24f, h * 0.22f), size = Size(w * 0.14f, h * 0.18f),
        style = Stroke(width = w * 0.07f),
    )
    drawArc(
        tint, startAngle = 90f, sweepAngle = -180f, useCenter = false,
        topLeft = Offset(w * 0.62f, h * 0.22f), size = Size(w * 0.14f, h * 0.18f),
        style = Stroke(width = w * 0.07f),
    )
    drawRoundRect(
        tint, topLeft = Offset(w * 0.47f, h * 0.52f), size = Size(w * 0.06f, h * 0.20f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
    )
    drawRoundRect(
        tint, topLeft = Offset(w * 0.34f, h * 0.76f), size = Size(w * 0.32f, h * 0.07f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
    )
}

/** A microphone: a solid capsule head in a cradle over a stand. */
private fun DrawScope.drawMicrophone(tint: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.40f, h * 0.16f),
        size = Size(w * 0.20f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
    )
    drawArc(
        tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.30f, h * 0.30f), size = Size(w * 0.40f, h * 0.34f),
        style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
    )
    drawRoundRect(
        tint, topLeft = Offset(w * 0.47f, h * 0.62f), size = Size(w * 0.06f, h * 0.20f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
    )
    drawRoundRect(
        tint, topLeft = Offset(w * 0.37f, h * 0.80f), size = Size(w * 0.26f, h * 0.06f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
    )
}

/** An eighth note: a solid head with a stem and a flag. */
private fun DrawScope.drawNote(tint: Color) {
    val w = size.width
    val h = size.height
    drawOval(tint, topLeft = Offset(w * 0.26f, h * 0.60f), size = Size(w * 0.24f, h * 0.20f))
    drawRoundRect(
        tint, topLeft = Offset(w * 0.44f, h * 0.22f), size = Size(w * 0.055f, h * 0.48f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.495f, h * 0.22f)
            cubicTo(w * 0.64f, h * 0.27f, w * 0.70f, h * 0.36f, w * 0.62f, h * 0.48f)
            cubicTo(w * 0.66f, h * 0.36f, w * 0.60f, h * 0.30f, w * 0.495f, h * 0.31f)
            close()
        },
        tint,
    )
}

/** A house: a solid body under a roof. */
private fun DrawScope.drawHouse(tint: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, h * 0.18f)
            lineTo(w * 0.86f, h * 0.50f)
            lineTo(w * 0.14f, h * 0.50f)
            close()
        },
        tint,
    )
    drawRoundRect(
        tint, topLeft = Offset(w * 0.24f, h * 0.48f), size = Size(w * 0.52f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
    )
}

/** A christmas tree: two solid tiers on a trunk, crowned by a sparkle. */
private fun DrawScope.drawTree(tint: Color) {
    val w = size.width
    val h = size.height
    drawSparkle(tint, cx = w * 0.5f, cy = h * 0.13f, r = w * 0.10f)
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, h * 0.24f); lineTo(w * 0.30f, h * 0.54f)
            lineTo(w * 0.70f, h * 0.54f); close()
        },
        tint,
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, h * 0.42f); lineTo(w * 0.24f, h * 0.76f)
            lineTo(w * 0.76f, h * 0.76f); close()
        },
        tint,
    )
    drawRoundRect(
        tint, topLeft = Offset(w * 0.45f, h * 0.76f), size = Size(w * 0.10f, h * 0.10f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
    )
}

/** A layered stack of cards — the collection / other fallback. */
private fun DrawScope.drawStack(tint: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, h * 0.16f); lineTo(w * 0.82f, h * 0.34f)
            lineTo(w * 0.5f, h * 0.52f); lineTo(w * 0.18f, h * 0.34f); close()
        },
        tint,
    )
    drawLine(tint, Offset(w * 0.18f, h * 0.52f), Offset(w * 0.5f, h * 0.70f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.82f, h * 0.52f), Offset(w * 0.5f, h * 0.70f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.18f, h * 0.64f), Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.82f, h * 0.64f), Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
}
