package com.thotapalli.plex.core.model

/**
 * An item together with everything playback needs: its parts, its markers and its chapters.
 * Returned by the metadata endpoint with includeMarkers and includeChapters set.
 */
data class MediaDetail(
    val item: MediaItem,
    val parts: List<MediaPart>,
    val markers: List<Marker>,
    val chapters: List<Chapter>,
) {
    val primaryPart: MediaPart? get() = parts.firstOrNull()
    val intro: Marker? get() = markers.firstOrNull { it.type == MarkerType.INTRO }
    val credits: Marker? get() = markers.firstOrNull { it.type == MarkerType.CREDITS }
}
