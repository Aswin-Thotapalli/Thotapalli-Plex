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
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.ServerUpdate
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
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
    ContentWidthCap(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().material(GlassRole.GROUND).glassSource(),
            contentPadding = PaddingValues(PlexTheme.sizeClass.screenPadding),
            // The airy gap that separates one section from the next.
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { PageTitle("Settings") }

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

            item {
                SettingsSection("Account") {
                    SettingsCard {
                        AccountRow(signedInAs = state.signedInAs, onManage = onSignOut)
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
 * Structure
 * ------------------------------------------------------------------------------------------- */

/** The page heading: a tall amber rule and the title in the display face. */
@Composable
private fun PageTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .background(PlexTheme.colours.accent, Radius.pill),
        )
        Spacer(Modifier.width(Spacing.sm))
        PlexText(text = title, style = PlexTheme.type.display)
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

/** A row whose control is a wrapping set of choice pills below the title. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PillRow(
    glyph: SettingGlyph,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
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
                options.forEach { (code, label) ->
                    TextChip(label, selected = code == selected, onClick = { onSelect(code) })
                }
            }
        }
    }
}

/** The account row: who is signed in, and the action to manage the account. */
@Composable
private fun AccountRow(signedInAs: String?, onManage: () -> Unit) {
    val colours = PlexTheme.colours
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(SettingGlyph.ACCOUNT)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            PlexText(
                text = signedInAs?.let { "Signed in as $it" } ?: "Signed in",
                style = PlexTheme.type.label,
            )
            PlexText(
                text = "Manage your Plex account",
                style = PlexTheme.type.caption,
                colour = colours.textSecondary,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        // The one account action this client exposes is sign-out; label it plainly.
        TextChip("Sign out", selected = false, onClick = onManage)
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

private enum class SettingGlyph { APPEARANCE, PLAYBACK, DOWNLOADS, LANGUAGE, ACCOUNT, SERVER }

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
            SettingGlyph.DOWNLOADS -> drawDownload(tint, stroke)
            SettingGlyph.LANGUAGE -> drawLanguage(tint, stroke)
            SettingGlyph.ACCOUNT -> drawAccount(tint, stroke)
            SettingGlyph.SERVER -> drawServer(tint, stroke)
        }
    }
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
