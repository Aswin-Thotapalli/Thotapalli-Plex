package com.thotapalli.plex.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shapes for the server itself. Every server endpoint wraps its payload in a
 * MediaContainer, so every response type here is [MediaContainerResponse] of something.
 */

@Serializable
data class MediaContainerResponse<T>(
    @SerialName("MediaContainer") val mediaContainer: T,
)

@Serializable
data class IdentityContainer(
    val machineIdentifier: String = "",
    val version: String? = null,
    val size: Int = 0,
)

@Serializable
data class DirectoryContainer(
    val size: Int = 0,
    @SerialName("Directory") val directory: List<DirectoryDto> = emptyList(),
)

@Serializable
data class DirectoryDto(
    val key: String = "",
    val title: String = "",
    val type: String = "",
    val uuid: String = "",
    val agent: String? = null,
    val scanner: String? = null,
    val language: String? = null,
    val refreshing: Boolean = false,
    val updatedAt: Long? = null,
)

@Serializable
data class MetadataContainer(
    val size: Int = 0,
    val totalSize: Int? = null,
    val offset: Int? = null,
    val librarySectionID: String? = null,
    val librarySectionTitle: String? = null,
    val librarySectionUUID: String? = null,
    @SerialName("Metadata") val metadata: List<MetadataDto> = emptyList(),
)

@Serializable
data class HubContainer(
    val size: Int = 0,
    @SerialName("Hub") val hub: List<HubDto> = emptyList(),
    @SerialName("Metadata") val metadata: List<MetadataDto> = emptyList(),
)

@Serializable
data class HubDto(
    val hubIdentifier: String = "",
    val title: String = "",
    val type: String = "",
    val size: Int = 0,
    @SerialName("Metadata") val metadata: List<MetadataDto> = emptyList(),
)

@Serializable
data class MetadataDto(
    val ratingKey: String = "",
    val key: String = "",
    val guid: String? = null,
    val type: String = "",
    val title: String = "",
    val titleSort: String? = null,
    val summary: String = "",
    val year: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val duration: Long = 0,
    val viewOffset: Long = 0,
    val viewCount: Int = 0,
    val lastViewedAt: Long? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val index: Int? = null,
    val parentIndex: Int? = null,
    val childCount: Int = 0,
    val leafCount: Int = 0,
    val viewedLeafCount: Int = 0,
    val librarySectionID: String? = null,
    val librarySectionKey: String? = null,
    val parentRatingKey: String? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val grandparentRatingKey: String? = null,
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    @SerialName("Media") val media: List<MediaDto> = emptyList(),
    @SerialName("Marker") val marker: List<MarkerDto> = emptyList(),
    @SerialName("Chapter") val chapter: List<ChapterDto> = emptyList(),
)

@Serializable
data class MediaDto(
    val id: String = "",
    val duration: Long = 0,
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val videoFrameRate: String? = null,
    @SerialName("Part") val part: List<PartDto> = emptyList(),
)

@Serializable
data class PartDto(
    val id: String = "",
    val key: String = "",
    val file: String? = null,
    val size: Long = 0,
    val duration: Long = 0,
    val container: String? = null,
    val indexes: String? = null,
    @SerialName("Stream") val stream: List<StreamDto> = emptyList(),
) {
    /** Trickplay thumbnails exist only when the server has indexed the part. */
    val hasTrickplay: Boolean get() = indexes?.contains("sd") == true
}

@Serializable
data class StreamDto(
    val id: String = "",
    /** 1 video, 2 audio, 3 subtitle. */
    val streamType: Int = 0,
    val codec: String = "",
    val index: Int? = null,
    val language: String? = null,
    val languageCode: String? = null,
    val languageTag: String? = null,
    val title: String? = null,
    val displayTitle: String? = null,
    val selected: Boolean = false,
    val default: Boolean = false,
    val forced: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitDepth: Int? = null,
    val colorTrc: String? = null,
    val channels: Int? = null,
    val key: String? = null,
)

@Serializable
data class MarkerDto(
    val id: String = "",
    val type: String = "",
    val startTimeOffset: Long = 0,
    val endTimeOffset: Long = 0,
    val final: Boolean = false,
)

@Serializable
data class ChapterDto(
    val id: String = "",
    val index: Int = 0,
    val tag: String? = null,
    val startTimeOffset: Long = 0,
    val endTimeOffset: Long = 0,
)
