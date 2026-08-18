package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * Settings, exactly the nine entries from CLAUDE.md section 14 item 9 and nothing else.
 */
@Composable
fun SettingsScreen(
    state: SettingsScreenState,
    onMatchDisplayRateChange: (Boolean) -> Unit,
    onUnmeteredOnlyChange: (Boolean) -> Unit,
    onAudioLanguageChange: (String) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onSubtitlesOnChange: (Boolean) -> Unit,
    onSelectServer: (PlexServer) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    ContentWidthCap(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PlexTheme.sizeClass.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item { SectionHeader("Playback") }

            item {
                ToggleRow(
                    title = "Match display rate to content",
                    // Defaults on for television, off for phone and Windows.
                    subtitle = "Changes the display mode so the refresh rate divides evenly " +
                        "by the frame rate. The screen blanks briefly when it changes.",
                    checked = state.matchDisplayRate,
                    onChange = onMatchDisplayRateChange,
                )
            }

            item { SectionHeader("Downloads") }

            item {
                ToggleRow(
                    title = "Download on unmetered networks only",
                    subtitle = "Queued downloads wait rather than fail while on metered data.",
                    checked = state.unmeteredOnly,
                    onChange = onUnmeteredOnlyChange,
                )
            }

            item { SectionHeader("Language") }

            item {
                ChoiceRow(
                    title = "Preferred audio language",
                    options = LANGUAGES,
                    selected = state.audioLanguage,
                    onSelect = onAudioLanguageChange,
                )
            }

            item {
                ChoiceRow(
                    title = "Preferred subtitle language",
                    options = LANGUAGES,
                    selected = state.subtitleLanguage,
                    onSelect = onSubtitleLanguageChange,
                )
            }

            item {
                ToggleRow(
                    title = "Subtitles on by default",
                    subtitle = null,
                    checked = state.subtitlesOn,
                    onChange = onSubtitlesOnChange,
                )
            }

            // Server selection, only when there is more than one to choose between.
            if (state.servers.size > 1) {
                item { SectionHeader("Server") }
                items(state.servers.size) { index ->
                    val server = state.servers[index]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .plexFocusable(Radius.card, onClick = { onSelectServer(server) }, scaleOnFocus = false)
                            .background(colours.surface, Radius.card)
                            .border(
                                1.dp,
                                if (server.machineIdentifier == state.activeServerId) colours.accent
                                else colours.border,
                                Radius.card,
                            )
                            .padding(Spacing.md),
                    ) {
                        Column {
                            PlexText(server.name, style = PlexTheme.type.label)
                            PlexText(
                                text = if (server.owned) "Owned" else "Shared with you",
                                style = PlexTheme.type.caption,
                                colour = colours.textSecondary,
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Account") }

            item {
                Column {
                    state.signedInAs?.let {
                        PlexText(
                            text = "Signed in as $it",
                            style = PlexTheme.type.caption,
                            colour = colours.textSecondary,
                            modifier = Modifier.padding(bottom = Spacing.xs),
                        )
                    }
                    TextChip("Sign out", selected = false, onClick = onSignOut)
                }
            }

            state.updateAvailable?.let { update ->
                item {
                    // A single non-blocking notice with a download action.
                    // See CLAUDE.md section 17 point 4.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.lg)
                            .background(colours.surfaceElevated, Radius.card)
                            .border(1.dp, colours.accent, Radius.card)
                            .padding(Spacing.md),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlexText("Version $update is available", style = PlexTheme.type.label)
                            TextChip("Download", selected = true, onClick = state.onDownloadUpdate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colours = PlexTheme.colours

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(Radius.card, onClick = { onChange(!checked) }, scaleOnFocus = false)
            .background(colours.surface, Radius.card)
            .border(1.dp, colours.border, Radius.card)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            PlexText(title, style = PlexTheme.type.label)
            subtitle?.let {
                PlexText(it, style = PlexTheme.type.caption, colour = colours.textSecondary)
            }
        }
        TextChip(if (checked) "On" else "Off", selected = checked, onClick = { onChange(!checked) })
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colours = PlexTheme.colours

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colours.surface, Radius.card)
            .border(1.dp, colours.border, Radius.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlexText(title, style = PlexTheme.type.label)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { (code, label) ->
                TextChip(label, selected = code == selected, onClick = { onSelect(code) })
            }
        }
    }
}

data class SettingsScreenState(
    val matchDisplayRate: Boolean = false,
    val unmeteredOnly: Boolean = true,
    val audioLanguage: String = "eng",
    val subtitleLanguage: String = "eng",
    val subtitlesOn: Boolean = false,
    val servers: List<PlexServer> = emptyList(),
    val activeServerId: String? = null,
    val signedInAs: String? = null,
    val updateAvailable: String? = null,
    val onDownloadUpdate: () -> Unit = {},
)

/**
 * ISO 639-2 codes, which is what Plex reports in languageCode.
 *
 * A short fixed list rather than every language on earth: this is a private deployment and
 * a scrolling list of two hundred entries would be worse than useless on a remote control.
 */
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
