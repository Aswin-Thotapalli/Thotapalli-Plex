package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.DiagnosticEvent
import com.thotapalli.plex.core.model.Diagnostics
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.ServerUpdate
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.screens.SettingsScreenState

/**
 * Settings for the remote: one scrolling column of rows, every row a target, switches flipped
 * by select and choices opened as a focus-trapped list. Exactly the settings CLAUDE.md section 14
 * item 9 names, plus the server's own update and the diagnostics log, which is how a no-telemetry
 * app explains itself on a device with no logcat.
 */
@Composable
internal fun TvSettings(
    state: SettingsScreenState,
    onMatchDisplayRateChange: (Boolean) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onTunnelledPlaybackChange: (Boolean) -> Unit,
    onAudioPassthroughChange: (Boolean) -> Unit,
    onUnmeteredOnlyChange: (Boolean) -> Unit,
    onAudioLanguageChange: (String) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onSubtitlesOnChange: (Boolean) -> Unit,
    onStreamingBitrateChange: (Int?) -> Unit,
    onSubtitleScaleChange: (Int) -> Unit,
    onSelectServer: (PlexServer) -> Unit,
    onSignOut: () -> Unit,
    serverUpdate: ServerUpdate?,
    serverUpdateApplying: Boolean,
    onCheckServerUpdate: () -> Unit,
    onApplyServerUpdate: () -> Unit,
    onDialogOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = rememberTvZone("settings")
    var choice by remember { mutableStateOf<Choice?>(null) }
    LaunchedEffect(choice) { onDialogOpen(choice != null) }
    TvFirstFocus(zone, enabled = choice == null)
    val events by Diagnostics.events.collectAsState()

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
            PlexText("Settings", style = PlexTheme.type.display, colour = TvPalette.text, maxLines = 1)
            Spacer(Modifier.height(Spacing.sm))

            val rows = Modifier.widthIn(max = 900.dp)

            Section("Playback")
            TvListRow(
                title = "Auto play next episode",
                key = "autoplay",
                detail = "When an episode ends, count down ten seconds and play the next one. Off: the next episode is offered and waits.",
                onClick = { onAutoPlayNextChange(!state.autoPlayNext) },
                trailing = { focused -> TvSwitch(on = state.autoPlayNext, focused = focused) },
                modifier = rows,
            )
            if (state.showMatchDisplayRate) {
                TvListRow(
                    title = "Match display rate to content",
                    key = "match-rate",
                    detail = "Switches the display mode so the refresh rate divides evenly by the frame rate. The screen blanks briefly.",
                    onClick = { onMatchDisplayRateChange(!state.matchDisplayRate) },
                    trailing = { focused -> TvSwitch(on = state.matchDisplayRate, focused = focused) },
                    modifier = rows,
                )
            }
            TvListRow(
                title = "Dolby and DTS passthrough",
                key = "passthrough",
                detail = "Send Dolby and DTS to the TV or receiver as the original bitstream. Turn off if sound takes seconds to return after a seek.",
                onClick = { onAudioPassthroughChange(!state.audioPassthrough) },
                trailing = { focused -> TvSwitch(on = state.audioPassthrough, focused = focused) },
                modifier = rows,
            )
            TvListRow(
                title = "Tunnelled video",
                key = "tunnelling",
                detail = "Let the TV hardware sync audio and video. Off by default: on many TVs the picture runs ahead with silent audio after a seek. Applies to the next playback.",
                onClick = { onTunnelledPlaybackChange(!state.tunnelledPlayback) },
                trailing = { focused -> TvSwitch(on = state.tunnelledPlayback, focused = focused) },
                modifier = rows,
            )
            TvListRow(
                title = "Streaming quality",
                key = "quality",
                detail = "The most the server will send when it has to transcode.",
                onClick = { choice = Choice.QUALITY },
                trailing = { focused -> TvValue(bitrateLabel(state.streamingMaxBitrateKbps), focused) },
                modifier = rows,
            )

            Section("Language")
            TvListRow(
                title = "Preferred audio language",
                key = "audio-lang",
                onClick = { choice = Choice.AUDIO },
                trailing = { focused -> TvValue(languageLabel(state.audioLanguage), focused) },
                modifier = rows,
            )
            TvListRow(
                title = "Preferred subtitle language",
                key = "sub-lang",
                onClick = { choice = Choice.SUBTITLE },
                trailing = { focused -> TvValue(languageLabel(state.subtitleLanguage), focused) },
                modifier = rows,
            )
            TvListRow(
                title = "Subtitles on by default",
                key = "subs-on",
                detail = "Show subtitles when a matching track is available.",
                onClick = { onSubtitlesOnChange(!state.subtitlesOn) },
                trailing = { focused -> TvSwitch(on = state.subtitlesOn, focused = focused) },
                modifier = rows,
            )
            TvListRow(
                title = "Subtitle size",
                key = "sub-size",
                onClick = { choice = Choice.SUBTITLE_SIZE },
                trailing = { focused -> TvValue("${state.subtitleScalePercent}%", focused) },
                modifier = rows,
            )

            Section("Downloads")
            TvListRow(
                title = "Download on unmetered networks only",
                key = "unmetered",
                detail = "Queued downloads wait rather than fail on metered data.",
                onClick = { onUnmeteredOnlyChange(!state.unmeteredOnly) },
                trailing = { focused -> TvSwitch(on = state.unmeteredOnly, focused = focused) },
                modifier = rows,
            )

            Section("Server")
            if (state.servers.size > 1) {
                state.servers.forEach { server ->
                    val active = server.machineIdentifier == state.activeServerId
                    TvListRow(
                        title = server.name,
                        key = "server-" + server.machineIdentifier,
                        detail = if (server.owned) "Your server" else "Shared with you",
                        selected = active,
                        onClick = { onSelectServer(server) },
                        trailing = { focused ->
                            if (active) TvGlyphIcon(TvGlyph.CHECK, tint = if (focused) TvPalette.ink else TvPalette.gold, size = 22.dp)
                        },
                        modifier = rows,
                    )
                }
            }
            TvListRow(
                title = "Plex Media Server update",
                key = "server-update",
                detail = when {
                    serverUpdateApplying -> "Updating — the server is restarting."
                    serverUpdate != null -> "Version ${serverUpdate.version} is available."
                    else -> "Check whether the server has an update."
                },
                enabled = !serverUpdateApplying,
                onClick = { if (serverUpdate != null) onApplyServerUpdate() else onCheckServerUpdate() },
                trailing = { focused -> TvValue(if (serverUpdate != null) "Update now" else "Check", focused) },
                modifier = rows,
            )

            Section("Account")
            TvListRow(
                title = state.signedInAs ?: "Signed in",
                key = "account",
                detail = state.signedInEmail,
                onClick = onSignOut,
                trailing = { focused -> TvValue("Sign out", focused) },
                modifier = rows,
            )

            Section("App")
            TvListRow(
                title = "Thotapalli Plex",
                key = "app-update",
                detail = state.updateAvailable?.let { "Version $it is available." } ?: "Up to date.",
                enabled = state.updateAvailable != null,
                onClick = state.onDownloadUpdate,
                trailing = { focused -> if (state.updateAvailable != null) TvValue("Download", focused) },
                modifier = rows,
            )

            Section("Diagnostics")
            TvListRow(
                title = if (events.isEmpty()) "No events this session" else "${events.size} events this session",
                key = "diag-clear",
                detail = "Connection, playback, cache and download events, newest first.",
                enabled = events.isNotEmpty(),
                onClick = { Diagnostics.clear() },
                trailing = { focused -> if (events.isNotEmpty()) TvValue("Clear", focused) },
                modifier = rows,
            )
            events.asReversed().take(MAX_EVENTS).forEach { event -> DiagnosticLine(event, rows) }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    when (choice) {
        Choice.QUALITY -> TvChoiceDialog(
            title = "Streaming quality",
            options = BITRATE_OPTIONS,
            selected = bitrateToken(state.streamingMaxBitrateKbps),
            onSelect = { onStreamingBitrateChange(if (it == ORIGINAL) null else it.toIntOrNull()) },
            onDismiss = { choice = null },
        )
        Choice.AUDIO -> TvChoiceDialog(
            title = "Preferred audio language",
            options = LANGUAGES,
            selected = state.audioLanguage,
            onSelect = onAudioLanguageChange,
            onDismiss = { choice = null },
        )
        Choice.SUBTITLE -> TvChoiceDialog(
            title = "Preferred subtitle language",
            options = LANGUAGES,
            selected = state.subtitleLanguage,
            onSelect = onSubtitleLanguageChange,
            onDismiss = { choice = null },
        )
        Choice.SUBTITLE_SIZE -> TvChoiceDialog(
            title = "Subtitle size",
            options = SUBTITLE_SIZES,
            selected = state.subtitleScalePercent.toString(),
            onSelect = { it.toIntOrNull()?.let(onSubtitleScaleChange) },
            onDismiss = { choice = null },
        )
        null -> Unit
    }
}

private enum class Choice { QUALITY, AUDIO, SUBTITLE, SUBTITLE_SIZE }

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Spacing.sm))
    TvCaption(title.uppercase())
}

@Composable
private fun DiagnosticLine(event: DiagnosticEvent, modifier: Modifier) {
    PlexText(
        text = "${event.category.name}  ${event.message}",
        style = PlexTheme.type.caption,
        colour = TvPalette.textDim,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xxs),
    )
}

private const val MAX_EVENTS = 12
private const val ORIGINAL = "original"

/** ISO 639-2, which is what Plex reports. A short list: two hundred entries on a remote is worse than none. */
private val LANGUAGES = listOf(
    "eng" to "English",
    "hin" to "Hindi",
    "tel" to "Telugu",
    "tam" to "Tamil",
    "spa" to "Spanish",
    "fra" to "French",
    "deu" to "German",
    "jpn" to "Japanese",
)

private val BITRATE_OPTIONS = listOf(
    ORIGINAL to "Original",
    "20000" to "20 Mbps",
    "8000" to "8 Mbps",
    "4000" to "4 Mbps",
    "2000" to "2 Mbps",
    "720" to "720 Kbps",
)

private val SUBTITLE_SIZES = listOf("75" to "75%", "100" to "100%", "125" to "125%", "150" to "150%", "200" to "200%")

private fun bitrateToken(kbps: Int?): String = kbps?.toString() ?: ORIGINAL
private fun bitrateLabel(kbps: Int?): String = BITRATE_OPTIONS.firstOrNull { it.first == bitrateToken(kbps) }?.second ?: "$kbps kbps"
private fun languageLabel(code: String): String = LANGUAGES.firstOrNull { it.first == code }?.second ?: code
