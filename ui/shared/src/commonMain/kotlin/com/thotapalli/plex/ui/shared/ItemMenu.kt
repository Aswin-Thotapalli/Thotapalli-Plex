package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.watched
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing

/**
 * The actions a media item's context menu offers. Every one is already implemented on the
 * view model; this record just carries the bound lambdas down to the tile or detail screen
 * that shows the menu. See CLAUDE.md section 5 (server administration).
 *
 * [onRemoveFromContinueWatching] is only meaningful for a Continue Watching tile, so it is
 * optional; the menu shows the row only when the host is told the item is a Continue Watching
 * one and the lambda is present.
 */
data class ItemActions(
    val onMarkWatched: () -> Unit,
    val onMarkUnwatched: () -> Unit,
    val onDownload: () -> Unit,
    val onRefreshMetadata: () -> Unit,
    val onDelete: () -> Unit,
    val onRemoveFromContinueWatching: (() -> Unit)? = null,
)

/**
 * Lays out [content] and hosts its action menu, opening on a right-click (desktop) or a
 * long-press (touch). The existing click on the content — the tap that opens the detail
 * screen — is untouched: the menu lives in a separate pointer layer above the content, and
 * the primary tap still reaches the content's own [plexFocusable]. Television has no right
 * mouse button and no long-press hardware, so it reaches these actions through the detail
 * screen's overflow instead, but a center-hold still opens the menu here where the platform
 * routes one.
 *
 * When [actions] is null the content is shown with no menu attached, so every existing caller
 * that passes nothing keeps working unchanged.
 */
@Composable
fun ItemMenuHost(
    item: MediaItem,
    actions: ItemActions?,
    isContinueWatching: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (actions == null) {
        Box(modifier) { content() }
        return
    }

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.itemMenuGestures { expanded = true }) {
        content()
        ItemActionsMenu(
            item = item,
            actions = actions,
            isContinueWatching = isContinueWatching,
            expanded = expanded,
            onDismiss = { expanded = false },
        )
    }
}

/**
 * A "More" overflow control that opens the same action menu, for the detail screen where there
 * is no tile to long-press. Sits beside the primary Play and Download buttons.
 */
@Composable
fun ItemOverflowButton(
    item: MediaItem,
    actions: ItemActions,
    isContinueWatching: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        SecondaryButton(label = "More", onClick = { expanded = true })
        ItemActionsMenu(
            item = item,
            actions = actions,
            isContinueWatching = isContinueWatching,
            expanded = expanded,
            onDismiss = { expanded = false },
        )
    }
}

/**
 * A small circular "⋮" overflow control that opens the same [ItemActionsMenu], for a poster tile
 * that shows the menu entry point on its face rather than hiding it behind a long-press. Carries the
 * [GlassRole.CHIP] material so the glyph stays legible dropped onto the brightest poster, and anchors
 * the dropdown to itself. Its own click consumes the tap, so pressing it opens the menu while a tap
 * anywhere else on the tile still reaches the tile's own click.
 */
@Composable
fun ItemOverflowIconButton(
    item: MediaItem,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    isContinueWatching: Boolean = false,
    size: Dp = 30.dp,
) {
    val colours = PlexTheme.colours
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .plexFocusable(shape = CircleShape, onClick = { expanded = true })
                .size(size)
                .material(GlassRole.CHIP, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The three vertical dots, drawn to take the button's content tint like the other glyphs.
            Canvas(Modifier.size(size * 0.5f)) {
                val cx = this.size.width / 2f
                val r = this.size.minDimension * 0.09f
                listOf(0.18f, 0.5f, 0.82f).forEach { fy ->
                    drawCircle(colours.textPrimary, r, Offset(cx, this.size.height * fy))
                }
            }
        }
        ItemActionsMenu(
            item = item,
            actions = actions,
            isContinueWatching = isContinueWatching,
            expanded = expanded,
            onDismiss = { expanded = false },
        )
    }
}

/**
 * Right-click and long-press, layered above the content's own click. Right-click reacts only
 * to the secondary mouse button, so it never disturbs the primary tap; long-press is Compose's
 * own [detectTapGestures] long-press, which consumes the gesture when it fires so the content's
 * click is cancelled rather than fired alongside the menu.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.itemMenuGestures(onOpen: () -> Unit): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onOpen()
                }
            }
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(onLongPress = { onOpen() })
    }

/**
 * The dropdown of actions plus the delete-confirmation dialog. The menu is styled on the theme
 * surface tokens (section 12); Delete carries the error tone and sits below a divider, and is
 * the only action that opens a confirmation before it runs.
 */
@Composable
private fun ItemActionsMenu(
    item: MediaItem,
    actions: ItemActions,
    isContinueWatching: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val colours = PlexTheme.colours
    var confirmDelete by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.material(GlassRole.SHEET, Radius.glassSmall),
    ) {
        if (item.watched) {
            MenuRow("Mark as Unwatched") { onDismiss(); actions.onMarkUnwatched() }
        } else {
            MenuRow("Mark as Watched") { onDismiss(); actions.onMarkWatched() }
        }

        if (isContinueWatching && actions.onRemoveFromContinueWatching != null) {
            val remove = actions.onRemoveFromContinueWatching
            MenuRow("Remove from Continue Watching") { onDismiss(); remove() }
        }

        MenuRow("Download") { onDismiss(); actions.onDownload() }
        MenuRow("Refresh Metadata") { onDismiss(); actions.onRefreshMetadata() }

        HorizontalDivider(
            color = colours.border,
            modifier = Modifier.padding(vertical = Spacing.xxs),
        )

        MenuRow("Delete", tone = colours.error) { onDismiss(); confirmDelete = true }
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            title = item.title,
            onCancel = { confirmDelete = false },
            onConfirm = { confirmDelete = false; actions.onDelete() },
        )
    }
}

@Composable
private fun MenuRow(
    label: String,
    tone: Color? = null,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    DropdownMenuItem(
        text = {
            PlexText(
                text = label,
                style = PlexTheme.type.label,
                colour = tone ?: colours.textPrimary,
                maxLines = 1,
            )
        },
        onClick = onClick,
    )
}

/**
 * The delete confirmation. Delete permanently removes the media from the server, so it never
 * runs straight from the menu; this stands between the menu tap and [ItemActions.onDelete].
 * See CLAUDE.md section 5.
 */
@Composable
private fun DeleteConfirmDialog(
    title: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colours = PlexTheme.colours
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.material(GlassRole.SHEET, Radius.card),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = colours.textPrimary,
        textContentColor = colours.textSecondary,
        shape = Radius.card,
        title = { PlexText(text = "Delete “$title”?", style = PlexTheme.type.title) },
        text = {
            PlexText(
                text = "This permanently removes the media from the server.",
                colour = colours.textSecondary,
            )
        },
        confirmButton = { DangerButton(label = "Delete", onClick = onConfirm) },
        dismissButton = { SecondaryButton(label = "Cancel", onClick = onCancel) },
    )
}

/** A filled pill in the error tone, for the one irreversible action in a dialog. */
@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick)
            .clip(Radius.pill)
            .background(colours.error, Radius.pill)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = Color.White,
            maxLines = 1,
        )
    }
}
