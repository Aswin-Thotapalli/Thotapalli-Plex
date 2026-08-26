package com.thotapalli.plex.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The liquid-glass material system.
 *
 * Every floating surface in the redesign — the navigation, a sheet, a chip, a button — is a sheet
 * of frosted glass: it samples and blurs the content behind it, casts a cool halo, catches a
 * specular highlight along its top-left and is framed by a bright refractive rim, all clipped to a
 * bubbly continuous corner. This file builds that material once, backed by [Haze][hazeEffect] so
 * the blur is real on both Android and desktop, and every primitive reads from it.
 *
 * ### How the backdrop blur is wired
 * Haze works in two halves. The scrolling content that a panel frosts must be marked as the Haze
 * *source* ([Modifier.glassSource]); the panel then declares itself an *effect* over that source
 * ([Modifier.liquidGlass]). The two are connected by a shared [HazeState]. The convention here is a
 * single ambient state carried in [LocalHazeState]: wrap a screen in [GlassScaffold] (or provide
 * the state yourself), mark the background layer with [Modifier.glassSource], and any glass panel
 * below automatically frosts it with no state threaded by hand.
 *
 * When no source is present — a panel drawn before any screen has adopted the scaffold — the glass
 * degrades gracefully to a translucent tinted fill with the same specular, rim and glow, so it
 * still reads as glass on any target rather than vanishing.
 */
object GlassDefaults {
    /** The frost blur radius. Wide enough that text behind the glass dissolves into colour. */
    val blurRadius: Dp = 40.dp

    /** A faint grain over the frost, breaking up the blur into something that reads as a surface. */
    const val noiseFactor: Float = 0.06f

    /** A pressed glass control springs down to this scale — the "bubble" squash. */
    const val pressedScale: Float = 0.97f
}

/**
 * The ambient Haze source a glass panel frosts. Null until a screen provides one (via
 * [GlassScaffold] or [CompositionLocalProvider]); a null state makes [Modifier.liquidGlass] fall
 * back to its translucent fill.
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState?> = staticCompositionLocalOf { null }

/** A remembered [HazeState], the object that ties a glass source to the panels that frost it. */
@Composable
fun rememberGlassState(): HazeState = rememberHazeState()

/**
 * A backdrop host. Creates (or adopts) a [HazeState], publishes it on [LocalHazeState] and lays
 * [content] in a [Box], so every glass panel inside frosts whatever the content marks as its
 * source with [Modifier.glassSource]. The shell or a screen wraps its body in this; the panels
 * need no wiring of their own.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    state: HazeState = rememberGlassState(),
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalHazeState provides state) {
        Box(modifier, content = content)
    }
}

/**
 * Mark this node as the Haze source — the pixels a glass panel samples and blurs. Applied to the
 * scrolling background of a screen. Reads the ambient [LocalHazeState] unless a [state] is passed;
 * with neither, it is a no-op, so it is always safe to attach.
 */
fun Modifier.glassSource(state: HazeState? = null): Modifier = composed {
    // Television draws no glass — the ten-foot chrome is a solid left rail — so there is nothing to
    // frost and registering a Haze source would only pay a full-screen render-capture every frame,
    // a real cause of stutter on weak TV GPUs. Skip it entirely on TV.
    if (PlexTheme.sizeClass.isTelevision) return@composed this
    val haze = state ?: LocalHazeState.current
    if (haze != null) this.hazeSource(haze) else this
}

/**
 * Dress a surface as a sheet of liquid glass. In back-to-front order it lays down:
 *
 * 1. a **Haze backdrop blur** of the source content (tinted [tint], radius [GlassDefaults.blurRadius]),
 *    or a translucent [tint] fill when no source is available;
 * 2. a **top specular** — a diagonal light gradient brightest at the top-left corner;
 * 3. a **one-pixel rim** — the bright refractive edge of the glass;
 * 4. a **soft outer glow** — a cool coloured halo lifting the pane off the ground,
 *
 * all clipped to [shape], the bubbly continuous corner by default.
 *
 * @param shape the pane outline. Defaults to [Radius.glass], the large bubbly corner.
 * @param elevated raises the outer glow, for a panel meant to sit further above the ground.
 * @param state the Haze source to frost. Defaults to the ambient [LocalHazeState].
 * @param tint the frost colour. [Color.Unspecified] resolves to the theme [PlexColours.glassTint].
 * @param specular draws the top-left light streak.
 * @param rim strokes the refractive edge.
 * @param glow casts the outer halo.
 * @param specularBoost 0..1, brightens and fills the specular — a control raises this while pressed
 *   so the glass appears to catch more light under the finger.
 */
fun Modifier.liquidGlass(
    shape: Shape = Radius.glass,
    elevated: Boolean = false,
    state: HazeState? = null,
    tint: Color = Color.Unspecified,
    specular: Boolean = true,
    rim: Boolean = true,
    glow: Boolean = true,
    specularBoost: Float = 0f,
): Modifier = composed {
    val colours = PlexTheme.colours
    val haze = state ?: LocalHazeState.current
    val fill = if (tint.isSpecified) tint else colours.glassTint
    val highlight = colours.glassHighlight
    val rimColour = colours.glassRim
    val glowColour = colours.glassGlow
    val glowElevation = if (elevated) Elevation.glassGlow + Spacing.xs else Elevation.glassGlow
    val boost = specularBoost.coerceIn(0f, 1f)

    this
        // 4. Soft outer glow: a coloured, diffuse shadow bleeding out around the bubble.
        .then(
            if (glow) {
                Modifier.shadow(
                    elevation = glowElevation,
                    shape = shape,
                    ambientColor = glowColour,
                    spotColor = glowColour,
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        // 1. Backdrop frost when a source is live; a translucent tinted fill otherwise.
        .then(
            if (haze != null) {
                // Positional args: the second argument's HazeTint type selects the single-tint
                // HazeStyle constructor unambiguously (background, tint, blurRadius, noiseFactor).
                Modifier.hazeEffect(
                    haze,
                    HazeStyle(
                        colours.background,
                        HazeTint(fill),
                        GlassDefaults.blurRadius,
                        GlassDefaults.noiseFactor,
                    ),
                )
            } else {
                Modifier.background(fill)
            },
        )
        // 2. Top specular and 3. refractive rim, painted over the frost and around the content.
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val strokePx = 1.dp.toPx()
            val specularAlpha = (highlight.alpha + boost * 0.25f).coerceAtMost(1f)
            val reach = 0.55f + boost * 0.2f
            val specularBrush = Brush.linearGradient(
                colors = listOf(highlight.copy(alpha = specularAlpha), Color.Transparent),
                start = Offset.Zero,
                end = Offset(size.width * reach, size.height * reach),
            )
            onDrawWithContent {
                if (specular) drawOutline(outline, brush = specularBrush)
                drawContent()
                if (rim) drawOutline(outline, color = rimColour, style = Stroke(width = strokePx))
            }
        }
}

/**
 * The press "bubble": spring the node down to [pressedScale] while [pressed], snapping back on
 * release. Pair with [Modifier.liquidGlass]'s `specularBoost` to also brighten the highlight under
 * the finger. Snappy with a hair of bounce, so a tap feels like a soft, springy button.
 */
fun Modifier.pressBubble(
    pressed: Boolean,
    pressedScale: Float = GlassDefaults.pressedScale,
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.springBouncy(),
        label = "glass-press-bubble",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The press bubble driven straight off an [InteractionSource]: springs the scale down whenever the
 * source reports a press. A convenience over [Modifier.pressBubble] for the common case of a
 * clickable that already owns an interaction source.
 */
fun Modifier.pressBubble(
    interactionSource: InteractionSource,
    pressedScale: Float = GlassDefaults.pressedScale,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    this.pressBubble(pressed = pressed, pressedScale = pressedScale)
}
