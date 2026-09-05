package com.thotapalli.plex.ui.shared.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.AppLogo
import com.thotapalli.plex.ui.shared.Destination
import com.thotapalli.plex.ui.shared.PlexIcon
import kotlinx.coroutines.delay

/**
 * The television shell: a nav rail on the left and the content on the right, as two focus zones
 * with a declared border between them.
 *
 * RIGHT from any rail item enters the content at wherever the viewer last was. LEFT out of the
 * content lands on the rail item for the page they are on. Neither direction is a guess: both are
 * declared with [tvZone] exits, which is what the previous rail never had. Selecting a rail item —
 * including the page already open — drops focus straight into that page. The rail expands to show
 * labels only while focus is inside it.
 *
 * A [tvFocusGuard] on the root re-seats focus in the content if it is ever lost, and every zone a
 * screen creates with a key outlives the screen through [TvZoneRegistry], so coming back to Home
 * from Settings puts focus on the same card. See CLAUDE.md section 13.
 */
@Composable
internal fun TvShell(
    current: Destination,
    onSelect: (Destination) -> Unit,
    accountName: String?,
    /** False while something above the shell owns the remote (the player, a dialog). */
    focusEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val registry = remember { TvZoneRegistry() }
    val railZone = remember { TvZoneState(parent = null) }
    val contentZone = remember { TvZoneState(parent = null) }

    // Every rail selection, including re-selecting the open page, pushes focus into the content
    // once the page has composed.
    var selectTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectTick, current) {
        if (selectTick == 0) return@LaunchedEffect
        delay(SETTLE_MS)
        contentZone.requestFocus()
    }

    CompositionLocalProvider(LocalTvZoneRegistry provides registry) {
        Box(
            modifier
                .fillMaxSize()
                .background(TvPalette.ground)
                .tvFocusGuard(fallback = contentZone, enabled = focusEnabled),
        ) {
            // Content first so the rail draws over its left edge (backdrops bleed behind the rail).
            TvZone(contentZone) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .tvZone(
                            contentZone,
                            exitLeft = { railZone.target(current) ?: railZone.entry },
                        ),
                ) { content() }
            }
            TvZone(railZone) {
                TvNavRail(
                    zone = railZone,
                    current = current,
                    accountName = accountName,
                    onSelect = { destination ->
                        onSelect(destination)
                        selectTick++
                    },
                    exitRight = { contentZone.entry },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}

private const val SETTLE_MS = 60L

/** The destinations in rail order. Settings doubles as the profile row at the foot. */
private val RAIL_ORDER = listOf(
    Destination.HOME,
    Destination.SEARCH,
    Destination.LIBRARY,
    Destination.DOWNLOADS,
)

@Composable
private fun TvNavRail(
    zone: TvZoneState,
    current: Destination,
    accountName: String?,
    onSelect: (Destination) -> Unit,
    exitRight: () -> androidx.compose.ui.focus.FocusRequester?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (expanded) 1f else 0f, label = "tv-rail-reveal")
    val width by animateDpAsState(
        if (expanded) TvDims.railExpanded else TvDims.railCollapsed,
        label = "tv-rail-width",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { expanded = it.hasFocus }
            .tvZone(zone, exitRight = exitRight)
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.60f + 0.35f * reveal),
                        1f to Color.Black.copy(alpha = 0.35f * reveal),
                    ),
                )
            }
            .padding(vertical = Spacing.xl, horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(Modifier.padding(start = 6.dp)) { AppLogo(size = 40.dp) }
        Spacer(Modifier.height(Spacing.xl))
        RAIL_ORDER.forEach { destination ->
            TvRailRow(
                key = destination,
                label = destination.label,
                selected = destination == current,
                expanded = expanded,
                onClick = { onSelect(destination) },
                glyph = { tint ->
                    if (destination == Destination.LIBRARY) TvGlyphIcon(TvGlyph.GRID, tint = tint, size = 24.dp)
                    else PlexIcon(kind = destination.icon, tint = tint, size = 26.dp)
                },
            )
        }
        Spacer(Modifier.weight(1f))
        TvRailRow(
            key = Destination.SETTINGS,
            label = accountName ?: "Settings",
            selected = current == Destination.SETTINGS,
            expanded = expanded,
            onClick = { onSelect(Destination.SETTINGS) },
            glyph = {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(TvPalette.gold),
                    contentAlignment = Alignment.Center,
                ) {
                    PlexText(
                        text = accountName?.firstOrNull()?.uppercase() ?: "•",
                        style = PlexTheme.type.caption,
                        colour = TvPalette.ink,
                        maxLines = 1,
                    )
                }
            },
        )
    }
}

private val RailRowShape = RoundedCornerShape(12.dp)

/**
 * One rail row: a gold indicator bar and glyph when selected, a charcoal fill when focused, the
 * label only while the rail is expanded.
 */
@Composable
private fun TvRailRow(
    key: Destination,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (tint: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = focused || selected
    val tint = if (active) TvPalette.gold else TvPalette.textDim
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RailRowShape)
            .background(if (focused) TvPalette.railFocus else Color.Transparent, RailRowShape)
            .tvInteractive(interaction, key = key, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(start = 4.dp)
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) TvPalette.gold else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { glyph(tint) }
        if (expanded) {
            Spacer(Modifier.width(Spacing.sm))
            PlexText(
                text = label,
                style = PlexTheme.type.label,
                colour = if (active) Color.White else TvPalette.textDim,
                maxLines = 1,
            )
        }
    }
}
