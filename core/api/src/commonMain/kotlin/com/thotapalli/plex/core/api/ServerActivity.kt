package com.thotapalli.plex.core.api

/**
 * A running background job on the server, as reported by `GET /activities`.
 *
 * Used to show live library-scan progress and to notice when a scan finishes, so newly
 * added items can be picked up without the viewer refreshing by hand.
 *
 * @property progress fraction complete, 0f..1f (the wire reports 0..100).
 * @property librarySectionId the key of the library the job touches, when the server names one.
 */
data class ServerActivity(
    val type: String,
    val title: String,
    val subtitle: String?,
    val progress: Float,
    val librarySectionId: String?,
)
