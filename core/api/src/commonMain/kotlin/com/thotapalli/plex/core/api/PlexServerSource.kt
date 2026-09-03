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

    /**
     * A library's contents with an explicit sort and filter set.
     *
     * The original [libraryContents] above is the title-ascending, filter-optional call the
     * home and library screens have always used and still works unchanged. This overload is
     * the generalised form the library screen's sort and filter controls drive
     * (CLAUDE.md section 16). It takes a required [sort] so a three-argument call never
     * becomes ambiguous with the older method, and an optional [filter].
     *
     * A default implementation delegates to the simpler call, carrying only the unwatched
     * flag, so a fake or cache implementation need not know about sort or filters. The real
     * HTTP implementation overrides it to apply every parameter.
     */
    suspend fun libraryContents(
        scope: ServerScope,
        libraryKey: String,
        kind: LibraryContentType,
        sort: LibrarySort,
        filter: LibraryFilter = LibraryFilter(),
    ): List<MediaItem> = libraryContents(scope, libraryKey, kind, filter.unwatchedOnly)

    /**
     * Whether the server says this part can be direct-played, from the decision endpoint
     * (CLAUDE.md section 10). Returns true on any error or ambiguous answer, so the caller
     * keeps its own direct→transcode fallback path rather than trusting a guess.
     *
     * A default implementation returns true, so a fake or cache implementation opts into the
     * caller's existing fallback without knowing the decision endpoint exists.
     */
    suspend fun canDirectPlay(scope: ServerScope, ratingKey: String, partId: String): Boolean = true

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

    /**
     * The server's `lastViewedAt` for an item, in seconds, or null if unknown/unreachable. Used to
     * resolve offline-vs-server watch-state conflicts by recency on reconnection (§11 point 3): an
     * offline position older than the server's must not overwrite it. Never throws.
     */
    suspend fun lastViewedAtSeconds(scope: ServerScope, ratingKey: String): Long? = null
}
