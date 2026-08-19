package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.Elevation
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing

/**
 * The two buttons the detail screens and the hero use.
 *
 * A filled accent pill for the one primary action on a screen (Play, Resume), and an
 * outlined pill for everything secondary (Details, Download, Mark watched). Both are
 * focusable and hoverable through [plexFocusable], so a remote, a keyboard and a mouse each
 * get the same accent ring and grow. An optional leading [PlexIconKind] is drawn from the
 * same Canvas icon set as the rest of the app.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: PlexIconKind? = null,
    enabled: Boolean = true,
) {
    val colours = PlexTheme.colours
    val content = if (colours.isDark) colours.background else Color.White
    PlexButtonBody(
        label = label,
        leadingIcon = leadingIcon,
        contentColour = content,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            // The one primary action on a screen sits proud of the surface with a soft shadow,
            // and a top-to-bottom accent gradient reads as a lit, convex pill rather than a flat
            // fill. A hairline lip finishes the edge.
            .shadow(
                elevation = if (enabled) Elevation.button else 0.dp,
                shape = Radius.pill,
                ambientColor = colours.elevationShadow,
                spotColor = colours.elevationShadow,
            )
            .clip(Radius.pill)
            .background(
                Brush.verticalGradient(
                    0f to colours.accentBright,
                    0.5f to colours.accent,
                    1f to colours.accentDeep,
                ),
                Radius.pill,
            )
            .border(1.dp, colours.edgeHighlight, Radius.pill),
    )
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: PlexIconKind? = null,
    enabled: Boolean = true,
) {
    val colours = PlexTheme.colours
    PlexButtonBody(
        label = label,
        leadingIcon = leadingIcon,
        contentColour = colours.textPrimary,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .clip(Radius.pill)
            // A quiet translucent fill plus a clean hairline reads as a control against either a
            // solid surface or a cinematic backdrop, without competing with the filled primary
            // beside it. The two-layer border — a soft light lip over the token line — keeps the
            // edge crisp over bright artwork where a single hairline would wash out.
            .background(Color(0x24FFFFFF), Radius.pill)
            .border(1.dp, Color(0x33FFFFFF), Radius.pill),
    )
}

@Composable
private fun PlexButtonBody(
    label: String,
    leadingIcon: PlexIconKind?,
    contentColour: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val isTelevision = PlexTheme.sizeClass.isTelevision
    val minHeight = if (isTelevision) 56.dp else 44.dp

    Row(
        modifier = modifier
            .plexFocusable(shape = Radius.pill, enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            PlexIcon(
                kind = leadingIcon,
                size = if (isTelevision) 24.dp else 20.dp,
                tint = contentColour,
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = contentColour,
            maxLines = 1,
        )
    }
}
