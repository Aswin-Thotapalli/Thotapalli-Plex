package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.download.DownloadRow
import com.thotapalli.plex.core.download.DownloadState
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.design.liquidGlass
import com.thotapalli.plex.ui.design.pressBubble
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus

/**
 * Downloads: downloaded and queued items with title, size and state. Active rows show
 * progress and a pause action, completed rows show delete, and the total space used sits
 * in a frosted header at the top. See CLAUDE.md section 14 item 8.
 */
@Composable
fun DownloadsScreen(
    entries: List<DownloadEntry>,
    totalBytesOnDisk: Long,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours
    // On a television the header takes first focus on entry. The rows are not focusable as a
    // whole (only their action chips are) and the list can be empty, so the always-present
    // header is the stable landing spot that keeps focus from landing nowhere.
    // See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = PlexTheme.sizeClass.isTelevision)

    ContentWidthCap(modifier) {
        // The translucent veil over the ambient backdrop, and the source every frosted panel samples.
        Box(Modifier.fillMaxSize().material(GlassRole.GROUND)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().glassSource(),
                contentPadding = PaddingValues(PlexTheme.sizeClass.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    // The header carries the total used, and holds first focus on television.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(firstFocus)
                            .focusable()
                            .material(GlassRole.CARD, shape = Radius.glass)
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        SectionHeader("Downloads")
                        PlexText(
                            text = "${formatBytes(totalBytesOnDisk)} used",
                            style = PlexTheme.type.body,
                            colour = colours.textSecondary,
                        )
                    }
                }

                if (entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .material(GlassRole.CARD, shape = Radius.glass)
                                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                PlexIcon(
                                    kind = PlexIconKind.DOWNLOADS,
                                    size = 32.dp,
                                    tint = colours.textSecondary,
                                )
                                PlexText(
                                    text = "Nothing downloaded yet.",
                                    style = PlexTheme.type.body,
                                    colour = colours.textSecondary,
                                )
                            }
                        }
                    }
                }

                items(entries, key = { it.row.ratingKey }) { entry ->
                    DownloadRowItem(entry, onPause, onResume, onDelete)
                }
            }
        }
    }
}

@Composable
private fun DownloadRowItem(
    entry: DownloadEntry,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colours = PlexTheme.colours
    val row = entry.row

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .material(GlassRole.CARD, shape = Radius.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                PlexText(entry.title, style = PlexTheme.type.label, maxLines = 1)
                PlexText(
                    text = statusLine(row),
                    style = PlexTheme.type.caption,
                    colour = if (row.state == DownloadState.FAILED) colours.error else colours.textSecondary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when (row.state) {
                    // An active row shows a pause action.
                    DownloadState.RUNNING, DownloadState.QUEUED ->
                        DownloadAction("Pause", PlexIconKind.PAUSE) { onPause(row.ratingKey) }

                    DownloadState.PAUSED, DownloadState.FAILED ->
                        DownloadAction("Resume", PlexIconKind.PLAY, emphasised = true) { onResume(row.ratingKey) }

                    // A completed row shows delete only.
                    DownloadState.COMPLETED -> Unit
                }
                DownloadAction("Delete", PlexIconKind.CLOSE) { onDelete(row.ratingKey) }
            }
        }

        // Progress only while there is progress to show. A completed row does not need a
        // full bar telling it so.
        if (row.state != DownloadState.COMPLETED) {
            DownloadProgress(fraction = row.fraction)
        }
    }
}

/**
 * A frosted glass action chip. It springs down into a soft bubble under a press and catches more
 * light as it does, and grows an amber rim under focus or the pointer. Selection and focus are the
 * accent's only jobs. See CLAUDE.md section 12.
 */
@Composable
private fun DownloadAction(
    label: String,
    icon: PlexIconKind,
    emphasised: Boolean = false,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val accented = emphasised || active

    Row(
        modifier = Modifier
            .pressBubble(interaction)
            .liquidGlass(shape = Radius.pill, specularBoost = if (pressed) 0.7f else 0f)
            .then(if (accented) Modifier.border(1.dp, colours.accent, Radius.pill) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        PlexIcon(
            kind = icon,
            size = 16.dp,
            tint = if (accented) colours.accent else colours.textSecondary,
        )
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = if (accented) colours.accent else colours.textPrimary,
        )
    }
}

/** The download progress bar: a quiet track with an accent fill, capped as a pill. */
@Composable
private fun DownloadProgress(fraction: Float) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(Radius.pill)
            .background(colours.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(Radius.pill)
                .background(
                    Brush.horizontalGradient(listOf(colours.accentBright, colours.accent)),
                ),
        )
    }
}

private fun statusLine(row: DownloadRow): String = when (row.state) {
    DownloadState.QUEUED -> "Queued  ${formatBytes(row.receivedBytes)} of ${formatBytes(row.totalBytes)}"
    DownloadState.RUNNING ->
        "${(row.fraction * 100).toInt()}%  ${formatBytes(row.receivedBytes)} of ${formatBytes(row.totalBytes)}"
    DownloadState.PAUSED -> "Paused  ${formatBytes(row.receivedBytes)} of ${formatBytes(row.totalBytes)}"
    DownloadState.COMPLETED -> formatBytes(row.totalBytes)
    DownloadState.FAILED -> "Failed  ${formatBytes(row.receivedBytes)} of ${formatBytes(row.totalBytes)} kept"
}

/** A download entry with the title resolved, since the queue only knows rating keys. */
data class DownloadEntry(
    val row: DownloadRow,
    val title: String,
)

/**
 * Binary units, because that is what a file system reports and what the viewer will see
 * if they go looking on disk.
 */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val gb = bytes / (1024.0 * 1024 * 1024)
    if (gb >= 1) {
        val whole = gb.toInt()
        val tenths = ((gb - whole) * 10).toInt()
        return "$whole.$tenths GB"
    }
    val mb = bytes / (1024.0 * 1024)
    if (mb >= 1) return "${mb.toInt()} MB"
    return "${(bytes / 1024.0).toInt()} KB"
}
