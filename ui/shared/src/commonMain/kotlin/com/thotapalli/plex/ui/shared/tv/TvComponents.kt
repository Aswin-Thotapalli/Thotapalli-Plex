package com.thotapalli.plex.ui.shared.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ProgressBar
import com.thotapalli.plex.ui.shared.primaryLine

/**
 * The ten-foot component set. Every control here is a [tvInteractive] target with the one focus
 * language — a firm scale and a gold ring — so the whole app reads the same way from the sofa.
 * Nothing in this file consumes a direction key; see TvFocus.kt.
 */

// --- buttons --------------------------------------------------------------------------------

/**
 * A pill action. [primary] is the gold call to action (Play, Resume, Sign in); the rest are quiet
 * chips that light to solid white under focus. [key] is what the enclosing zone remembers.
 */
@Composable
internal fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = label,
    glyph: TvGlyph? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.97f
            focused -> TvDims.FOCUS_SCALE_CONTROL
            else -> 1f
        },
        label = "tv-button",
    )
    val background = when {
        !enabled -> TvPalette.chipRest
        primary && focused -> TvPalette.goldBright
        primary -> TvPalette.gold
        focused -> TvPalette.chipFocus
        else -> TvPalette.chipRest
    }
    val content = when {
        !enabled -> TvPalette.textMuted
        primary || focused -> TvPalette.ink
        else -> TvPalette.textDim
    }
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(TvShape.button)
            .background(background)
            .then(
                if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), TvShape.button) else Modifier,
            )
            .tvInteractive(interaction, key = key, onClick = onClick, onLongClick = onLongClick, enabled = enabled)
            .heightIn(min = 56.dp)
            .padding(horizontal = if (primary) 28.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            TvGlyphIcon(glyph, tint = content, size = if (primary) 24.dp else 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        PlexText(text = label, style = PlexTheme.type.label, colour = content, maxLines = 1)
    }
}

/** A round icon-only control: a translucent disc at rest, solid light with a dark glyph on focus. */
@Composable
internal fun TvIconButton(
    glyph: TvGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = glyph,
    size: Dp = 52.dp,
    accent: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) TvDims.FOCUS_SCALE else 1f, label = "tv-icon")
    val background = when {
        !enabled -> Color(0x22000000)
        accent && focused -> TvPalette.goldBright
        accent -> TvPalette.gold
        focused -> TvPalette.chipFocus
        else -> Color(0x40000000)
    }
    val tint = when {
        !enabled -> TvPalette.textMuted
        accent || focused -> TvPalette.ink
        else -> TvPalette.text
    }
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), CircleShape) else Modifier)
            .tvInteractive(interaction, key = key, onClick = onClick, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) {
        TvGlyphIcon(glyph, tint = tint, size = size * 0.46f)
    }
}

// --- cards ----------------------------------------------------------------------------------

/** A 2:3 poster with the title beneath. */
@Composable
internal fun TvPosterCard(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = item.ratingKey,
    width: Dp = TvDims.posterWidth,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) TvDims.FOCUS_SCALE else 1f, label = "tv-poster")
    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .tvInteractive(interaction, key = key, onClick = onClick, onLongClick = onLongClick, onFocused = onFocused),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(TvShape.poster)
                .then(if (focused) Modifier.border(TvDims.focusRing, TvPalette.gold, TvShape.poster) else Modifier),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = primaryLine(item),
                fallbackTitle = primaryLine(item),
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                        .clip(TvShape.pill)
                        .background(TvPalette.gold)
                        .padding(horizontal = Spacing.xs, vertical = 2.dp),
                ) {
                    PlexText(badge, style = PlexTheme.type.caption, colour = TvPalette.ink, maxLines = 1)
                }
            }
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Spacing.xs)) {
                    ProgressBar(progress = item.progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        PlexText(
            text = primaryLine(item),
            style = PlexTheme.type.label,
            colour = if (focused) TvPalette.text else TvPalette.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A 16:9 card with a resume bar: continue watching, episodes, search hits. */
@Composable
internal fun TvWideCard(
    item: MediaItem,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = item.ratingKey,
    width: Dp = TvDims.wideWidth,
    subLine: String? = null,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tv-wide")
    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .tvInteractive(interaction, key = key, onClick = onClick, onLongClick = onLongClick, onFocused = onFocused),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(TvShape.card)
                .then(if (focused) Modifier.border(TvDims.focusRing, TvPalette.gold, TvShape.card) else Modifier),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = primaryLine(item),
                fallbackTitle = primaryLine(item),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)),
                ),
            )
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Spacing.xs)) {
                    ProgressBar(progress = item.progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        PlexText(
            text = primaryLine(item),
            style = PlexTheme.type.label,
            colour = if (focused) TvPalette.text else TvPalette.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subLine != null) {
            PlexText(text = subLine, style = PlexTheme.type.caption, colour = TvPalette.textMuted, maxLines = 1)
        }
    }
}

/** A plain labelled tile the size of a poster: "All ›", a library, a collection stack. */
@Composable
internal fun TvTileCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = label,
    caption: String? = null,
    width: Dp = TvDims.posterWidth,
    aspect: Float = 2f / 3f,
    glyph: TvGlyph? = null,
    onFocused: (() -> Unit)? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) TvDims.FOCUS_SCALE else 1f, label = "tv-tile")
    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .tvInteractive(interaction, key = key, onClick = onClick, onFocused = onFocused),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(TvShape.poster)
                .background(if (focused) TvPalette.gold else TvPalette.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (glyph != null) TvGlyphIcon(glyph, tint = if (focused) TvPalette.ink else TvPalette.text, size = 32.dp)
                PlexText(
                    text = label,
                    style = PlexTheme.type.title,
                    colour = if (focused) TvPalette.ink else TvPalette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(Spacing.xs))
            PlexText(text = caption, style = PlexTheme.type.label, colour = TvPalette.textDim, maxLines = 1)
        }
    }
}

// --- rails ----------------------------------------------------------------------------------

/**
 * Pivot scrolling: once a row starts to scroll, hold the focused card at a fixed column ~18 % in
 * from the left so what comes next is always previewed on the right.
 */
@OptIn(ExperimentalFoundationApi::class)
private val TvPivot = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - containerSize * 0.18f
}

/**
 * A titled horizontal row that is its own [TvZone]: coming back to it lands on the card the viewer
 * left from. The title sits inset past the nav rail; the row overruns to the right edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvRail(
    title: String?,
    modifier: Modifier = Modifier,
    zone: TvZoneState = rememberTvZone(),
    startPadding: Dp = TvDims.contentStart,
    exitLeft: (() -> FocusRequester?)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            PlexText(
                text = title,
                style = PlexTheme.type.title,
                colour = TvPalette.text,
                maxLines = 1,
                modifier = Modifier.padding(start = startPadding, end = Spacing.xl, bottom = Spacing.sm),
            )
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            TvZone(zone) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().tvZone(zone, exitLeft = exitLeft),
                    contentPadding = PaddingValues(start = startPadding, end = TvDims.overscanX),
                    horizontalArrangement = Arrangement.spacedBy(TvDims.railGap),
                    content = content,
                )
            }
        }
    }
}

// --- rows -----------------------------------------------------------------------------------

/**
 * A full-width list row (settings, downloads, track pickers): a title, an optional detail, and a
 * trailing slot. Solid fill on focus; never a ring on a full-width row, which would read as a box.
 */
@Composable
internal fun TvListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = title,
    detail: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    leading: (@Composable (tint: Color) -> Unit)? = null,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        focused -> TvPalette.chipFocus
        selected -> TvPalette.surfaceRaised
        else -> TvPalette.surface
    }
    val titleColour = when {
        !enabled -> TvPalette.textMuted
        focused -> TvPalette.ink
        else -> TvPalette.text
    }
    val detailColour = if (focused) TvPalette.ink.copy(alpha = 0.7f) else TvPalette.textDim
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(TvShape.card)
            .background(background)
            .tvInteractive(interaction, key = key, onClick = onClick, enabled = enabled, onFocused = onFocused)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (leading != null) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading(titleColour) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PlexText(text = title, style = PlexTheme.type.body, colour = titleColour, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                PlexText(text = detail, style = PlexTheme.type.caption, colour = detailColour, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing(focused)
    }
}

/** The trailing on/off pill for a [TvListRow]. */
@Composable
internal fun TvSwitch(on: Boolean, focused: Boolean) {
    val track = when {
        on && focused -> TvPalette.ink
        on -> TvPalette.gold
        focused -> TvPalette.ink.copy(alpha = 0.25f)
        else -> TvPalette.hairline
    }
    val knob = when {
        on && focused -> TvPalette.gold
        on -> TvPalette.ink
        else -> TvPalette.text
    }
    Box(
        Modifier.width(52.dp).height(30.dp).clip(TvShape.pill).background(track),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(knob))
    }
}

/** The trailing "value ›" affordance for a choice row. */
@Composable
internal fun TvValue(value: String, focused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlexText(
            text = value,
            style = PlexTheme.type.label,
            colour = if (focused) TvPalette.ink else TvPalette.gold,
            maxLines = 1,
        )
        TvGlyphIcon(TvGlyph.CHEVRON_RIGHT, tint = if (focused) TvPalette.ink else TvPalette.textMuted, size = 18.dp)
    }
}

// --- text and layout helpers ----------------------------------------------------------------

@Composable
internal fun TvSectionTitle(text: String, modifier: Modifier = Modifier) {
    PlexText(
        text = text,
        style = PlexTheme.type.title,
        colour = TvPalette.text,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
internal fun TvCaption(text: String, modifier: Modifier = Modifier) {
    PlexText(
        text = text,
        style = PlexTheme.type.caption,
        colour = TvPalette.textMuted,
        maxLines = 1,
        modifier = modifier.padding(bottom = Spacing.xs),
    )
}

/** A solid panel: dialogs, side sheets, the player's track picker. */
@Composable
internal fun TvPanel(
    modifier: Modifier = Modifier,
    shape: Shape = TvShape.panel,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(TvPalette.surface)
            .border(1.dp, TvPalette.hairline, shape),
    ) { content() }
}

/** An empty-state message in the middle of a screen. */
@Composable
internal fun TvEmpty(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(TvDims.overscanX),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlexText(text = title, style = PlexTheme.type.title, colour = TvPalette.text)
        if (body != null) {
            Spacer(Modifier.height(Spacing.xs))
            PlexText(
                text = body,
                style = PlexTheme.type.body,
                colour = TvPalette.textDim,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}
