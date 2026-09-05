package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.download.DownloadState
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ProgressBar
import com.thotapalli.plex.ui.shared.screens.DownloadEntry
import com.thotapalli.plex.ui.shared.screens.formatBytes

/**
 * Downloads: total space used at the top, then one row per item with its title, size and state.
 * Select on a row opens its actions — Play, Pause or Resume, Delete — as a menu, so each row is
 * one target and DOWN walks the list cleanly. See CLAUDE.md section 14 item 8.
 */
@Composable
internal fun TvDownloads(
    entries: List<DownloadEntry>,
    totalBytesOnDisk: Long,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPlayDownload: (DownloadEntry) -> Unit,
    onDialogOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = rememberTvZone("downloads")
    var menuFor by remember { mutableStateOf<DownloadEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<DownloadEntry?>(null) }
    val dialogOpen = menuFor != null || confirmDelete != null
    LaunchedEffect(dialogOpen) { onDialogOpen(dialogOpen) }
    TvFirstFocus(zone, key = entries.size, enabled = !dialogOpen)

    TvZone(zone) {
        Column(
            modifier
                .fillMaxSize()
                .background(TvPalette.ground)
                .tvZone(zone)
                .verticalScroll(rememberScrollState())
                .padding(start = TvDims.contentStart, end = TvDims.overscanX, top = TvDims.overscanY, bottom = TvDims.overscanY),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PlexText("Downloads", style = PlexTheme.type.display, colour = TvPalette.text, maxLines = 1)
            PlexText(
                text = if (entries.isEmpty()) "Nothing downloaded on this device." else "${formatBytes(totalBytesOnDisk)} used",
                style = PlexTheme.type.label,
                colour = TvPalette.textDim,
                maxLines = 1,
            )
            Spacer(Modifier.height(Spacing.md))
            entries.forEach { entry ->
                val row = entry.row
                val active = row.state == DownloadState.RUNNING || row.state == DownloadState.QUEUED
                TvListRow(
                    title = entry.title,
                    key = row.ratingKey,
                    detail = buildString {
                        append(
                            when (row.state) {
                                DownloadState.QUEUED -> "Queued"
                                DownloadState.RUNNING -> "Downloading"
                                DownloadState.PAUSED -> "Paused"
                                DownloadState.COMPLETED -> "Downloaded"
                                DownloadState.FAILED -> "Failed"
                            },
                        )
                        append("  •  ")
                        if (row.state == DownloadState.COMPLETED) append(formatBytes(row.totalBytes))
                        else append("${formatBytes(row.receivedBytes)} of ${formatBytes(row.totalBytes)}")
                    },
                    onClick = { menuFor = entry },
                    trailing = { _ ->
                        if (active || row.state == DownloadState.PAUSED) {
                            Box(Modifier.width(160.dp)) {
                                ProgressBar(progress = row.fraction, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    menuFor?.let { entry ->
        val row = entry.row
        TvMenuDialog(
            title = entry.title,
            subtitle = formatBytes(row.totalBytes),
            onDismiss = { menuFor = null },
            actions = buildList {
                if (row.state == DownloadState.COMPLETED) add(TvMenuAction("Play") { onPlayDownload(entry) })
                when (row.state) {
                    DownloadState.RUNNING, DownloadState.QUEUED -> add(TvMenuAction("Pause") { onPause(row.ratingKey) })
                    DownloadState.PAUSED, DownloadState.FAILED -> add(TvMenuAction("Resume") { onResume(row.ratingKey) })
                    DownloadState.COMPLETED -> Unit
                }
                add(TvMenuAction("Delete", detail = "Removes the file from this device") { confirmDelete = entry })
            },
        )
    }
    confirmDelete?.let { entry ->
        TvConfirmDialog(
            title = "Delete download?",
            body = "${entry.title} will be removed from this device. It stays on the server.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(entry.row.ratingKey) },
            onDismiss = { confirmDelete = null },
        )
    }
}
