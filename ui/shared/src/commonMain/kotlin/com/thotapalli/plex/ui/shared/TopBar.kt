package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing

/**
 * A slim top bar: an optional back button, a title, and an optional trailing actions slot.
 *
 * The [transparent] mode leaves the bar unpainted so it can float over a cinematic backdrop;
 * in that mode the back button carries its own circular scrim so it stays legible over bright
 * artwork. Opaque mode paints the background token and is used on ordinary scrolled screens.
 */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    transparent: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colours = PlexTheme.colours
    val isTelevision = PlexTheme.sizeClass.isTelevision

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (transparent) Color.Transparent else colours.background)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            TopBarIconButton(
                kind = PlexIconKind.BACK,
                onClick = onBack,
                scrim = transparent,
            )
            Spacer(Modifier.width(Spacing.xs))
        }

        PlexText(
            text = title,
            style = if (isTelevision) PlexTheme.type.display else PlexTheme.type.title,
            colour = if (transparent) Color(0xFFF2F3F5) else colours.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        if (actions != null) {
            Spacer(Modifier.width(Spacing.xs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                content = actions,
            )
        }
    }
}

/**
 * A round, focusable icon button sized for the bar. Exposed so trailing [TopBar] actions can
 * be built from the same token as the back button.
 */
@Composable
fun TopBarIconButton(
    kind: PlexIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Boolean = false,
    tint: Color? = null,
) {
    val colours = PlexTheme.colours
    val isTelevision = PlexTheme.sizeClass.isTelevision
    val touch = if (isTelevision) 48.dp else 40.dp

    Box(
        modifier = modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick)
            .size(touch)
            .background(
                if (scrim) Color(0x66000000) else Color.Transparent,
                Radius.pill,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlexIcon(
            kind = kind,
            size = if (isTelevision) 28.dp else 24.dp,
            tint = tint ?: if (scrim) Color.White else colours.textPrimary,
        )
    }
}
