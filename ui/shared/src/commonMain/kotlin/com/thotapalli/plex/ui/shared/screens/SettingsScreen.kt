package com.thotapalli.plex.ui.shared.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.ServerUpdate
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.ThemeMode
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ContentWidthCap
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * Settings, exactly the nine entries from CLAUDE.md section 14 item 9 and nothing else.
 *
 * The clean, modern layout: a page title under an amber rule, then a stack of sections. Each
 * section carries a small accent bar and its name, and its rows live inside one solid
 * [GlassRole.CARD]. Every row leads with its glyph in an amber-tinted rounded square, so the
 * amber accent reads as the one colour the interface owns while the surfaces stay quiet.
 */
@Composable
fun SettingsScreen(
    state: SettingsScreenState,
    onMatchDisplayRateChange: (Boolean) -> Unit,
    onUnmeteredOnlyChange: (Boolean) -> Unit,
    onAudioLanguageChange: (String) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onSubtitlesOnChange: (Boolean) -> Unit,
    onStreamingBitrateChange: (Int?) -> Unit,
    onSubtitleScaleChange: (Int) -> Unit,
    onSubtitleForegroundChange: (Long) -> Unit,
    onSubtitleBackgroundOpacityChange: (Int) -> Unit,
    onSelectServer: (PlexServer) -> Unit,
    onSignOut: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    serverUpdate: com.thotapalli.plex.core.model.ServerUpdate?,
    serverUpdateApplying: Boolean,
    onCheckServerUpdate: () -> Unit,
    onApplyServerUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tablet, desktop and television take the grouped wide layout; the compact phone body below is
    // left exactly as it was. See CLAUDE.md sections 13 and 14.
    if (PlexTheme.sizeClass != SizeClass.COMPACT) {
        WideSettings(
            state = state,
            onMatchDisplayRateChange = onMatchDisplayRateChange,
            onUnmeteredOnlyChange = onUnmeteredOnlyChange,
            onAudioLanguageChange = onAudioLanguageChange,
            onSubtitleLanguageChange = onSubtitleLanguageChange,
            onSubtitlesOnChange = onSubtitlesOnChange,
            onStreamingBitrateChange = onStreamingBitrateChange,
            onSubtitleScaleChange = onSubtitleScaleChange,
            onSubtitleForegroundChange = onSubtitleForegroundChange,
            onSubtitleBackgroundOpacityChange = onSubtitleBackgroundOpacityChange,
            onSelectServer = onSelectServer,
            onSignOut = onSignOut,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            serverUpdate = serverUpdate,
            serverUpdateApplying = serverUpdateApplying,
            onCheckServerUpdate = onCheckServerUpdate,
            onApplyServerUpdate = onApplyServerUpdate,
            modifier = modifier,
        )
        return
    }

    // Auto play next episode and Delete watched downloads have no backend flag yet, so they are
    // held in local UI state for a faithful visual match. TODO: persist alongside the other prefs.
    var autoPlayNext by remember { mutableStateOf(true) }
    var deleteWatched by remember { mutableStateOf(false) }

    ContentWidthCap(modifier) {
        val edge = PlexTheme.sizeClass.screenPadding
        LazyColumn(
            modifier = Modifier.fillMaxSize().material(GlassRole.GROUND).glassSource(),
            // Comfortable bottom room so the last section clears the floating bottom tab bar on
            // compact. Harmless on wider classes where no bar overlaps.
            contentPadding = PaddingValues(start = edge, top = edge, end = edge, bottom = 96.dp),
            // The airy gap that separates one section from the next.
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { PageTitle("Settings", "Customize your app experience") }

            // Appearance sits first: the theme is the setting a viewer reaches for first.
            item {
                SettingsSection("Appearance") {
                    SettingsCard {
                        AppearanceRow(selected = themeMode, onSelect = onThemeModeChange)
                    }
                }
            }

            item {
                SettingsSection("Playback") {
                    SettingsCard {
                        ToggleRow(
                            glyph = SettingGlyph.PLAYBACK,
                            title = "Match display rate to content",
                            // Defaults on for television, off for phone and Windows.
                            subtitle = "Switches the display mode so the refresh rate divides " +
                                "evenly by the frame rate. The screen blanks briefly.",
                            checked = state.matchDisplayRate,
                            onChange = onMatchDisplayRateChange,
                        )
                        RowDivider()
                        ToggleRow(
                            glyph = SettingGlyph.AUTOPLAY,
                            title = "Auto play next episode",
                            subtitle = "Automatically play the next episode in a series.",
                            checked = autoPlayNext,
                            // TODO: persist — no backend flag yet, held in local UI state.
                            onChange = { autoPlayNext = it },
                        )
                    }
                }
            }

            // Streaming quality: the default remote max bitrate. See CLAUDE.md section 10.
            item {
                SettingsSection("Quality") {
                    SettingsCard {
                        PillRow(
                            glyph = SettingGlyph.QUALITY,
                            title = "Streaming quality",
                            options = BITRATE_OPTIONS,
                            selected = bitrateToken(state.streamingMaxBitrateKbps),
                            onSelect = { onStreamingBitrateChange(tokenToBitrate(it)) },
                        )
                    }
                }
            }

            item {
                SettingsSection("Downloads") {
                    SettingsCard {
                        ToggleRow(
                            glyph = SettingGlyph.DOWNLOADS,
                            title = "Download on unmetered networks only",
                            subtitle = "Queued downloads wait rather than fail on metered data.",
                            checked = state.unmeteredOnly,
                            onChange = onUnmeteredOnlyChange,
                        )
                        RowDivider()
                        ToggleRow(
                            glyph = SettingGlyph.DELETE,
                            title = "Delete watched downloads",
                            subtitle = "Automatically remove downloads after watching.",
                            checked = deleteWatched,
                            // TODO: persist — no backend flag yet, held in local UI state.
                            onChange = { deleteWatched = it },
                        )
                    }
                }
            }

            item {
                SettingsSection("Language") {
                    SettingsCard {
                        PillRow(
                            glyph = SettingGlyph.LANGUAGE,
                            title = "Preferred audio language",
                            options = LANGUAGES,
                            selected = state.audioLanguage,
                            onSelect = onAudioLanguageChange,
                        )
                        RowDivider()
                        PillRow(
                            glyph = SettingGlyph.LANGUAGE,
                            title = "Preferred subtitle language",
                            options = LANGUAGES,
                            selected = state.subtitleLanguage,
                            onSelect = onSubtitleLanguageChange,
                        )
                        RowDivider()
                        ToggleRow(
                            glyph = SettingGlyph.LANGUAGE,
                            title = "Subtitles on by default",
                            subtitle = null,
                            checked = state.subtitlesOn,
                            onChange = onSubtitlesOnChange,
                        )
                    }
                }
            }

            // Subtitle appearance. These map onto core/playback SubtitleStyle. See CLAUDE.md §14.
            item {
                SettingsSection("Subtitles") {
                    SettingsCard {
                        PillRow(
                            glyph = SettingGlyph.SUBTITLE,
                            title = "Text size",
                            options = SUBTITLE_SIZE_OPTIONS,
                            selected = state.subtitleScalePercent.toString(),
                            onSelect = { onSubtitleScaleChange(it.toInt()) },
                        )
                        RowDivider()
                        PillRow(
                            glyph = SettingGlyph.SUBTITLE,
                            title = "Text color",
                            options = SUBTITLE_COLOR_OPTIONS,
                            selected = state.subtitleForegroundArgb.toString(),
                            onSelect = { onSubtitleForegroundChange(it.toLong()) },
                        )
                        RowDivider()
                        PillRow(
                            glyph = SettingGlyph.SUBTITLE,
                            title = "Background",
                            options = SUBTITLE_BACKGROUND_OPTIONS,
                            selected = state.subtitleBackgroundOpacityPercent.toString(),
                            onSelect = { onSubtitleBackgroundOpacityChange(it.toInt()) },
                        )
                    }
                }
            }

            item {
                SettingsSection("Account") {
                    SettingsCard {
                        ProfileRow(
                            name = state.signedInAs,
                            email = state.signedInEmail,
                            onOpen = onSignOut,
                        )
                        RowDivider()
                        ManageAccountRow(onManage = onSignOut)
                    }
                }
            }

            // The Server section carries the server picker (only when there is more than one to
            // choose between) and the always-available server-update controls.
            item {
                SettingsSection("Server") {
                    SettingsCard {
                        if (state.servers.size > 1) {
                            state.servers.forEachIndexed { index, server ->
                                ServerPickerRow(
                                    server = server,
                                    active = server.machineIdentifier == state.activeServerId,
                                    onSelect = { onSelectServer(server) },
                                )
                                RowDivider()
                            }
                        }
                        ServerUpdateBody(
                            update = serverUpdate,
                            applying = serverUpdateApplying,
                            onCheck = onCheckServerUpdate,
                            onApply = onApplyServerUpdate,
                        )
                    }
                }
            }

            // A single non-blocking notice for a newer client build. See CLAUDE.md section 17.
            state.updateAvailable?.let { update ->
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .material(GlassRole.CARD, shape = Radius.card)
                            .border(1.dp, PlexTheme.colours.accent, Radius.card)
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

/* ----------------------------------------------------------------------------------------------
 * Wide layout (tablet, desktop, television)
 *
 * The TV/tablet Settings: a large page header under an amber rule, then grouped sections. Each
 * group carries an uppercase, letter-spaced label above one rounded [GlassRole.CARD] whose rows
 * are split by hairline dividers. Every row is [plexFocusable] so a directional pad draws the
 * accent focus ring. See CLAUDE.md sections 9, 11, 12, 13 and 14.
 * ------------------------------------------------------------------------------------------- */

@Composable
private fun WideSettings(
    state: SettingsScreenState,
    onMatchDisplayRateChange: (Boolean) -> Unit,
    onUnmeteredOnlyChange: (Boolean) -> Unit,
    onAudioLanguageChange: (String) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onSubtitlesOnChange: (Boolean) -> Unit,
    onStreamingBitrateChange: (Int?) -> Unit,
    onSubtitleScaleChange: (Int) -> Unit,
    onSubtitleForegroundChange: (Long) -> Unit,
    onSubtitleBackgroundOpacityChange: (Int) -> Unit,
    onSelectServer: (PlexServer) -> Unit,
    onSignOut: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    serverUpdate: ServerUpdate?,
    serverUpdateApplying: Boolean,
    onCheckServerUpdate: () -> Unit,
    onApplyServerUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Neither of these has a backend flag yet, so they mirror the compact branch: local UI state
    // for a faithful visual match. TODO persist via SettingsStore alongside the other prefs.
    var autoPlayNext by remember { mutableStateOf(true) }
    var showBackgrounds by remember { mutableStateOf(true) }

    ContentWidthCap(modifier) {
        val edge = PlexTheme.sizeClass.screenPadding
        LazyColumn(
            modifier = Modifier.fillMaxSize().material(GlassRole.GROUND).glassSource(),
            contentPadding = PaddingValues(start = edge, top = edge, end = edge, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { WidePageHeader() }

            // Appearance leads: the theme is what a viewer reaches for first.
            item {
                GroupedSection("Appearance") {
                    AppearanceRow(selected = themeMode, onSelect = onThemeModeChange)
                    RowDivider()
                    ToggleRow(
                        glyph = SettingGlyph.APPEARANCE,
                        title = "Show backgrounds",
                        subtitle = "Show artwork backdrops behind detail screens.",
                        checked = showBackgrounds,
                        // TODO persist via SettingsStore — no backend flag yet, held in local state.
                        onChange = { showBackgrounds = it },
                    )
                }
            }

            item {
                GroupedSection("Playback") {
                    ToggleRow(
                        glyph = SettingGlyph.PLAYBACK,
                        title = "Match display rate to content",
                        subtitle = "Switches the display mode so the refresh rate divides evenly " +
                            "by the frame rate. The screen blanks briefly.",
                        checked = state.matchDisplayRate,
                        onChange = onMatchDisplayRateChange,
                    )
                    RowDivider()
                    ToggleRow(
                        glyph = SettingGlyph.AUTOPLAY,
                        title = "Allow autoplay next episode",
                        subtitle = "Automatically play the next episode in a series.",
                        checked = autoPlayNext,
                        // TODO persist via SettingsStore — no backend flag yet, held in local state.
                        onChange = { autoPlayNext = it },
                    )
                }
            }

            // Streaming quality: the default remote max bitrate. See CLAUDE.md section 10.
            item {
                GroupedSection("Quality") {
                    ValueRow(
                        glyph = SettingGlyph.QUALITY,
                        title = "Streaming quality",
                        value = bitrateLabel(state.streamingMaxBitrateKbps),
                        // No picker screen exists yet; tapping cycles the fixed list, exercising the
                        // real callback. TODO replace with a quality picker.
                        onOpen = { onStreamingBitrateChange(nextBitrate(state.streamingMaxBitrateKbps)) },
                    )
                }
            }

            item {
                GroupedSection("Downloads") {
                    ToggleRow(
                        glyph = SettingGlyph.DOWNLOADS,
                        title = "Download on unmetered networks only",
                        subtitle = "Queued downloads wait rather than fail on metered data.",
                        checked = state.unmeteredOnly,
                        onChange = onUnmeteredOnlyChange,
                    )
                    RowDivider()
                    ValueRow(
                        glyph = SettingGlyph.DOWNLOADS,
                        title = "Download location",
                        // No download-location field on the state yet; display the platform default.
                        // TODO persist via SettingsStore and open a picker.
                        value = "Internal Storage",
                        onOpen = { /* TODO open a download-location picker */ },
                    )
                }
            }

            item {
                GroupedSection("Language") {
                    ValueRow(
                        glyph = SettingGlyph.LANGUAGE,
                        title = "Preferred audio language",
                        value = languageLabel(state.audioLanguage),
                        // No picker screen exists yet; tapping cycles the fixed list, so the real
                        // callback is exercised. TODO replace with a language picker.
                        onOpen = { onAudioLanguageChange(nextLanguage(state.audioLanguage)) },
                    )
                    RowDivider()
                    ValueRow(
                        glyph = SettingGlyph.LANGUAGE,
                        title = "Preferred subtitle language",
                        value = languageLabel(state.subtitleLanguage),
                        onOpen = { onSubtitleLanguageChange(nextLanguage(state.subtitleLanguage)) },
                    )
                    RowDivider()
                    ToggleRow(
                        glyph = SettingGlyph.LANGUAGE,
                        title = "Subtitles on by default",
                        subtitle = "Show subtitles when a matching track is available.",
                        checked = state.subtitlesOn,
                        onChange = onSubtitlesOnChange,
                    )
                }
            }

            // Subtitle appearance. These map onto core/playback SubtitleStyle. See CLAUDE.md §14.
            item {
                GroupedSection("Subtitles") {
                    ValueRow(
                        glyph = SettingGlyph.SUBTITLE,
                        title = "Text size",
                        value = subtitleSizeLabel(state.subtitleScalePercent),
                        // Tapping cycles the fixed list; the real callback runs. TODO add a picker.
                        onOpen = { onSubtitleScaleChange(nextSubtitleSize(state.subtitleScalePercent)) },
                    )
                    RowDivider()
                    ValueRow(
                        glyph = SettingGlyph.SUBTITLE,
                        title = "Text color",
                        value = subtitleColorLabel(state.subtitleForegroundArgb),
                        onOpen = { onSubtitleForegroundChange(nextSubtitleColor(state.subtitleForegroundArgb)) },
                    )
                    RowDivider()
                    ValueRow(
                        glyph = SettingGlyph.SUBTITLE,
                        title = "Background",
                        value = subtitleBackgroundLabel(state.subtitleBackgroundOpacityPercent),
                        onOpen = { onSubtitleBackgroundOpacityChange(nextSubtitleBackground(state.subtitleBackgroundOpacityPercent)) },
                    )
                }
            }

            // The Server section carries the server picker (only when there is more than one to
            // choose between) and the always-available server-update controls, matching compact.
            item {
                GroupedSection("Server") {
                    if (state.servers.size > 1) {
                        state.servers.forEach { server ->
                            ServerPickerRow(
                                server = server,
                                active = server.machineIdentifier == state.activeServerId,
                                onSelect = { onSelectServer(server) },
                            )
                            RowDivider()
                        }
                    }
                    ServerUpdateBody(
                        update = serverUpdate,
                        applying = serverUpdateApplying,
                        onCheck = onCheckServerUpdate,
                        onApply = onApplyServerUpdate,
                    )
                }
            }

            item {
                GroupedSection("Account") {
                    // The profile row already shows the signed-in email and a chevron; opening it
                    // runs the account action (sign-out).
                    ProfileRow(
                        name = state.signedInAs,
                        email = state.signedInEmail,
                        onOpen = onSignOut,
                    )
                }
            }

            // A single non-blocking notice for a newer client build, in the About/footer area.
            // See CLAUDE.md section 17.
            state.updateAvailable?.let { update ->
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .material(GlassRole.CARD, shape = Radius.card)
                            .border(1.dp, PlexTheme.colours.accent, Radius.card)
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

            item { VersionFooter(state) }
        }
    }
}

/** The wide page heading: a tall amber rule beside the title, a body-weight subtitle beneath. */
@Composable
private fun WidePageHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(PlexTheme.colours.accent, Radius.pill),
            )
            Spacer(Modifier.width(Spacing.sm))
            PlexText(text = "Settings", style = PlexTheme.type.display)
        }
        PlexText(
            text = "Customize your app experience",
            style = PlexTheme.type.body,
            colour = PlexTheme.colours.textSecondary,
            // Indent past the accent rule so the subtitle sits under the title, not the bar.
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.xxs),
        )
    }
}

/** A grouped section: an uppercase, letter-spaced label above one rounded card of rows. */
@Composable
private fun GroupedSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PlexText(
            text = title.uppercase(),
            style = PlexTheme.type.caption.copy(letterSpacing = 1.5.sp),
            colour = PlexTheme.colours.textSecondary,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        SettingsCard { content() }
    }
}

/** A row whose control is a current value beside a chevron — a read-only display or a picker entry. */
@Composable
private fun ValueRow(
    glyph: SettingGlyph,
    title: String,
    value: String,
    onOpen: () -> Unit,
) {
    SettingRow(
        glyph = glyph,
        title = title,
        subtitle = null,
        modifier = Modifier.plexFocusable(Radius.card, onClick = onOpen, scaleOnFocus = false),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlexText(
                    text = value,
                    style = PlexTheme.type.label,
                    colour = PlexTheme.colours.textSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Spacing.xs))
                Chevron()
            }
        },
    )
}

/**
 * The footer version line. The client build's version name/code is not surfaced to this screen, so
 * only the app name and update status are shown honestly rather than a hardcoded number.
 * TODO surface the real "Version {name} ({code})" via SettingsScreenState and render it here.
 */
@Composable
private fun VersionFooter(state: SettingsScreenState) {
    val status = state.updateAvailable?.let { "Update $it available" } ?: "Up to date"
    PlexText(
        text = "Thotapalli Plex • $status",
        style = PlexTheme.type.caption,
        colour = PlexTheme.colours.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm, start = Spacing.xs),
    )
}

/** The human label for a stored language code, falling back to the raw code when unknown. */
private fun languageLabel(code: String): String =
    LANGUAGES.firstOrNull { it.first == code }?.second ?: code

/** The next language code in the fixed list, wrapping around — a stand-in for a full picker. */
private fun nextLanguage(code: String): String {
    val index = LANGUAGES.indexOfFirst { it.first == code }
    val next = if (index < 0) 0 else (index + 1) % LANGUAGES.size
    return LANGUAGES[next].first
}

/* ----------------------------------------------------------------------------------------------
 * Structure
 * ------------------------------------------------------------------------------------------- */

/**
 * The page heading: a tall amber rule beside the title in the display face, and a quiet subtitle
 * line directly beneath, aligned with the title text.
 */
@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .background(PlexTheme.colours.accent, Radius.pill),
            )
            Spacer(Modifier.width(Spacing.sm))
            PlexText(text = title, style = PlexTheme.type.display)
        }
        PlexText(
            text = subtitle,
            style = PlexTheme.type.caption,
            colour = PlexTheme.colours.textSecondary,
            // Indent past the accent rule so the subtitle sits under the title, not the bar.
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.xxs),
        )
    }
}

/** A section: its accent-barred header, then a single body [content]. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHead(title)
        content()
    }
}

/** The section header: a small vertical amber bar and the name in bold. */
@Composable
private fun SectionHead(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .background(PlexTheme.colours.accent, Radius.pill),
        )
        Spacer(Modifier.width(Spacing.xs))
        PlexText(text = title, style = PlexTheme.type.title)
    }
}

/** One rounded card holding a section's rows. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .material(GlassRole.CARD, shape = Radius.card),
    ) {
        content()
    }
}

/** A hairline between rows inside a card, inset to line up under the row text. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = PlexTheme.colours.border,
        modifier = Modifier.padding(horizontal = Spacing.md),
    )
}

/* ----------------------------------------------------------------------------------------------
 * Rows
 * ------------------------------------------------------------------------------------------- */

/** The leading glyph of a row, in an amber-tinted rounded square. */
@Composable
private fun RowIcon(glyph: SettingGlyph) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colours.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph = glyph, size = 22.dp, tint = colours.accent)
    }
}

/** Icon square, a title, an optional subtitle, and a trailing control. */
@Composable
private fun SettingRow(
    glyph: SettingGlyph,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colours = PlexTheme.colours
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(glyph)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            PlexText(title, style = PlexTheme.type.label)
            subtitle?.let {
                PlexText(
                    it,
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(Spacing.md))
            it()
        }
    }
}

/** A row whose control is an amber toggle switch. */
@Composable
private fun ToggleRow(
    glyph: SettingGlyph,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        glyph = glyph,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.plexFocusable(
            Radius.card,
            onClick = { onChange(!checked) },
            scaleOnFocus = false,
        ),
        trailing = { PlexSwitch(checked = checked, onChange = onChange) },
    )
}

/**
 * A row whose control is a wrapping set of choice pills below the title. When the option list is
 * long it collapses to the first few plus a "More" pill; tapping "More" reveals the rest. The
 * selected option is always kept visible even while collapsed, so the current choice never hides.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PillRow(
    glyph: SettingGlyph,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // Show the first few; anything beyond folds behind a "More" affordance.
    val collapsedCount = 4
    var expanded by remember { mutableStateOf(false) }
    val isLong = options.size > collapsedCount + 1
    val shown: List<Pair<String, String>> = when {
        expanded || !isLong -> options
        else -> {
            val head = options.take(collapsedCount)
            // Swap the selected option in when it lives past the fold, so it stays on screen.
            if (head.any { it.first == selected }) head
            else head.dropLast(1) + (options.firstOrNull { it.first == selected } ?: head.last())
        }
    }
    val hidden = options.size - shown.size

    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        RowIcon(glyph)
        Spacer(Modifier.width(Spacing.md))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PlexText(title, style = PlexTheme.type.label)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                shown.forEach { (code, label) ->
                    TextChip(label, selected = code == selected, onClick = { onSelect(code) })
                }
                if (isLong && !expanded) {
                    TextChip("+$hidden  More", selected = false, onClick = { expanded = true })
                } else if (isLong) {
                    TextChip("Less", selected = false, onClick = { expanded = false })
                }
            }
        }
    }
}

/**
 * The profile row: a circular amber avatar carrying the account's initial, the name and email, and
 * a trailing chevron. Tapping opens the account action.
 */
@Composable
private fun ProfileRow(name: String?, email: String?, onOpen: () -> Unit) {
    val colours = PlexTheme.colours
    val display = name?.takeIf { it.isNotBlank() } ?: "Plex account"
    val initial = display.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(Radius.card, onClick = onOpen, scaleOnFocus = false)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colours.accent),
            contentAlignment = Alignment.Center,
        ) {
            PlexText(
                text = initial,
                style = PlexTheme.type.title,
                colour = if (colours.isDark) colours.background else colours.surface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            PlexText(display, style = PlexTheme.type.label, maxLines = 1)
            email?.takeIf { it.isNotBlank() }?.let {
                PlexText(
                    text = it,
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Chevron()
    }
}

/** The manage-account row: a shield glyph, a subtitle, and a chevron into the account action. */
@Composable
private fun ManageAccountRow(onManage: () -> Unit) {
    SettingRow(
        glyph = SettingGlyph.SHIELD,
        title = "Manage Account",
        subtitle = "Security, devices and more",
        modifier = Modifier.plexFocusable(Radius.card, onClick = onManage, scaleOnFocus = false),
        trailing = { Chevron() },
    )
}

/** A small trailing chevron drawn in the quiet secondary colour. */
@Composable
private fun Chevron() {
    val tint = PlexTheme.colours.textSecondary
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(
            width = size.minDimension * 0.10f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val path = Path().apply {
            moveTo(size.width * 0.40f, size.height * 0.28f)
            lineTo(size.width * 0.64f, size.height * 0.50f)
            lineTo(size.width * 0.40f, size.height * 0.72f)
        }
        drawPath(path, tint, style = stroke)
    }
}

/** A selectable server, shown only when more than one is reachable. */
@Composable
private fun ServerPickerRow(
    server: PlexServer,
    active: Boolean,
    onSelect: () -> Unit,
) {
    val colours = PlexTheme.colours
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(Radius.card, onClick = onSelect, scaleOnFocus = false)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(SettingGlyph.SERVER)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            PlexText(server.name, style = PlexTheme.type.label)
            PlexText(
                text = if (server.owned) "Owned" else "Shared with you",
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        TextChip(if (active) "Active" else "Use", selected = active, onClick = onSelect)
    }
}

/**
 * The server-update controls as card rows. A "Check for updates" row is always present; when the
 * server reports a pending update a notice names the version (and notes) and offers to apply it in
 * place. Applying restarts the server, so while it runs the apply action becomes a progress row.
 */
@Composable
private fun ServerUpdateBody(
    update: ServerUpdate?,
    applying: Boolean,
    onCheck: () -> Unit,
    onApply: () -> Unit,
) {
    val colours = PlexTheme.colours

    // Always offer a manual check.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plexFocusable(Radius.card, onClick = onCheck, scaleOnFocus = false)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(SettingGlyph.SERVER)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            PlexText("Check for updates", style = PlexTheme.type.label)
            update?.takeIf { it.available != true }?.let {
                PlexText(
                    text = "Server is up to date",
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        TextChip("Check", selected = false, onClick = onCheck)
    }

    if (update?.available == true) {
        RowDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(SettingGlyph.SERVER)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    PlexText("Update available", style = PlexTheme.type.label)
                    update.version?.let {
                        PlexText(
                            text = "Version $it",
                            style = PlexTheme.type.caption,
                            colour = colours.textSecondary,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
            update.notes?.let {
                PlexText(text = it, style = PlexTheme.type.caption, colour = colours.textSecondary)
            }

            when {
                applying -> {
                    // Applying restarts the server; the connection may drop meanwhile.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PlexText(text = "Updating server…", style = PlexTheme.type.label, maxLines = 1)
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f).clip(Radius.pill),
                            color = colours.accent,
                            trackColor = colours.surface,
                        )
                    }
                }

                update.canApply -> TextChip("Update server now", selected = true, onClick = onApply)

                else -> PlexText(
                    text = "This update must be applied on the server itself.",
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                )
            }
        }
    }
}

/* ----------------------------------------------------------------------------------------------
 * Controls
 * ------------------------------------------------------------------------------------------- */

/**
 * The theme control: an icon-square row with a full-width three-way segmented switch beneath.
 * The chosen segment fills amber; the others stay quiet. See CLAUDE.md section 2 and section 12.
 */
@Composable
private fun AppearanceRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val colours = PlexTheme.colours
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(SettingGlyph.APPEARANCE)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                PlexText("Theme", style = PlexTheme.type.label)
                PlexText(
                    text = "System, light, or dark",
                    style = PlexTheme.type.caption,
                    colour = colours.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
                .material(GlassRole.CHIP, shape = Radius.pill)
                .padding(Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            ThemeSegment("System", selected == ThemeMode.SYSTEM, Modifier.weight(1f)) {
                onSelect(ThemeMode.SYSTEM)
            }
            ThemeSegment("Light", selected == ThemeMode.LIGHT, Modifier.weight(1f)) {
                onSelect(ThemeMode.LIGHT)
            }
            ThemeSegment("Dark", selected == ThemeMode.DARK, Modifier.weight(1f)) {
                onSelect(ThemeMode.DARK)
            }
        }
    }
}

/**
 * One segment of the theme switch. [plexFocusable] carries the focus ring and the springy
 * squash-on-press; the selected segment fills amber.
 */
@Composable
private fun ThemeSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    Box(
        modifier = modifier
            .plexFocusable(Radius.pill, onClick = onClick, scaleOnFocus = false)
            .clip(Radius.pill)
            .background(if (selected) colours.accent else Color.Transparent, Radius.pill)
            .padding(vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = when {
                selected && colours.isDark -> colours.background
                selected -> colours.surface
                else -> colours.textSecondary
            },
            maxLines = 1,
        )
    }
}

/**
 * A toggle switch: an amber track when on, quiet when off, with a white knob that slides across.
 * The whole row is already clickable; the switch is the visible affordance.
 */
@Composable
private fun PlexSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colours = PlexTheme.colours
    val trackWidth = 46.dp
    val trackHeight = 28.dp
    val knob = 22.dp
    val travel = trackWidth - knob - Spacing.xxs * 2

    val track by animateColorAsState(
        targetValue = if (checked) colours.accent else colours.border,
        label = "switch-track",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        label = "switch-knob",
    )

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .plexFocusable(Radius.pill, onClick = { onChange(!checked) }, scaleOnFocus = false)
            .clip(Radius.pill)
            .background(track, Radius.pill)
            .padding(Spacing.xxs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(knob)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/* ----------------------------------------------------------------------------------------------
 * Glyphs
 *
 * A handful of line glyphs drawn on a Canvas, so the amber row squares get a fitting icon without
 * pulling in an icon pack. Same stroke language as the app's other hand-drawn icons.
 * ------------------------------------------------------------------------------------------- */

private enum class SettingGlyph {
    APPEARANCE, PLAYBACK, AUTOPLAY, DOWNLOADS, DELETE, LANGUAGE, ACCOUNT, SHIELD, SERVER,
    QUALITY, SUBTITLE
}

@Composable
private fun GlyphIcon(glyph: SettingGlyph, size: Dp, tint: Color) {
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            SettingGlyph.APPEARANCE -> drawAppearance(tint, stroke)
            SettingGlyph.PLAYBACK -> drawPlayback(tint, stroke)
            SettingGlyph.AUTOPLAY -> drawAutoPlay(tint, stroke)
            SettingGlyph.DOWNLOADS -> drawDownload(tint, stroke)
            SettingGlyph.DELETE -> drawDelete(tint, stroke)
            SettingGlyph.LANGUAGE -> drawLanguage(tint, stroke)
            SettingGlyph.ACCOUNT -> drawAccount(tint, stroke)
            SettingGlyph.SHIELD -> drawShield(tint, stroke)
            SettingGlyph.SERVER -> drawServer(tint, stroke)
            SettingGlyph.QUALITY -> drawQuality(tint, stroke)
            SettingGlyph.SUBTITLE -> drawSubtitle(tint, stroke)
        }
    }
}

private fun DrawScope.drawQuality(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A signal meter: three bars of rising height on a shared baseline.
    drawLine(tint, Offset(w * 0.30f, h * 0.72f), Offset(w * 0.30f, h * 0.56f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.50f, h * 0.42f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.70f, h * 0.72f), Offset(w * 0.70f, h * 0.28f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.22f, h * 0.72f), Offset(w * 0.78f, h * 0.72f), stroke.width, stroke.cap)
}

private fun DrawScope.drawSubtitle(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A caption frame with two short lines of text near its base.
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.16f, h * 0.26f),
        size = Size(w * 0.68f, h * 0.48f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.26f, h * 0.56f), Offset(w * 0.50f, h * 0.56f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.56f, h * 0.56f), Offset(w * 0.74f, h * 0.56f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.26f, h * 0.65f), Offset(w * 0.44f, h * 0.65f), stroke.width, stroke.cap)
}

private fun DrawScope.drawAppearance(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val topLeft = Offset(w * 0.18f, h * 0.18f)
    val boxSize = Size(w * 0.64f, h * 0.64f)
    // A contrast disc: the right half filled, the whole outlined.
    drawArc(tint, startAngle = -90f, sweepAngle = 180f, useCenter = true, topLeft = topLeft, size = boxSize)
    drawArc(tint, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = boxSize, style = stroke)
}

private fun DrawScope.drawPlayback(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A display panel with a small stand.
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.18f, h * 0.22f),
        size = Size(w * 0.64f, h * 0.40f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.50f, h * 0.62f), Offset(w * 0.50f, h * 0.74f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.36f, h * 0.78f), Offset(w * 0.64f, h * 0.78f), stroke.width, stroke.cap)
}

private fun DrawScope.drawDownload(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.58f), stroke.width, stroke.cap)
    val head = Path().apply {
        moveTo(w * 0.34f, h * 0.44f)
        lineTo(w * 0.50f, h * 0.60f)
        lineTo(w * 0.66f, h * 0.44f)
    }
    drawPath(head, tint, style = stroke)
    drawLine(tint, Offset(w * 0.28f, h * 0.78f), Offset(w * 0.72f, h * 0.78f), stroke.width, stroke.cap)
}

private fun DrawScope.drawLanguage(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A globe: an outer circle, a vertical meridian ellipse, and an equator.
    drawArc(tint, 0f, 360f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f), style = stroke)
    drawArc(tint, 0f, 360f, false, Offset(w * 0.40f, h * 0.18f), Size(w * 0.20f, h * 0.64f), style = stroke)
    drawLine(tint, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), stroke.width, stroke.cap)
}

private fun DrawScope.drawAccount(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // Head and shoulders.
    drawArc(tint, 0f, 360f, false, Offset(w * 0.34f, h * 0.20f), Size(w * 0.32f, h * 0.32f), style = stroke)
    drawArc(tint, 180f, 180f, false, Offset(w * 0.24f, h * 0.56f), Size(w * 0.52f, h * 0.44f), style = stroke)
}

private fun DrawScope.drawServer(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
    // Two rack units, each with a status dot.
    drawRoundRect(tint, Offset(w * 0.20f, h * 0.26f), Size(w * 0.60f, h * 0.20f), corner, style = stroke)
    drawRoundRect(tint, Offset(w * 0.20f, h * 0.54f), Size(w * 0.60f, h * 0.20f), corner, style = stroke)
    drawCircle(tint, w * 0.035f, Offset(w * 0.30f, h * 0.36f))
    drawCircle(tint, w * 0.035f, Offset(w * 0.30f, h * 0.64f))
}

private fun DrawScope.drawAutoPlay(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A skip-to-next: a filled play triangle nudged up against an end bar.
    val tri = Path().apply {
        moveTo(w * 0.24f, h * 0.28f)
        lineTo(w * 0.58f, h * 0.50f)
        lineTo(w * 0.24f, h * 0.72f)
        close()
    }
    drawPath(tri, tint)
    drawLine(tint, Offset(w * 0.68f, h * 0.28f), Offset(w * 0.68f, h * 0.72f), stroke.width, stroke.cap)
}

private fun DrawScope.drawDelete(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A trash can: lid, body, and two ribs.
    drawLine(tint, Offset(w * 0.24f, h * 0.30f), Offset(w * 0.76f, h * 0.30f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.42f, h * 0.30f), Offset(w * 0.42f, h * 0.22f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.58f, h * 0.30f), Offset(w * 0.58f, h * 0.22f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.58f, h * 0.22f), stroke.width, stroke.cap)
    val body = Path().apply {
        moveTo(w * 0.30f, h * 0.30f)
        lineTo(w * 0.34f, h * 0.78f)
        lineTo(w * 0.66f, h * 0.78f)
        lineTo(w * 0.70f, h * 0.30f)
    }
    drawPath(body, tint, style = stroke)
    drawLine(tint, Offset(w * 0.44f, h * 0.40f), Offset(w * 0.45f, h * 0.68f), stroke.width, stroke.cap)
    drawLine(tint, Offset(w * 0.56f, h * 0.40f), Offset(w * 0.55f, h * 0.68f), stroke.width, stroke.cap)
}

private fun DrawScope.drawShield(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // A shield outline with a small check inside.
    val shield = Path().apply {
        moveTo(w * 0.50f, h * 0.18f)
        lineTo(w * 0.76f, h * 0.30f)
        lineTo(w * 0.76f, h * 0.54f)
        // Taper to a point at the base.
        cubicTo(w * 0.76f, h * 0.72f, w * 0.62f, h * 0.80f, w * 0.50f, h * 0.84f)
        cubicTo(w * 0.38f, h * 0.80f, w * 0.24f, h * 0.72f, w * 0.24f, h * 0.54f)
        lineTo(w * 0.24f, h * 0.30f)
        close()
    }
    drawPath(shield, tint, style = stroke)
    val check = Path().apply {
        moveTo(w * 0.40f, h * 0.50f)
        lineTo(w * 0.47f, h * 0.58f)
        lineTo(w * 0.61f, h * 0.42f)
    }
    drawPath(check, tint, style = stroke)
}

data class SettingsScreenState(
    val matchDisplayRate: Boolean = false,
    val unmeteredOnly: Boolean = true,
    val audioLanguage: String = "eng",
    val subtitleLanguage: String = "eng",
    val subtitlesOn: Boolean = false,
    // Default remote streaming quality. null = Original (no client cap); otherwise a max bitrate
    // in kbps. Mirrors SettingsStore.streamingMaxBitrateKbps.
    val streamingMaxBitrateKbps: Int? = null,
    // Subtitle appearance, mirroring SettingsStore. These map onto core/playback SubtitleStyle.
    val subtitleScalePercent: Int = 100,
    val subtitleForegroundArgb: Long = 0xFFFFFFFF,
    val subtitleBackgroundOpacityPercent: Int = 0,
    val servers: List<PlexServer> = emptyList(),
    val activeServerId: String? = null,
    val signedInAs: String? = null,
    val signedInEmail: String? = null,
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

/* ----------------------------------------------------------------------------------------------
 * Streaming quality and subtitle appearance option tables
 *
 * The pill/value rows work in String codes, so each option below pairs a stable token with its
 * label. Streaming quality tokens are the kbps value as text, with "original" standing for the
 * null (uncapped) choice. Subtitle tokens are the numeric setting value as text. See CLAUDE.md
 * sections 10 (transcode/quality) and 14 (settings).
 * ------------------------------------------------------------------------------------------- */

private const val ORIGINAL_QUALITY_TOKEN = "original"

/** Streaming quality choices, mapping to SettingsStore.streamingMaxBitrateKbps (kbps; null = Original). */
private val BITRATE_OPTIONS: List<Pair<String, String>> = listOf(
    ORIGINAL_QUALITY_TOKEN to "Original",
    "20000" to "20 Mbps",
    "8000" to "8 Mbps",
    "4000" to "4 Mbps",
    "2000" to "2 Mbps",
    "720" to "720 Kbps",
)

/** The pill token for a stored bitrate: the kbps as text, or the Original token when null. */
private fun bitrateToken(kbps: Int?): String = kbps?.toString() ?: ORIGINAL_QUALITY_TOKEN

/** The stored bitrate for a pill token: null for Original, otherwise the parsed kbps. */
private fun tokenToBitrate(token: String): Int? =
    if (token == ORIGINAL_QUALITY_TOKEN) null else token.toIntOrNull()

/** The human label for a stored bitrate, falling back to Original when unrecognised. */
private fun bitrateLabel(kbps: Int?): String =
    BITRATE_OPTIONS.firstOrNull { it.first == bitrateToken(kbps) }?.second ?: "Original"

/** The next bitrate in the fixed list, wrapping around — a stand-in for a full picker. */
private fun nextBitrate(kbps: Int?): Int? {
    val index = BITRATE_OPTIONS.indexOfFirst { it.first == bitrateToken(kbps) }
    val next = if (index < 0) 0 else (index + 1) % BITRATE_OPTIONS.size
    return tokenToBitrate(BITRATE_OPTIONS[next].first)
}

/** Subtitle text size choices, mapping to SettingsStore.subtitleScalePercent. */
private val SUBTITLE_SIZE_OPTIONS: List<Pair<String, String>> = listOf(
    "75" to "Small",
    "100" to "Normal",
    "150" to "Large",
)

private fun subtitleSizeLabel(percent: Int): String =
    SUBTITLE_SIZE_OPTIONS.firstOrNull { it.first == percent.toString() }?.second ?: "Normal"

private fun nextSubtitleSize(percent: Int): Int {
    val index = SUBTITLE_SIZE_OPTIONS.indexOfFirst { it.first == percent.toString() }
    val next = if (index < 0) 1 else (index + 1) % SUBTITLE_SIZE_OPTIONS.size
    return SUBTITLE_SIZE_OPTIONS[next].first.toInt()
}

/** Subtitle text colour choices as packed ARGB, mapping to SettingsStore.subtitleForegroundArgb. */
private val SUBTITLE_COLOR_OPTIONS: List<Pair<String, String>> = listOf(
    0xFFFFFFFF.toString() to "White",
    0xFFF5C518.toString() to "Yellow",
)

private fun subtitleColorLabel(argb: Long): String =
    SUBTITLE_COLOR_OPTIONS.firstOrNull { it.first == argb.toString() }?.second ?: "White"

private fun nextSubtitleColor(argb: Long): Long {
    val index = SUBTITLE_COLOR_OPTIONS.indexOfFirst { it.first == argb.toString() }
    val next = if (index < 0) 0 else (index + 1) % SUBTITLE_COLOR_OPTIONS.size
    return SUBTITLE_COLOR_OPTIONS[next].first.toLong()
}

/** Subtitle background choices as an opacity percent, mapping to subtitleBackgroundOpacityPercent. */
private val SUBTITLE_BACKGROUND_OPTIONS: List<Pair<String, String>> = listOf(
    "0" to "None",
    "60" to "Semi-opaque",
)

private fun subtitleBackgroundLabel(percent: Int): String =
    SUBTITLE_BACKGROUND_OPTIONS.firstOrNull { it.first == percent.toString() }?.second ?: "None"

private fun nextSubtitleBackground(percent: Int): Int {
    val index = SUBTITLE_BACKGROUND_OPTIONS.indexOfFirst { it.first == percent.toString() }
    val next = if (index < 0) 0 else (index + 1) % SUBTITLE_BACKGROUND_OPTIONS.size
    return SUBTITLE_BACKGROUND_OPTIONS[next].first.toInt()
}
