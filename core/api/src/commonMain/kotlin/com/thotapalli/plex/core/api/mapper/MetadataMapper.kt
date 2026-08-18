package com.thotapalli.plex.core.api.mapper

import com.thotapalli.plex.core.api.dto.ChapterDto
import com.thotapalli.plex.core.api.dto.DirectoryDto
import com.thotapalli.plex.core.api.dto.MarkerDto
import com.thotapalli.plex.core.api.dto.MetadataDto
import com.thotapalli.plex.core.api.dto.PartDto
import com.thotapalli.plex.core.api.dto.StreamDto
import com.thotapalli.plex.core.model.AudioStream
import com.thotapalli.plex.core.model.Chapter
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.Marker
import com.thotapalli.plex.core.model.MarkerType
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.MediaPart
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.SubtitleStream
import com.thotapalli.plex.core.model.VideoStream

/**
 * Maps Plex responses onto the section 6 domain model.
 *
 * This file is the only place that knows a Plex response shape, which is what makes a
 * Plex-side change a one-module change. See CLAUDE.md section 18 point 2.
 */

fun DirectoryDto.toLibrary() = Library(
    key = key,
    title = title,
    kind = when (type.lowercase()) {
        "movie" -> LibraryKind.MOVIE
        "show" -> LibraryKind.SHOW
        // Music and photo libraries are permanently out of scope, so they are marked
        // unsupported here and filtered out before the interface ever sees them.
        else -> LibraryKind.UNSUPPORTED
    },
    uuid = uuid,
)

fun List<DirectoryDto>.toLibraries(): List<Library> =
    map { it.toLibrary() }
        .filter { it.kind.supported }
        .sortedBy { it.title.lowercase() }

/**
 * One metadata entry.
 *
 * Returns null for a type this client does not model, so an unexpected type in a list
 * drops that entry rather than failing the whole response.
 */
fun MetadataDto.toMediaItem(libraryKey: String? = null): MediaItem? = when (type.lowercase()) {
    "movie" -> toMovie(libraryKey)
    "show" -> toShow(libraryKey)
    "season" -> toSeason()
    "episode" -> toEpisode()
    "collection" -> toCollection(libraryKey)
    else -> null
}

fun List<MetadataDto>.toMediaItems(libraryKey: String? = null): List<MediaItem> =
    mapNotNull { it.toMediaItem(libraryKey) }

private fun MetadataDto.resolvedLibraryKey(fallback: String?): String =
    librarySectionKey?.substringAfterLast('/')
        ?: librarySectionID
        ?: fallback
        ?: ""

/** Plex omits titleSort when it equals the title. */
private val MetadataDto.sortTitle: String get() = titleSort ?: title

fun MetadataDto.toMovie(libraryKey: String? = null) = Movie(
    ratingKey = ratingKey,
    title = title,
    year = year,
    summary = summary,
    thumbPath = thumb,
    artPath = art,
    durationMs = duration,
    viewOffsetMs = viewOffset,
    viewCount = viewCount,
    titleSort = sortTitle,
    libraryKey = resolvedLibraryKey(libraryKey),
)

fun MetadataDto.toShow(libraryKey: String? = null) = Show(
    ratingKey = ratingKey,
    title = title,
    year = year,
    summary = summary,
    thumbPath = thumb,
    artPath = art,
    durationMs = duration,
    viewOffsetMs = viewOffset,
    viewCount = viewCount,
    titleSort = sortTitle,
    libraryKey = resolvedLibraryKey(libraryKey),
    childCount = childCount,
    leafCount = leafCount,
    viewedLeafCount = viewedLeafCount,
)

fun MetadataDto.toSeason() = Season(
    ratingKey = ratingKey,
    title = title,
    year = year,
    summary = summary,
    // A season without its own art falls back to the show's, so a season grid is never
    // half empty.
    thumbPath = thumb ?: parentThumb,
    artPath = art ?: grandparentArt,
    durationMs = duration,
    viewOffsetMs = viewOffset,
    viewCount = viewCount,
    showRatingKey = parentRatingKey.orEmpty(),
    showTitle = parentTitle.orEmpty(),
    index = index ?: 0,
    leafCount = leafCount,
    viewedLeafCount = viewedLeafCount,
)

fun MetadataDto.toEpisode() = Episode(
    ratingKey = ratingKey,
    title = title,
    year = year,
    summary = summary,
    thumbPath = thumb,
    artPath = art ?: grandparentArt,
    durationMs = duration,
    viewOffsetMs = viewOffset,
    viewCount = viewCount,
    showRatingKey = grandparentRatingKey.orEmpty(),
    seasonRatingKey = parentRatingKey.orEmpty(),
    showTitle = grandparentTitle.orEmpty(),
    seasonIndex = parentIndex ?: 0,
    episodeIndex = index ?: 0,
)

fun MetadataDto.toCollection(libraryKey: String? = null) = MediaCollection(
    ratingKey = ratingKey,
    title = title,
    year = year,
    summary = summary,
    thumbPath = thumb,
    artPath = art,
    durationMs = duration,
    viewOffsetMs = viewOffset,
    viewCount = viewCount,
    titleSort = sortTitle,
    libraryKey = resolvedLibraryKey(libraryKey),
    childCount = childCount,
)

/** The full playback picture: the item, its parts, its markers and its chapters. */
fun MetadataDto.toMediaDetail(libraryKey: String? = null): MediaDetail? {
    val item = toMediaItem(libraryKey) ?: return null
    return MediaDetail(
        item = item,
        parts = media.flatMap { m -> m.part.map { it.toMediaPart(fallbackContainer = m.container) } },
        markers = marker.mapNotNull { it.toMarker() },
        chapters = chapter.map { it.toChapter() },
    )
}

fun PartDto.toMediaPart(fallbackContainer: String? = null) = MediaPart(
    partId = id,
    fileKey = key,
    container = (container ?: fallbackContainer).orEmpty(),
    sizeBytes = size,
    durationMs = duration,
    videoStreams = stream.filter { it.streamType == STREAM_VIDEO }.map { it.toVideoStream() },
    audioStreams = stream.filter { it.streamType == STREAM_AUDIO }.map { it.toAudioStream() },
    subtitleStreams = stream.filter { it.streamType == STREAM_SUBTITLE }.map { it.toSubtitleStream() },
)

fun StreamDto.toVideoStream() = VideoStream(
    id = id,
    codec = codec,
    width = width ?: 0,
    height = height ?: 0,
    frameRate = frameRate,
    bitDepth = bitDepth,
    // Plex reports the transfer characteristic rather than a flag. smpte2084 is PQ and
    // arib-std-b67 is HLG; anything else is standard range.
    hdr = colorTrc?.lowercase() in setOf("smpte2084", "arib-std-b67"),
    title = displayTitle ?: title,
)

fun StreamDto.toAudioStream() = AudioStream(
    id = id,
    codec = codec,
    channels = channels ?: 0,
    language = language,
    languageCode = languageCode ?: languageTag,
    title = displayTitle ?: title,
    default = default || selected,
)

fun StreamDto.toSubtitleStream() = SubtitleStream(
    id = id,
    codec = codec,
    language = language,
    languageCode = languageCode ?: languageTag,
    title = displayTitle ?: title,
    forced = forced,
    default = default || selected,
    // An embedded track has no key of its own; only a sidecar file does.
    external = !key.isNullOrBlank(),
    key = key,
)

fun MarkerDto.toMarker(): Marker? {
    val markerType = when (type.lowercase()) {
        "intro" -> MarkerType.INTRO
        "credits" -> MarkerType.CREDITS
        // Plex has added marker types over time. An unknown one is ignored rather than
        // guessed at, because guessing would skip the wrong part of the episode.
        else -> return null
    }
    return Marker(type = markerType, startMs = startTimeOffset, endMs = endTimeOffset)
}

fun ChapterDto.toChapter() = Chapter(
    index = index,
    title = tag,
    startMs = startTimeOffset,
    endMs = endTimeOffset,
)

private const val STREAM_VIDEO = 1
private const val STREAM_AUDIO = 2
private const val STREAM_SUBTITLE = 3
