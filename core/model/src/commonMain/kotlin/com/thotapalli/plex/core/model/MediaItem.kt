package com.thotapalli.plex.core.model

/**
 * Anything the library can show. Deliberately free of Plex response shapes: core/api maps
 * Plex onto these types so a Plex-side change touches one layer. See CLAUDE.md section 6.
 */
sealed interface MediaItem {
    val ratingKey: String
    val title: String
    val year: Int?
    val summary: String
    val thumbPath: String?
    val artPath: String?
    val durationMs: Long
    val viewOffsetMs: Long
    val viewCount: Int
}

/** True once the server has recorded a completed view. */
val MediaItem.watched: Boolean get() = viewCount > 0

/** True while a resume position sits meaningfully inside the item. */
val MediaItem.partiallyWatched: Boolean
    get() = viewOffsetMs > 0L && durationMs > 0L && viewOffsetMs < durationMs

/** Progress through the item, 0f to 1f. Zero when the duration is unknown. */
val MediaItem.progress: Float
    get() = if (durationMs <= 0L) 0f else (viewOffsetMs.toFloat() / durationMs).coerceIn(0f, 1f)

data class Movie(
    override val ratingKey: String,
    override val title: String,
    override val year: Int?,
    override val summary: String,
    override val thumbPath: String?,
    override val artPath: String?,
    override val durationMs: Long,
    override val viewOffsetMs: Long,
    override val viewCount: Int,
    val titleSort: String,
    val libraryKey: String,
) : MediaItem

data class Show(
    override val ratingKey: String,
    override val title: String,
    override val year: Int?,
    override val summary: String,
    override val thumbPath: String?,
    override val artPath: String?,
    override val durationMs: Long,
    override val viewOffsetMs: Long,
    override val viewCount: Int,
    val titleSort: String,
    val libraryKey: String,
    val childCount: Int,
    val leafCount: Int,
    val viewedLeafCount: Int,
) : MediaItem {
    val unwatchedLeafCount: Int get() = (leafCount - viewedLeafCount).coerceAtLeast(0)
}

data class Season(
    override val ratingKey: String,
    override val title: String,
    override val year: Int?,
    override val summary: String,
    override val thumbPath: String?,
    override val artPath: String?,
    override val durationMs: Long,
    override val viewOffsetMs: Long,
    override val viewCount: Int,
    val showRatingKey: String,
    val showTitle: String,
    val index: Int,
    val leafCount: Int,
    val viewedLeafCount: Int,
) : MediaItem

data class Episode(
    override val ratingKey: String,
    override val title: String,
    override val year: Int?,
    override val summary: String,
    override val thumbPath: String?,
    override val artPath: String?,
    override val durationMs: Long,
    override val viewOffsetMs: Long,
    override val viewCount: Int,
    val showRatingKey: String,
    val seasonRatingKey: String,
    val showTitle: String,
    val seasonIndex: Int,
    val episodeIndex: Int,
) : MediaItem

/**
 * A collection, shown inside the library that owns it rather than as a discovery surface.
 * See CLAUDE.md section 2.
 */
data class MediaCollection(
    override val ratingKey: String,
    override val title: String,
    override val year: Int?,
    override val summary: String,
    override val thumbPath: String?,
    override val artPath: String?,
    override val durationMs: Long,
    override val viewOffsetMs: Long,
    override val viewCount: Int,
    val titleSort: String,
    val libraryKey: String,
    val childCount: Int,
) : MediaItem
