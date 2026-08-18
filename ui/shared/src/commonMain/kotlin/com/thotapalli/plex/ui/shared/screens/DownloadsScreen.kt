package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.download.DownloadRow
import com.thotapalli.plex.core.download.DownloadState
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.SectionHeader

/**
 * Downloads: downloaded and queued items with title, size and state. Active rows show
 * progress and a pause action, completed rows show delete, and the total space used sits
 * at the top. See CLAUDE.md section 14 item 8.
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

    ContentWidthCap(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                PlexTheme.sizeClass.screenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item {
                Column {
                    SectionHeader("Downloads")
                    PlexText(
                        text = "${formatBytes(totalBytesOnDisk)} used",
                        style = PlexTheme.type.caption,
                        colour = colours.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            if (entries.isEmpty()) {
                item {
                    PlexText(
                        text = "Nothing downloaded yet.",
                        colour = colours.textSecondary,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            items(entries, key = { it.row.ratingKey }) { entry ->
                DownloadRowItem(entry, onPause, onResume, onDelete)
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
            .background(colours.surface, Radius.card)
            .border(1.dp, colours.border, Radius.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                        TextChip("Pause", selected = false, onClick = { onPause(row.ratingKey) })

                    DownloadState.PAUSED, DownloadState.FAILED ->
                        TextChip("Resume", selected = false, onClick = { onResume(row.ratingKey) })

                    // A completed row shows delete.
                    DownloadState.COMPLETED -> Unit
                }
                TextChip("Delete", selected = false, onClick = { onDelete(row.ratingKey) })
            }
        }

        // Progress only while there is progress to show. A completed row does not need a
        // full bar telling it so.
        if (row.state != DownloadState.COMPLETED) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colours.border, Radius.pill),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(row.fraction)
                        .background(colours.accent, Radius.pill),
                )
            }
        }
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
