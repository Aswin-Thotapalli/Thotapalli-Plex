package com.thotapalli.plex.core.model

/**
 * An intro or credits marker.
 *
 * An intro marker shows a skip button while it is active. A credits marker skips
 * automatically into the next episode. See CLAUDE.md section 2.
 */
data class Marker(
    val type: MarkerType,
    val startMs: Long,
    val endMs: Long,
) {
    fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

enum class MarkerType { INTRO, CREDITS }

/** A chapter, used only to inform seeking. */
data class Chapter(
    val index: Int,
    val title: String?,
    val startMs: Long,
    val endMs: Long,
)
