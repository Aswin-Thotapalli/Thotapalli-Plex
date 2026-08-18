package com.thotapalli.plex.core.data

import com.thotapalli.plex.core.api.LibraryContentType
import com.thotapalli.plex.core.api.PlexServerSource
import com.thotapalli.plex.core.api.SearchResults
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.api.TimelineState
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show

/**
 * A server that counts what it was asked for.
 *
 * The point of the phase 3 step 3 test is that a second read touches no network at all,
 * which needs a call count rather than a stubbed response.
 */
class CountingServerSource(
    var libraries: List<Library> = emptyList(),
    var movies: List<MediaItem> = emptyList(),
    var collections: List<MediaCollection> = emptyList(),
    var seasons: List<Season> = emptyList(),
    var episodes: List<Episode> = emptyList(),
    var onDeck: List<MediaItem> = emptyList(),
) : PlexServerSource {

    val calls = mutableMapOf<String, Int>()

    /** Total network calls of every kind. */
    val total: Int get() = calls.values.sum()

    fun count(name: String): Int = calls[name] ?: 0

    fun reset() = calls.clear()

    private fun record(name: String) {
        calls[name] = (calls[name] ?: 0) + 1
    }

    override suspend fun identity(scope: ServerScope): String {
        record("identity"); return "machine"
    }

    override suspend fun libraries(scope: ServerScope): List<Library> {
        record("libraries"); return libraries
    }

    override suspend fun libraryContents(
        scope: ServerScope,
        libraryKey: String,
        kind: LibraryContentType,
        unwatchedOnly: Boolean,
    ): List<MediaItem> {
        record("libraryContents"); return movies
    }

    override suspend fun collections(scope: ServerScope, libraryKey: String): List<MediaCollection> {
        record("collections"); return collections
    }

    override suspend fun metadata(scope: ServerScope, ratingKey: String): MediaDetail? {
        record("metadata"); return null
    }

    override suspend fun children(scope: ServerScope, ratingKey: String): List<MediaItem> {
        record("children"); return seasons
    }

    override suspend fun seasons(scope: ServerScope, showRatingKey: String): List<Season> {
        record("seasons"); return seasons
    }

    override suspend fun allEpisodes(scope: ServerScope, showRatingKey: String): List<Episode> {
        record("allEpisodes"); return episodes
    }

    override suspend fun continueWatching(scope: ServerScope): List<MediaItem> {
        record("continueWatching"); return onDeck
    }

    override suspend fun search(scope: ServerScope, query: String, limit: Int): SearchResults {
        record("search")
        val hits = (movies + episodes).filter { it.title.contains(query, ignoreCase = true) }
        return SearchResults(
            movies = hits.filterIsInstance<Movie>(),
            shows = hits.filterIsInstance<Show>(),
            episodes = hits.filterIsInstance<Episode>(),
        )
    }

    override suspend fun timeline(
        scope: ServerScope,
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
        sessionIdentifier: String,
    ) = record("timeline")

    override suspend fun scrobble(scope: ServerScope, ratingKey: String) = record("scrobble")

    override suspend fun unscrobble(scope: ServerScope, ratingKey: String) = record("unscrobble")
}

/** A server that is always unreachable, for the offline paths. */
class OfflineServerSource : PlexServerSource {
    private fun fail(): Nothing = throw java.io.IOException("offline")
    override suspend fun identity(scope: ServerScope) = fail()
    override suspend fun libraries(scope: ServerScope) = fail()
    override suspend fun libraryContents(
        scope: ServerScope,
        libraryKey: String,
        kind: LibraryContentType,
        unwatchedOnly: Boolean,
    ) = fail()
    override suspend fun collections(scope: ServerScope, libraryKey: String) = fail()
    override suspend fun metadata(scope: ServerScope, ratingKey: String) = fail()
    override suspend fun children(scope: ServerScope, ratingKey: String) = fail()
    override suspend fun seasons(scope: ServerScope, showRatingKey: String) = fail()
    override suspend fun allEpisodes(scope: ServerScope, showRatingKey: String) = fail()
    override suspend fun continueWatching(scope: ServerScope) = fail()
    override suspend fun search(scope: ServerScope, query: String, limit: Int) = fail()
    override suspend fun timeline(
        scope: ServerScope,
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
        sessionIdentifier: String,
    ) = fail()
    override suspend fun scrobble(scope: ServerScope, ratingKey: String) = fail()
    override suspend fun unscrobble(scope: ServerScope, ratingKey: String) = fail()
}

// --- sample data -----------------------------------------------------------------------

val filmsLibrary = Library(key = "1", title = "Films", kind = LibraryKind.MOVIE, uuid = "u1")
val tvLibrary = Library(key = "2", title = "Television", kind = LibraryKind.SHOW, uuid = "u2")

fun movie(
    ratingKey: String,
    title: String,
    titleSort: String = title,
    viewOffsetMs: Long = 0,
    viewCount: Int = 0,
    durationMs: Long = 7_200_000,
) = Movie(
    ratingKey = ratingKey,
    title = title,
    year = 2020,
    summary = "",
    thumbPath = "/thumb/$ratingKey",
    artPath = null,
    durationMs = durationMs,
    viewOffsetMs = viewOffsetMs,
    viewCount = viewCount,
    titleSort = titleSort,
    libraryKey = "1",
)

fun show(ratingKey: String, title: String, leafCount: Int = 10, viewedLeafCount: Int = 0) = Show(
    ratingKey = ratingKey,
    title = title,
    year = 2020,
    summary = "",
    thumbPath = null,
    artPath = null,
    durationMs = 0,
    viewOffsetMs = 0,
    viewCount = 0,
    titleSort = title,
    libraryKey = "2",
    childCount = 1,
    leafCount = leafCount,
    viewedLeafCount = viewedLeafCount,
)

fun episode(
    ratingKey: String,
    title: String,
    seasonIndex: Int = 1,
    episodeIndex: Int = 1,
    viewCount: Int = 0,
    viewOffsetMs: Long = 0,
    showRatingKey: String = "show-1",
) = Episode(
    ratingKey = ratingKey,
    title = title,
    year = 2020,
    summary = "",
    thumbPath = null,
    artPath = null,
    durationMs = 2_700_000,
    viewOffsetMs = viewOffsetMs,
    viewCount = viewCount,
    showRatingKey = showRatingKey,
    seasonRatingKey = "season-$seasonIndex",
    showTitle = "A Show",
    seasonIndex = seasonIndex,
    episodeIndex = episodeIndex,
)

fun collection(ratingKey: String, title: String, childCount: Int = 3) = MediaCollection(
    ratingKey = ratingKey,
    title = title,
    year = null,
    summary = "",
    thumbPath = null,
    artPath = null,
    durationMs = 0,
    viewOffsetMs = 0,
    viewCount = 0,
    titleSort = title,
    libraryKey = "1",
    childCount = childCount,
)
