package com.thotapalli.plex.core.data

import com.thotapalli.plex.core.data.db.Continue_watching_view
import com.thotapalli.plex.core.data.db.Media_item_view
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.data.db.Library as LibraryRow

/** The kind column. Stored as text so the table stays readable. */
enum class ItemKind {
    MOVIE, SHOW, SEASON, EPISODE, COLLECTION;

    companion object {
        fun of(item: MediaItem): ItemKind = when (item) {
            is Movie -> MOVIE
            is Show -> SHOW
            is Season -> SEASON
            is Episode -> EPISODE
            is MediaCollection -> COLLECTION
        }
    }
}

fun LibraryRow.toLibrary() = Library(
    key = key,
    title = title,
    kind = runCatching { LibraryKind.valueOf(kind) }.getOrDefault(LibraryKind.UNSUPPORTED),
    uuid = uuid,
)

/**
 * One row's worth of cached item, independent of which table it came from.
 *
 * Both catalogue reads (media_item_view) and the continue-watching snapshot
 * (continue_watching_view) land here, so the mapping to a domain item is written once and the two
 * paths cannot drift. It is also the shape writes are built in, so a round trip is symmetrical.
 */
data class ItemRow(
    val ratingKey: String,
    val libraryKey: String,
    val parentKey: String?,
    val kind: String,
    val title: String,
    val titleSort: String,
    val year: Long?,
    val summary: String,
    val thumbPath: String?,
    val artPath: String?,
    val durationMs: Long,
    val viewOffsetMs: Long,
    val viewCount: Long,
    val seasonIndex: Long?,
    val episodeIndex: Long?,
    val showRatingKey: String?,
    val showTitle: String?,
    val childCount: Long,
    val leafCount: Long,
    val viewedLeafCount: Long,
    val refreshedAt: Long,
)

fun Media_item_view.toItemRow() = ItemRow(
    ratingKey = rating_key,
    libraryKey = library_key,
    parentKey = parent_key,
    kind = kind,
    title = title,
    titleSort = title_sort,
    year = year,
    summary = summary,
    thumbPath = thumb_path,
    artPath = art_path,
    durationMs = duration_ms,
    viewOffsetMs = view_offset_ms,
    viewCount = view_count,
    seasonIndex = season_index,
    episodeIndex = episode_index,
    showRatingKey = show_rating_key,
    showTitle = show_title,
    childCount = child_count,
    leafCount = leaf_count,
    viewedLeafCount = viewed_leaf_count,
    refreshedAt = refreshed_at,
)

fun Continue_watching_view.toItemRow() = ItemRow(
    ratingKey = rating_key,
    libraryKey = library_key,
    parentKey = parent_key,
    kind = kind,
    title = title,
    titleSort = title_sort,
    year = year,
    summary = summary,
    thumbPath = thumb_path,
    artPath = art_path,
    durationMs = duration_ms,
    viewOffsetMs = view_offset_ms,
    viewCount = view_count,
    seasonIndex = season_index,
    episodeIndex = episode_index,
    showRatingKey = show_rating_key,
    showTitle = show_title,
    childCount = child_count,
    leafCount = leaf_count,
    viewedLeafCount = viewed_leaf_count,
    refreshedAt = refreshed_at,
)

/**
 * A cached row back into a domain item.
 *
 * Returns null for a kind this build does not know, which happens only when a database
 * written by a newer build is read by an older one. Dropping the row is correct: the cache
 * is never the source of truth, so the next refresh restores it.
 */
fun ItemRow.toMediaItem(): MediaItem? = when (runCatching { ItemKind.valueOf(kind) }.getOrNull()) {
    ItemKind.MOVIE -> Movie(
        ratingKey = ratingKey,
        title = title,
        year = year?.toInt(),
        summary = summary,
        thumbPath = thumbPath,
        artPath = artPath,
        logoPath = null,
        durationMs = durationMs,
        viewOffsetMs = viewOffsetMs,
        viewCount = viewCount.toInt(),
        titleSort = titleSort,
        libraryKey = libraryKey,
    )

    ItemKind.SHOW -> Show(
        ratingKey = ratingKey,
        title = title,
        year = year?.toInt(),
        summary = summary,
        thumbPath = thumbPath,
        artPath = artPath,
        logoPath = null,
        durationMs = durationMs,
        viewOffsetMs = viewOffsetMs,
        viewCount = viewCount.toInt(),
        titleSort = titleSort,
        libraryKey = libraryKey,
        childCount = childCount.toInt(),
        leafCount = leafCount.toInt(),
        viewedLeafCount = viewedLeafCount.toInt(),
    )

    ItemKind.SEASON -> Season(
        ratingKey = ratingKey,
        title = title,
        year = year?.toInt(),
        summary = summary,
        thumbPath = thumbPath,
        artPath = artPath,
        logoPath = null,
        durationMs = durationMs,
        viewOffsetMs = viewOffsetMs,
        viewCount = viewCount.toInt(),
        showRatingKey = showRatingKey.orEmpty(),
        showTitle = showTitle.orEmpty(),
        index = seasonIndex?.toInt() ?: 0,
        leafCount = leafCount.toInt(),
        viewedLeafCount = viewedLeafCount.toInt(),
    )

    ItemKind.EPISODE -> Episode(
        ratingKey = ratingKey,
        title = title,
        year = year?.toInt(),
        summary = summary,
        thumbPath = thumbPath,
        artPath = artPath,
        logoPath = null,
        durationMs = durationMs,
        viewOffsetMs = viewOffsetMs,
        viewCount = viewCount.toInt(),
        showRatingKey = showRatingKey.orEmpty(),
        seasonRatingKey = parentKey.orEmpty(),
        showTitle = showTitle.orEmpty(),
        seasonIndex = seasonIndex?.toInt() ?: 0,
        episodeIndex = episodeIndex?.toInt() ?: 0,
    )

    ItemKind.COLLECTION -> MediaCollection(
        ratingKey = ratingKey,
        title = title,
        year = year?.toInt(),
        summary = summary,
        thumbPath = thumbPath,
        artPath = artPath,
        logoPath = null,
        durationMs = durationMs,
        viewOffsetMs = viewOffsetMs,
        viewCount = viewCount.toInt(),
        titleSort = titleSort,
        libraryKey = libraryKey,
        childCount = childCount.toInt(),
    )

    null -> null
}

fun List<Media_item_view>.toMediaItems(): List<MediaItem> = mapNotNull { it.toItemRow().toMediaItem() }

fun List<Continue_watching_view>.toSnapshotItems(): List<MediaItem> =
    mapNotNull { it.toItemRow().toMediaItem() }

fun MediaItem.toRow(libraryKey: String, refreshedAtMs: Long): ItemRow {
    val sort = when (this) {
        is Movie -> titleSort
        is Show -> titleSort
        is MediaCollection -> titleSort
        else -> title
    }
    return ItemRow(
        ratingKey = ratingKey,
        // A season or episode carries no library of its own, so it inherits the caller's.
        libraryKey = when (this) {
            is Movie -> this.libraryKey.ifBlank { libraryKey }
            is Show -> this.libraryKey.ifBlank { libraryKey }
            is MediaCollection -> this.libraryKey.ifBlank { libraryKey }
            else -> libraryKey
        },
        parentKey = when (this) {
            is Season -> showRatingKey
            is Episode -> seasonRatingKey
            else -> null
        },
        kind = ItemKind.of(this).name,
        title = title,
        titleSort = sort,
        year = year?.toLong(),
        summary = summary,
        thumbPath = thumbPath,
        artPath = artPath,
        durationMs = durationMs,
        viewOffsetMs = viewOffsetMs,
        viewCount = viewCount.toLong(),
        seasonIndex = when (this) {
            is Season -> index.toLong()
            is Episode -> seasonIndex.toLong()
            else -> null
        },
        episodeIndex = (this as? Episode)?.episodeIndex?.toLong(),
        showRatingKey = when (this) {
            is Season -> showRatingKey
            is Episode -> showRatingKey
            else -> null
        },
        showTitle = when (this) {
            is Season -> showTitle
            is Episode -> showTitle
            else -> null
        },
        childCount = when (this) {
            is Show -> childCount.toLong()
            is MediaCollection -> childCount.toLong()
            else -> 0L
        },
        leafCount = when (this) {
            is Show -> leafCount.toLong()
            is Season -> leafCount.toLong()
            else -> 0L
        },
        viewedLeafCount = when (this) {
            is Show -> viewedLeafCount.toLong()
            is Season -> viewedLeafCount.toLong()
            else -> 0L
        },
        refreshedAt = refreshedAtMs,
    )
}
