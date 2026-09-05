package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing

/**
 * A modal for the remote. A scrim over everything, a solid panel in the middle, and focus held
 * inside: the panel is its own zone that takes first focus and re-seats it if lost, and Back
 * closes the dialog rather than reaching the screen beneath. Back is not a direction key, so
 * consuming it here keeps to the contract in TvFocus.kt.
 *
 * The host must pass `focusEnabled = false` to the shell while a dialog is up, so the shell's own
 * guard does not fight this one for focus.
 */
@Composable
internal fun TvDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 520.dp,
    content: @Composable () -> Unit,
) {
    val zone = remember { TvZoneState(parent = null) }
    TvFirstFocus(zone)
    Box(
        modifier
            .fillMaxSize()
            .background(TvPalette.scrim)
            .tvFocusGuard(fallback = zone)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TvZone(zone) {
            TvPanel(Modifier.widthIn(max = width).tvZone(zone)) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) { content() }
            }
        }
    }
}

/** A titled dialog with a list of actions, the everyday shape of a TV menu. */
@Composable
internal fun TvMenuDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    actions: List<TvMenuAction>,
) {
    TvDialog(onDismiss = onDismiss) {
        PlexText(text = title, style = PlexTheme.type.title, colour = TvPalette.text, maxLines = 2)
        if (subtitle != null) {
            PlexText(text = subtitle, style = PlexTheme.type.caption, colour = TvPalette.textMuted, maxLines = 2)
        }
        actions.forEach { action ->
            TvListRow(
                title = action.label,
                key = action.label,
                detail = action.detail,
                enabled = action.enabled,
                onClick = { action.onClick(); if (action.dismisses) onDismiss() },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TvButton(label = "Close", key = "close", onClick = onDismiss)
        }
    }
}

internal data class TvMenuAction(
    val label: String,
    val detail: String? = null,
    val enabled: Boolean = true,
    val dismisses: Boolean = true,
    val onClick: () -> Unit,
)

/** A yes/no confirmation. The safe choice is first, so first focus lands on it. */
@Composable
internal fun TvConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    TvDialog(onDismiss = onDismiss, width = 480.dp) {
        PlexText(text = title, style = PlexTheme.type.title, colour = TvPalette.text)
        PlexText(text = body, style = PlexTheme.type.body, colour = TvPalette.textDim)
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            TvButton(label = "Cancel", key = "cancel", onClick = onDismiss)
            TvButton(
                label = confirmLabel,
                key = "confirm",
                primary = !destructive,
                onClick = { onConfirm(); onDismiss() },
            )
        }
    }
}

/**
 * A single-choice list: the current value is marked, select picks one and closes. Rows are
 * composed eagerly so DOWN can walk every option; these lists are short by design.
 */
@Composable
internal fun TvChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TvDialog(onDismiss = onDismiss, width = 480.dp) {
        PlexText(text = title, style = PlexTheme.type.title, colour = TvPalette.text)
        options.forEach { (token, label) ->
            val chosen = token == selected
            TvListRow(
                title = label,
                key = token,
                selected = chosen,
                onClick = { onSelect(token); onDismiss() },
                trailing = { focused ->
                    if (chosen) {
                        TvGlyphIcon(TvGlyph.CHECK, tint = if (focused) TvPalette.ink else TvPalette.gold, size = 22.dp)
                    } else {
                        Box(Modifier.width(22.dp))
                    }
                },
            )
        }
    }
}
