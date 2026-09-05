package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.MediaPart
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.VideoStream
import com.thotapalli.plex.core.model.partiallyWatched
import com.thotapalli.plex.core.model.watched
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.design.backgroundBrush
import com.thotapalli.plex.ui.design.glassSource
import com.thotapalli.plex.ui.shared.ActiveServer
import com.thotapalli.plex.ui.shared.ArtworkSize
import com.thotapalli.plex.ui.shared.CinematicBackdrop
import com.thotapalli.plex.ui.shared.ambient.AmbientBackground
import com.thotapalli.plex.ui.shared.DetailState
import com.thotapalli.plex.ui.shared.EpisodeRow
import com.thotapalli.plex.ui.shared.ItemActions
import com.thotapalli.plex.ui.shared.Artwork
import com.thotapalli.plex.ui.shared.ItemOverflowButton
import com.thotapalli.plex.ui.shared.PlexIcon
import com.thotapalli.plex.ui.shared.PlexIconKind
import com.thotapalli.plex.ui.shared.PrimaryButton
import com.thotapalli.plex.ui.shared.SecondaryButton
import com.thotapalli.plex.ui.shared.SectionHeader
import com.thotapalli.plex.ui.shared.formatDuration
import com.thotapalli.plex.ui.shared.material
import com.thotapalli.plex.ui.shared.input.rememberFirstFocus
import com.thotapalli.plex.ui.shared.motion.staggeredEntrance
import com.thotapalli.plex.ui.shared.plexFocusable
import kotlin.math.roundToInt

/**
 * Movie detail and show detail, which share a cinematic header and differ only below it.
 * See CLAUDE.md section 14 items 4 and 5.
 *
 * The screen is built as layers of the redesign: a full-bleed [CinematicBackdrop] bleeds art to
 * the top edge, an [AmbientBackground] carries the artwork's colour down the whole screen and is
 * marked as the glass source, a [GlassRole.GROUND] veil over it is the same base the rest of the
 * app floats on, and every cluster below — the action/metadata cluster, the track reference, each
 * episode row — floats as a [GlassRole.CARD] via `Modifier.material`, with the season pills as
 * [GlassRole.CHIP]. Compact and Medium scroll one column; Expanded and Television split two panes.
 */
@Composable
fun DetailScreen(
    server: ActiveServer,
    state: DetailState,
    onPlay: (MediaItem, resumeFromMs: Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onSetContainerWatched: (String, Boolean) -> Unit = { _, _ -> },
    actions: ItemActions? = null,
    modifier: Modifier = Modifier,
) {
    val sizeClass = PlexTheme.sizeClass
    val item = state.item
    // On a television the primary Play/Resume action takes first focus on entry, so the
    // remote lands on the one action that matters. See CLAUDE.md section 13.
    val firstFocus = rememberFirstFocus(enabled = sizeClass.isTelevision)
    val contentPadding = sizeClass.screenPadding
    val backdropUrl = server.urls.artwork(
        item.artPath ?: item.thumbPath,
        ArtworkSize.BACKDROP_WIDTH,
        ArtworkSize.BACKDROP_HEIGHT,
    )

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // The deep-indigo ground under everything, so the art dissolves onto the page.
            .background(PlexTheme.colours.backgroundBrush()),
    ) {
        // The hero height is relative to the actual viewport, not a fixed dp. The two-pane layout
        // puts the hero above the panes in a column, so a fixed 460dp hero on a short tablet-
        // LANDSCAPE viewport (~540dp) left the Resume action and episode list crammed into the
        // remaining sliver and effectively unscrollable. Capping it at ~44% of the height keeps the
        // panes tall enough to read and scroll. The single-column layout scrolls as one, so its hero
        // can stay a comfortable fixed height. See CLAUDE.md sections 13 and 14.
        // Only television keeps the split two-pane hero. Tablet/desktop now scroll as one Plex-style
        // column (with the sidebar hidden, they get the full width), which reads far better than the
        // cramped two-pane. The single column scrolls, so a comfortable fixed hero is right.
        val heroHeight = if (sizeClass == SizeClass.TELEVISION) {
            // The TV column scrolls as one and the actions sit on the ground below the art, so the
            // hero can be a comfortable, cinematic height rather than a cramped band.
            (maxHeight * 0.52f).coerceIn(320.dp, 540.dp)
        } else {
            (maxHeight * 0.5f).coerceIn(300.dp, 440.dp)
        }
        // The whole screen takes on the colour of the content's own artwork, and that wash is the
        // glass source: every frosted panel below samples and blurs it. Skipped on television — the
        // TV detail is solid-surface and scrolls, so the ambient colour sampling is pure cost there
        // and was a source of scroll stutter; a plain dark ground is used instead.
        if (sizeClass != SizeClass.TELEVISION) {
            AmbientBackground(
                url = backdropUrl,
                modifier = Modifier.fillMaxSize().glassSource(),
            )
            // The GROUND veil: the same translucent base every screen floats on, laid over the ambient
            // art so the whole area below the cinematic backdrop reads as one glass world with the nav.
            // The full-bleed backdrop is drawn above it and covers it at the top.
            Box(Modifier.fillMaxSize().material(GlassRole.GROUND))
        } else {
            Box(Modifier.fillMaxSize().background(PlexTheme.colours.background))
        }

        SingleColumnDetail(
            server = server,
            state = state,
            heroHeight = heroHeight,
            contentPadding = contentPadding,
            backdropUrl = backdropUrl,
            firstFocus = firstFocus,
            onPlay = onPlay,
            onDownload = onDownload,
            onToggleWatched = onToggleWatched,
            onSeasonSelected = onSeasonSelected,
            onSelectEpisode = onSelectEpisode,
            onSetContainerWatched = onSetContainerWatched,
            actions = actions,
        )
    }
}

// --- single column, Compact and Medium ---------------------------------------------------

@Composable
private fun SingleColumnDetail(
    server: ActiveServer,
    state: DetailState,
    heroHeight: Dp,
    contentPadding: Dp,
    backdropUrl: String?,
    firstFocus: FocusRequester?,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onSeasonSelected: (Season) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onSetContainerWatched: (String, Boolean) -> Unit,
    actions: ItemActions?,
) {
    val item = state.item

    LazyColumn(Modifier.fillMaxSize()) {
        // The hero: a full-bleed backdrop that bleeds to the top edge and fades into the page,
        // with the title and the key facts over the bottom of the image.
        item {
            CinematicBackdrop(url = backdropUrl, title = item.title, height = heroHeight) {
                HeroCaption(item, Modifier.align(Alignment.BottomStart).padding(horizontal = contentPadding, vertical = Spacing.lg))
            }
        }

        // The action cluster: a frosted panel, pulled up to read as attached to the hero, that
        // groups the primary and secondary actions and the summary.
        item {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding)
                    .padding(top = Spacing.md)
                    .staggeredEntrance(index = 0),
            ) {
                DetailActions(
                    state = state,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onToggleWatched = onToggleWatched,
                    actions = actions,
                    firstFocus = firstFocus,
                )
                if (item.summary.isNotBlank()) {
                    PlexText(
                        text = item.summary,
                        colour = PlexTheme.colours.textSecondary,
                        maxLines = 6,
                    )
                }
            }
        }

        // Media / version info and the audio and subtitle track reference, on a quiet glass
        // panel below the summary. See CLAUDE.md section 14 items 4 and 5.
        if (state.detail?.primaryPart != null) {
            item {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = Spacing.md)
                        .staggeredEntrance(index = 1),
                ) {
                    MediaInfoSection(state)
                }
            }
        }

        // Episode navigation shows for a show and for an episode-opened detail alike (§14.5); a
        // movie has no episodes and skips it. The state is populated from the parent show in both
        // episodic cases by AppViewModel.openDetail.
        if (item is Show || item is Episode) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = Spacing.md),
                ) {
                    EpisodeNav(server, state, onSeasonSelected, onSetContainerWatched)
                }
            }

            item {
                // Each episode row floats as its own CARD, rather than sharing one panel, so the
                // list reads as a stack of glass tiles in the same material world as the app.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    state.episodesInSelectedSeason.forEachIndexed { index, episode ->
                        EpisodeRow(
                            episode = episode,
                            thumbnailUrl = server.urls.artwork(
                                episode.thumbPath,
                                ArtworkSize.THUMB_WIDTH,
                                ArtworkSize.THUMB_HEIGHT,
                            ),
                            onPlay = { onPlay(episode, episode.viewOffsetMs) },
                            onSelect = { onSelectEpisode(episode) },
                            selected = episode.ratingKey == state.selectedEpisode?.ratingKey,
                            actions = actions,
                            modifier = Modifier
                                .material(GlassRole.CARD, shape = Radius.card)
                                .staggeredEntrance(index = index, key = episode.ratingKey),
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

// --- shared pieces -----------------------------------------------------------------------

/** The title and one line of key facts, over the darkest foot of the hero fade. */
@Composable
private fun HeroCaption(item: MediaItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Light text on the backdrop's dark caption scrim, so it reads over any still in either
        // theme (the hero foot is always dark; the page below returns to the theme ground).
        PlexText(
            item.title,
            style = PlexTheme.type.display,
            colour = Color.White,
            maxLines = 2,
        )
        PlexText(
            text = metadataLine(item),
            style = PlexTheme.type.label,
            colour = Color.White.copy(alpha = 0.78f),
        )
    }
}

/**
 * A [GlassRole.CARD] surface hosting a stack of detail content. Every content cluster on the
 * screen — the action/metadata cluster, the track reference, the movie overview — floats as one of
 * these, so the material engine gives each the same calibrated frost and it reads as part of one
 * glass world with the rest of the app.
 */
@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.glass,
    contentPadding: Dp = Spacing.md,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.sm),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.material(GlassRole.CARD, shape = shape).padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(
    state: DetailState,
    onPlay: (MediaItem, Long) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    actions: ItemActions? = null,
    firstFocus: FocusRequester? = null,
) {
    val item = state.item
    // An episode-opened detail plays the episode the viewer is looking at — the one they opened,
    // or another they have since selected in the list — rather than the show's next unwatched.
    // A show plays its next unwatched episode; if every episode is watched it plays the first of
    // the shown season rather than the show itself, which has no file to play and would otherwise
    // stall at 0:00. A movie has no episodes, so it stays the item. See CLAUDE.md section 14 item 5.
    val playTarget: MediaItem = when {
        item is Episode -> state.selectedEpisode ?: item
        else -> state.nextUnwatched
            ?: state.episodesInSelectedSeason.firstOrNull()
            ?: state.episodes.firstOrNull()
            ?: item
    }
    val resumeFrom = playTarget.viewOffsetMs
    val resumable = playTarget.partiallyWatched

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PrimaryButton(
            label = when {
                item is Show && state.nextUnwatched != null ->
                    "Play S${pad(state.nextUnwatched.seasonIndex)}E${pad(state.nextUnwatched.episodeIndex)}"
                // Resume immediately, no prompt. See CLAUDE.md section 2.
                resumable -> "Resume"
                else -> "Play"
            },
            onClick = { onPlay(playTarget, resumeFrom) },
            leadingIcon = PlexIconKind.PLAY,
            modifier = if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
        )
        // Resume leaves a way back to the beginning, which resume-immediately otherwise hides.
        if (resumable) {
            SecondaryButton(label = "From start", onClick = { onPlay(playTarget, 0L) })
        }
        SecondaryButton(
            label = "Download",
            onClick = { onDownload(item) },
            leadingIcon = PlexIconKind.DOWNLOADS,
        )
        SecondaryButton(
            label = if (item.watched) "Watched" else "Mark watched",
            onClick = { onToggleWatched(item) },
            leadingIcon = if (item.watched) PlexIconKind.CHECK else null,
        )
        // The overflow carries the rest of the server actions (refresh, delete) so the header
        // stays to its primary Play and Download while everything else lives one tap away.
        if (actions != null) {
            ItemOverflowButton(item = item, actions = actions)
        }
    }
}

/**
 * Media / version reference for a title: the video summary and quiet badges, an optional version
 * picker when the title has more than one file, and the audio and subtitle track listing. All of
 * it is understated reference material, not a headline. See CLAUDE.md section 14 items 4 and 5,
 * and the section 6 domain model (MediaPart / VideoStream / AudioStream / SubtitleStream).
 *
 * The detail state exposes the full parts list on `detail.parts`, so the version picker below is a
 * real chip row over every version. It changes which version's info is shown here.
 *
 * TODO multi-version picker: making the Play action use the picked version needs the chosen part
 *  carried through — `onPlay` is `(MediaItem, Long)` today and playback resolves
 *  `detail.primaryPart` downstream, so the selection cannot steer Play from inside this screen
 *  alone. It needs a selected-part on DetailState (or an onPlay overload) to close that loop.
 */
@Composable
private fun MediaInfoSection(state: DetailState, modifier: Modifier = Modifier) {
    val parts = state.detail?.parts.orEmpty()
    if (parts.isEmpty()) return

    // The chosen version, reset when the screen moves to a different item.
    var selected by remember(state.item.ratingKey) { mutableStateOf(0) }
    val index = selected.coerceIn(0, parts.lastIndex)
    val part = parts[index]

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (parts.size > 1) {
            SectionHeader("Versions")
            VersionPicker(parts = parts, selectedIndex = index, onSelect = { selected = it })
        }
        MediaSummary(part)
        AudioSubtitleReference(part)
    }
}

/**
 * The video summary: resolution and HDR as small frosted [GlassRole.CHIP] badges, then codec,
 * bit depth, frame rate, container and file size as one quiet caption line, e.g.
 * "HEVC  •  10-bit  •  23.976 fps  •  MKV  •  24.3 GB".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaSummary(part: MediaPart) {
    val video = part.videoStreams.firstOrNull()
    val badges = buildList {
        video?.let { resolutionLabel(it)?.let(::add) }
        if (video?.hdr == true) add("HDR")
    }
    val line = listOfNotNull(
        video?.codec?.uppercase(),
        video?.bitDepth?.takeIf { it >= 10 }?.let { "$it-bit" },
        frameRateLabel(video?.frameRate),
        part.container.takeIf { it.isNotBlank() }?.uppercase(),
        formatBytes(part.sizeBytes).takeIf { part.sizeBytes > 0 },
    ).joinToString("  •  ")

    if (badges.isEmpty() && line.isBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader("Media")
        if (badges.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                badges.forEach { MediaBadge(it) }
            }
        }
        if (line.isNotBlank()) {
            PlexText(
                text = line,
                style = PlexTheme.type.caption,
                colour = PlexTheme.colours.textSecondary,
            )
        }
    }
}

/** One small frosted badge, in the same CHIP material as the season pills. */
@Composable
private fun MediaBadge(text: String) {
    Box(
        modifier = Modifier
            .material(GlassRole.CHIP, shape = Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        PlexText(
            text = text,
            style = PlexTheme.type.caption,
            colour = PlexTheme.colours.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * The version picker: a scrolling row of the same segmented glass pills the season selector uses,
 * one per [MediaPart]. Selecting a version switches which version's info is shown above.
 */
@Composable
private fun VersionPicker(
    parts: List<MediaPart>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(vertical = Spacing.xxs),
    ) {
        itemsIndexed(parts, key = { i, p -> p.partId.ifBlank { i.toString() } }) { i, part ->
            SeasonPill(
                label = versionLabel(part, i),
                selected = i == selectedIndex,
                onClick = { onSelect(i) },
            )
        }
    }
}

/**
 * The audio and subtitle track listing for one part, shown for reference. See CLAUDE.md section 14.
 * Rendered inside a quiet glass panel by both layouts.
 */
@Composable
private fun AudioSubtitleReference(part: MediaPart, modifier: Modifier = Modifier) {
    if (part.audioStreams.isEmpty() && part.subtitleStreams.isEmpty()) return

    Column(modifier) {
        if (part.audioStreams.isNotEmpty()) {
            SectionHeader("Audio")
            part.audioStreams.forEach { stream ->
                PlexText(
                    text = listOfNotNull(
                        stream.title ?: stream.language,
                        stream.codec.uppercase(),
                        channelLayout(stream.channels),
                    ).joinToString("  "),
                    style = PlexTheme.type.caption,
                    colour = PlexTheme.colours.textSecondary,
                )
            }
        }
        if (part.subtitleStreams.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            SectionHeader("Subtitles")
            part.subtitleStreams.forEach { stream ->
                PlexText(
                    text = listOfNotNull(
                        stream.title ?: stream.language,
                        stream.codec.uppercase(),
                        "forced".takeIf { stream.forced },
                    ).joinToString("  "),
                    style = PlexTheme.type.caption,
                    colour = PlexTheme.colours.textSecondary,
                )
            }
        }
    }
}

/** A version's label: its resolution and size where known, else a plain ordinal. */
private fun versionLabel(part: MediaPart, index: Int): String {
    val resolution = part.videoStreams.firstOrNull()?.let { resolutionLabel(it) }
    val size = formatBytes(part.sizeBytes).takeIf { part.sizeBytes > 0 }
    return listOfNotNull(resolution, size).joinToString("  ").ifBlank { "Version ${index + 1}" }
}

/** A human resolution tier from the stream dimensions, e.g. 4K / 1080p / 720p. */
private fun resolutionLabel(video: VideoStream): String? {
    val w = video.width
    val h = video.height
    if (w <= 0 && h <= 0) return null
    return when {
        h >= 2000 || w >= 3800 -> "4K"
        h >= 1400 || w >= 2500 -> "1440p"
        h >= 1000 || w >= 1900 -> "1080p"
        h >= 700 || w >= 1200 -> "720p"
        h >= 460 || w >= 700 -> "480p"
        else -> "SD"
    }
}

/** A speaker layout from the channel count, e.g. 2 -> "2.0", 6 -> "5.1", 8 -> "7.1". */
private fun channelLayout(channels: Int): String? = when {
    channels <= 0 -> null
    channels == 1 -> "Mono"
    channels == 2 -> "2.0"
    else -> "${channels - 1}.1"
}

/** A trimmed frame-rate label, e.g. 23.976 -> "23.976 fps", 30.0 -> "30 fps". */
private fun frameRateLabel(fps: Float?): String? {
    if (fps == null || fps <= 0f) return null
    val milli = (fps * 1000).roundToInt()
    if (milli % 1000 == 0) return "${milli / 1000} fps"
    val text = "${milli / 1000}." + (milli % 1000).toString().padStart(3, '0')
    return "${text.trimEnd('0').trimEnd('.')} fps"
}

/**
 * The episode-navigation cluster: the "Episodes" header carrying the show's total unwatched count
 * (§13 quality-of-life), the season selector, and the per-season / per-show mark-watched controls
 * (§12 high-value parity). Emitted into the caller's column, so the single-column and two-pane
 * layouts share one implementation.
 */
@Composable
private fun EpisodeNav(
    server: ActiveServer,
    state: DetailState,
    onSeasonSelected: (Season) -> Unit,
    onSetContainerWatched: (String, Boolean) -> Unit,
) {
    // A horizontal rail of season poster cards, Plex-style, when the show has more than one season.
    // Picking a card switches the episode list below; the selected card wears the accent ring.
    if (state.seasons.size > 1) {
        SectionHeader("${state.seasons.size} Seasons")
        SeasonRail(server, state, onSeasonSelected)
    }

    val showUnwatched = showUnwatchedCount(state)
    // The episode header names the selected season (Plex shows "7 Episodes"; we lead with the season
    // so the rail selection reads through), with the show's unwatched count trailing.
    SectionHeader(
        state.selectedSeason?.title ?: "Episodes",
        trailing = if (showUnwatched > 0) {
            { UnwatchedBadge(showUnwatched) }
        } else {
            null
        },
    )
    ContainerWatchedControls(state, onSetContainerWatched)
}

/**
 * The Plex "Seasons" strip: a horizontal rail of season poster cards, the selected one carrying the
 * amber ring, each showing its artwork, a watched tick or unwatched count, its title and episode
 * count. Tapping a card selects that season, updating the episode list beneath. See CLAUDE.md §14.
 */
@Composable
private fun SeasonRail(
    server: ActiveServer,
    state: DetailState,
    onSeasonSelected: (Season) -> Unit,
) {
    val selectedKey = state.selectedSeason?.ratingKey
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        // Horizontal + vertical padding so the first card is not flush against the row's clip edge —
        // otherwise its focus scale is cropped on the left (the "first season cut off" bug).
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
        // A desktop mouse only scrolls vertically, so the vertical wheel is redirected to this
        // horizontal rail whenever the pointer is over it — otherwise the later seasons of a long
        // show are unreachable. The event is consumed only when the rail actually moved, so at either
        // end the wheel falls through to scroll the page. Harmless on touch and D-pad (no scroll).
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f && rowState.dispatchRawDelta(dy * 80f) != 0f) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        },
    ) {
        items(state.seasons, key = { it.ratingKey }) { season ->
            SeasonCard(
                title = season.title,
                episodeCount = season.leafCount,
                artworkUrl = server.urls.artwork(
                    season.thumbPath,
                    ArtworkSize.POSTER_WIDTH,
                    ArtworkSize.POSTER_HEIGHT,
                ),
                watched = seasonFullyWatched(state, season),
                unwatched = seasonUnwatchedCount(state, season),
                selected = season.ratingKey == selectedKey,
                onClick = { onSeasonSelected(season) },
            )
        }
    }
}

/** One season poster card in the [SeasonRail]. */
@Composable
private fun SeasonCard(
    title: String,
    episodeCount: Int,
    artworkUrl: String?,
    watched: Boolean,
    unwatched: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(128.dp)
            .plexFocusable(shape = shape, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .then(
                    if (selected) Modifier.border(2.dp, colours.accent, shape) else Modifier,
                ),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = title,
                fallbackTitle = title,
                modifier = Modifier.fillMaxSize(),
            )
            if (watched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xxs)
                        .size(22.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colours.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    PlexIcon(kind = PlexIconKind.CHECK, tint = Color.Black, size = 14.dp)
                }
            } else if (unwatched > 0) {
                Box(Modifier.align(Alignment.TopEnd).padding(Spacing.xxs)) {
                    SeasonCountBadge(unwatched)
                }
            }
        }
        PlexText(
            text = title,
            style = PlexTheme.type.label,
            colour = if (selected) colours.accent else colours.textPrimary,
            maxLines = 1,
        )
        PlexText(
            text = "$episodeCount episodes",
            style = PlexTheme.type.caption,
            colour = colours.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The per-season and per-show "Mark watched / Mark unwatched" controls (§12 high-value parity).
 * Each targets a container ratingKey — the currently selected season, or the show — that
 * [onSetContainerWatched] scrobbles in one call. The label reads "unwatched" and passes `false`
 * when the container already appears fully watched, and "watched" / `true` otherwise. Both are
 * [SecondaryButton]s, so they are [plexFocusable] and in the television focus order beside the
 * primary detail actions. The season control is skipped when no season is selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContainerWatchedControls(
    state: DetailState,
    onSetContainerWatched: (String, Boolean) -> Unit,
) {
    // For a Show the container is the show itself; for an episode-opened detail it is the parent
    // show carried on the episode. See CLAUDE.md section 6.
    val showKey = when (val item = state.item) {
        is Episode -> item.showRatingKey
        else -> item.ratingKey
    }
    val season = state.selectedSeason

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(vertical = Spacing.xs),
    ) {
        if (season != null) {
            val watched = seasonFullyWatched(state, season)
            SecondaryButton(
                label = if (watched) "Mark season unwatched" else "Mark season watched",
                onClick = { onSetContainerWatched(season.ratingKey, !watched) },
                leadingIcon = if (watched) PlexIconKind.CHECK else null,
            )
        }
        val showWatched = showFullyWatched(state)
        SecondaryButton(
            label = if (showWatched) "Mark show unwatched" else "Mark show watched",
            onClick = { onSetContainerWatched(showKey, !showWatched) },
            leadingIcon = if (showWatched) PlexIconKind.CHECK else null,
        )
    }
}

/** Episodes in the given season the server has never recorded a completed view for. */
private fun seasonUnwatchedCount(state: DetailState, season: Season): Int =
    state.episodes.count { it.seasonRatingKey == season.ratingKey && it.viewCount == 0 }

/** Every episode the show carries that has never been watched. */
private fun showUnwatchedCount(state: DetailState): Int =
    state.episodes.count { it.viewCount == 0 }

/**
 * A season reads as fully watched when the server has marked the container itself viewed, or when
 * every loaded episode for it has a completed view. Falls back to the season's own leaf counts when
 * no episodes are loaded for it.
 */
private fun seasonFullyWatched(state: DetailState, season: Season): Boolean {
    if (season.viewCount > 0) return true
    val eps = state.episodes.filter { it.seasonRatingKey == season.ratingKey }
    if (eps.isNotEmpty()) return eps.all { it.viewCount > 0 }
    return season.leafCount > 0 && season.viewedLeafCount >= season.leafCount
}

/**
 * The show reads as fully watched when its own leaf counts are complete, or when every loaded
 * episode has a completed view. A Show's own `viewCount` stays zero even when finished — Plex
 * tracks viewed leaves — so the episode scan is the reliable signal.
 */
private fun showFullyWatched(state: DetailState): Boolean {
    val show = state.item as? Show
    if (show != null && show.leafCount > 0 && show.viewedLeafCount >= show.leafCount) return true
    return state.episodes.isNotEmpty() && state.episodes.all { it.viewCount > 0 }
}

/** The show-level unwatched count, a frosted CHIP pill in the "Episodes" header. */
@Composable
private fun UnwatchedBadge(count: Int) {
    Box(
        modifier = Modifier
            .material(GlassRole.CHIP, shape = Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        PlexText(
            text = "$count unwatched",
            style = PlexTheme.type.caption,
            colour = PlexTheme.colours.textPrimary,
            maxLines = 1,
        )
    }
}

/** A compact per-season unwatched count, a frosted CHIP pill on the season selector. */
@Composable
private fun SeasonCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .material(GlassRole.CHIP, shape = Radius.pill)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    ) {
        PlexText(
            text = count.toString(),
            style = PlexTheme.type.caption,
            colour = PlexTheme.colours.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * The season selector: a frosted glass anchor pill showing the current season that opens a
 * [DropdownMenu] of every season, like the Plex app. Picking one switches the visible episode
 * list via [onSeasonSelected]. The anchor is [plexFocusable] so a remote lands on it, and the
 * whole control is drawn from the design system (CHIP/SHEET material, PlexText, Radius, Spacing).
 * See CLAUDE.md section 14 item 5.
 */
@Composable
private fun SeasonSelector(state: DetailState, onSeasonSelected: (Season) -> Unit) {
    val seasons = state.seasons
    if (seasons.isEmpty()) return

    val colours = PlexTheme.colours
    val selected = state.selectedSeason ?: seasons.first()
    var expanded by remember(state.item.ratingKey) { mutableStateOf(false) }

    Box(Modifier.padding(vertical = Spacing.xs)) {
        // The anchor: a quiet frosted pill carrying the current season, opening the menu on click.
        Row(
            modifier = Modifier
                .plexFocusable(shape = Radius.pill, onClick = { expanded = true })
                .material(GlassRole.CHIP, shape = Radius.pill)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlexText(
                text = selected.title,
                style = PlexTheme.type.label,
                colour = colours.textPrimary,
                maxLines = 1,
            )
            // The selected season's unwatched count, hidden once the season is fully watched.
            val selectedUnwatched = seasonUnwatchedCount(state, selected)
            if (selectedUnwatched > 0) {
                SeasonCountBadge(selectedUnwatched)
            }
            // A small chevron cue that this opens a menu rather than being a plain pill.
            PlexText(
                text = "▾",
                style = PlexTheme.type.label,
                colour = colours.textSecondary,
                maxLines = 1,
            )
        }

        // Every season, listed. The current one takes the amber accent every selection wears.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.material(GlassRole.SHEET, Radius.glassSmall),
        ) {
            seasons.forEach { season ->
                val isSelected = season.ratingKey == state.selectedSeason?.ratingKey
                val unwatched = seasonUnwatchedCount(state, season)
                DropdownMenuItem(
                    text = {
                        PlexText(
                            text = season.title,
                            style = PlexTheme.type.label,
                            colour = if (isSelected) colours.accent else colours.textPrimary,
                            maxLines = 1,
                        )
                    },
                    trailingIcon = if (unwatched > 0) {
                        { SeasonCountBadge(unwatched) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSeasonSelected(season)
                    },
                )
            }
        }
    }
}

/**
 * One segmented pill. Selected is a lit amber lozenge with the same warm accent every selection
 * in the app carries; unselected is a sheet of frosted glass that frosts the backdrop behind it.
 */
@Composable
private fun SeasonPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = PlexTheme.colours
    val onAccent = if (colours.isDark) colours.background else Color.White
    val base = if (selected) {
        Modifier.background(colours.accent, Radius.pill)
    } else {
        Modifier.material(GlassRole.CHIP, shape = Radius.pill)
    }
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick)
            .then(base)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = if (selected) onAccent else colours.textSecondary,
            maxLines = 1,
        )
    }
}

private fun metadataLine(item: MediaItem): String = when (item) {
    is Movie -> listOfNotNull(item.year?.toString(), formatDuration(item.durationMs))
        .joinToString("  ")
    is Show -> listOfNotNull(
        item.year?.toString(),
        "${item.childCount} seasons".takeIf { item.childCount > 0 },
        "${item.leafCount} episodes".takeIf { item.leafCount > 0 },
    ).joinToString("  ")
    is Episode -> "S${pad(item.seasonIndex)}E${pad(item.episodeIndex)}  ${formatDuration(item.durationMs)}"
    else -> formatDuration(item.durationMs)
}

private fun pad(value: Int) = value.toString().padStart(2, '0')
