package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Season

/**
 * What a server can be asked for.
 *
 * [PlexServerApi] is the one implementation that speaks HTTP. The interface exists so the
 * layers above depend on the questions rather than on Ktor, which is what lets a
 * repository test assert that a cached read made no network call at all.
 */
interface PlexServerSource {
    suspend fun identity(scope: ServerScope): String

    suspend fun libraries(scope: ServerScope): List<Library>

    suspend fun libraryContents(
        scope: ServerScope,
        libraryKey: String,
        kind: LibraryContentType,
        unwatchedOnly: Boolean = false,
    ): List<MediaItem>

    suspend fun collections(scope: ServerScope, libraryKey: String): List<MediaCollection>

    suspend fun metadata(scope: ServerScope, ratingKey: String): MediaDetail?

    suspend fun children(scope: ServerScope, ratingKey: String): List<MediaItem>

    suspend fun seasons(scope: ServerScope, showRatingKey: String): List<Season>

    suspend fun allEpisodes(scope: ServerScope, showRatingKey: String): List<Episode>

    suspend fun continueWatching(scope: ServerScope): List<MediaItem>

    suspend fun search(scope: ServerScope, query: String, limit: Int = 50): SearchResults

    suspend fun timeline(
        scope: ServerScope,
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
        sessionIdentifier: String,
    )

    suspend fun scrobble(scope: ServerScope, ratingKey: String)

    suspend fun unscrobble(scope: ServerScope, ratingKey: String)
}
