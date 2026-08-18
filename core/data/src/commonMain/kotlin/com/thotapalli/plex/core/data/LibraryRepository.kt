package com.thotapalli.plex.core.data

import com.thotapalli.plex.core.api.LibraryContentType
import com.thotapalli.plex.core.api.PlexServerSource
import com.thotapalli.plex.core.api.SearchResults
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.data.db.PlexDatabase
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Cache-first reads with a background refresh.
 *
 * Every read answers from the database immediately when it holds anything, and starts a
 * refresh in the background. A cold cache waits on the network, because there is nothing
 * else to show. The database is a cache and never the source of truth for anything the
 * server also knows. See CLAUDE.md section 7.
 */
class LibraryRepository(
    private val api: PlexServerSource,
    private val database: PlexDatabase,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
) {

    private val libraries = database.libraryQueries
    private val items = database.mediaItemQueries

    /** Libraries on the server, movie and show only, sorted by title ascending. */
    suspend fun libraries(server: ServerScope, machineIdentifier: String): List<Library> {
        val cached = libraries.selectAll(machineIdentifier).executeAsList().map { it.toLibrary() }
        val refreshedAt = libraries.oldestRefresh(machineIdentifier).executeAsOne().MIN

        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) refreshInBackground { refreshLibraries(server, machineIdentifier) }
            return cached
        }

        return refreshLibraries(server, machineIdentifier)
    }

    suspend fun refreshLibraries(server: ServerScope, machineIdentifier: String): List<Library> {
        val fresh = api.libraries(server)
        val at = nowMs()
        database.transaction {
            libraries.deleteForServer(machineIdentifier)
            fresh.forEach {
                libraries.upsert(
                    key = it.key,
                    title = it.title,
                    kind = it.kind.name,
                    uuid = it.uuid,
                    server_id = machineIdentifier,
                    refreshed_at = at,
                )
            }
        }
        return fresh
    }

    /**
     * A library's contents. Collections come first with a stacked poster treatment, then
     * individual titles. See CLAUDE.md section 14.
     */
    suspend fun libraryContents(
        server: ServerScope,
        library: Library,
        unwatchedOnly: Boolean = false,
    ): List<MediaItem> {
        val kind = library.kind.itemKind() ?: return emptyList()

        val cached = readContents(library.key, kind, unwatchedOnly)
        val refreshedAt = items.newestRefreshInLibrary(library.key, kind.name).executeAsOne().MAX

        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) {
                refreshInBackground { refreshLibraryContents(server, library) }
            }
            return cached
        }

        refreshLibraryContents(server, library)
        return readContents(library.key, kind, unwatchedOnly)
    }

    suspend fun refreshLibraryContents(server: ServerScope, library: Library) {
        val kind = library.kind.itemKind() ?: return
        val contentType = when (library.kind) {
            LibraryKind.MOVIE -> LibraryContentType.MOVIE
            LibraryKind.SHOW -> LibraryContentType.SHOW
            LibraryKind.UNSUPPORTED -> return
        }

        val fresh = api.libraryContents(server, library.key, contentType)
        val collections = runCatching { api.collections(server, library.key) }.getOrDefault(emptyList())
        val at = nowMs()

        database.transaction {
            items.deleteInLibrary(library.key, kind.name)
            items.deleteInLibrary(library.key, ItemKind.COLLECTION.name)
            (fresh + collections).forEach { write(it, library.key, at) }
        }
    }

    private fun readContents(
        libraryKey: String,
        kind: ItemKind,
        unwatchedOnly: Boolean,
    ): List<MediaItem> {
        val titles = if (unwatchedOnly) {
            items.selectUnwatchedInLibrary(libraryKey, kind.name).executeAsList()
        } else {
            items.selectInLibrary(libraryKey, kind.name).executeAsList()
        }.toMediaItems()

        // A collection is never "unwatched", so the filter applies to titles only.
        val collections = if (unwatchedOnly) {
            emptyList()
        } else {
            items.selectInLibrary(libraryKey, ItemKind.COLLECTION.name)
                .executeAsList().toMediaItems().filterIsInstance<MediaCollection>()
        }

        return collections + titles
    }

    /** Seasons of a show, ordered by index. */
    suspend fun seasons(server: ServerScope, show: Show): List<Season> {
        val cached = items.selectChildren(show.ratingKey).executeAsList()
            .toMediaItems().filterIsInstance<Season>()

        if (cached.isNotEmpty()) {
            refreshInBackground { refreshSeasons(server, show) }
            return cached
        }

        refreshSeasons(server, show)
        return items.selectChildren(show.ratingKey).executeAsList()
            .toMediaItems().filterIsInstance<Season>()
    }

    suspend fun refreshSeasons(server: ServerScope, show: Show) {
        val fresh = api.seasons(server, show.ratingKey)
        val at = nowMs()
        database.transaction { fresh.forEach { write(it, show.libraryKey, at) } }
    }

    /** Every episode of a show, which is how the next unwatched one is found. */
    suspend fun episodes(server: ServerScope, show: Show): List<Episode> {
        val cached = items.selectEpisodesOfShow(show.ratingKey).executeAsList()
            .toMediaItems().filterIsInstance<Episode>()

        if (cached.isNotEmpty()) {
            refreshInBackground { refreshEpisodes(server, show) }
            return cached
        }

        refreshEpisodes(server, show)
        return items.selectEpisodesOfShow(show.ratingKey).executeAsList()
            .toMediaItems().filterIsInstance<Episode>()
    }

    suspend fun refreshEpisodes(server: ServerScope, show: Show) {
        val fresh = api.allEpisodes(server, show.ratingKey)
        val at = nowMs()
        database.transaction { fresh.forEach { write(it, show.libraryKey, at) } }
    }

    /** The next unwatched episode, which is the show detail screen's primary action. */
    suspend fun nextUnwatchedEpisode(server: ServerScope, show: Show): Episode? {
        val all = episodes(server, show)
        // A partly watched episode is resumed before an untouched later one.
        return all.firstOrNull { it.viewOffsetMs > 0 && it.viewCount == 0 }
            ?: all.firstOrNull { it.viewCount == 0 }
    }

    suspend fun collectionChildren(server: ServerScope, collection: MediaCollection): List<MediaItem> {
        val cached = items.selectChildren(collection.ratingKey).executeAsList().toMediaItems()
        if (cached.isNotEmpty()) return cached

        val fresh = api.children(server, collection.ratingKey)
        val at = nowMs()
        database.transaction {
            fresh.forEach { item ->
                val row = item.toRow(collection.libraryKey, at).copy(parentKey = collection.ratingKey)
                write(row)
            }
        }
        return fresh
    }

    /**
     * Full metadata, always from the server.
     *
     * Markers, chapters and parts are what playback is built on, and a stale marker skips
     * the wrong part of an episode, so this one read is never served from the cache.
     */
    suspend fun detail(server: ServerScope, ratingKey: String): MediaDetail? =
        api.metadata(server, ratingKey)

    /**
     * Continue watching, driven by server-side resume positions so a position set on one
     * device is the resume point on another. See CLAUDE.md section 1.
     */
    suspend fun continueWatching(server: ServerScope): List<MediaItem> {
        val fresh = runCatching { api.continueWatching(server) }.getOrNull()

        if (fresh != null) {
            val at = nowMs()
            database.transaction { fresh.forEach { write(it, it.libraryKeyOrEmpty(), at) } }
            return fresh
        }

        // Offline. The cache still knows what was part way through.
        return items.selectInProgress().executeAsList().toMediaItems()
    }

    /** Search across every library from one field. See CLAUDE.md section 14. */
    suspend fun search(server: ServerScope, query: String): SearchResults {
        if (query.length < MIN_QUERY_LENGTH) return SearchResults(emptyList(), emptyList(), emptyList())

        return runCatching { api.search(server, query) }.getOrElse {
            // Offline search falls back to the cache rather than showing nothing.
            val cached = items.search(query, SEARCH_LIMIT.toLong()).executeAsList().toMediaItems()
            SearchResults(
                movies = cached.filterIsInstance<Movie>().take(GROUP_LIMIT),
                shows = cached.filterIsInstance<Show>().take(GROUP_LIMIT),
                episodes = cached.filterIsInstance<Episode>().take(GROUP_LIMIT),
            )
        }
    }

    /** Records a local watch state change so the interface updates before the server replies. */
    fun recordProgress(ratingKey: String, positionMs: Long, viewCount: Int) {
        items.updateProgress(positionMs, viewCount.toLong(), ratingKey)
    }

    fun cachedItem(ratingKey: String): MediaItem? =
        items.selectByRatingKey(ratingKey).executeAsOneOrNull()?.toMediaItem()

    private fun MediaItem.libraryKeyOrEmpty(): String = when (this) {
        is Movie -> libraryKey
        is Show -> libraryKey
        is MediaCollection -> libraryKey
        else -> ""
    }

    private fun write(item: MediaItem, libraryKey: String, atMs: Long) =
        write(item.toRow(libraryKey, atMs))

    private fun write(row: ItemRow) = items.upsert(
        rating_key = row.ratingKey,
        library_key = row.libraryKey,
        parent_key = row.parentKey,
        kind = row.kind,
        title = row.title,
        title_sort = row.titleSort,
        year = row.year,
        summary = row.summary,
        thumb_path = row.thumbPath,
        art_path = row.artPath,
        duration_ms = row.durationMs,
        view_offset_ms = row.viewOffsetMs,
        view_count = row.viewCount,
        season_index = row.seasonIndex,
        episode_index = row.episodeIndex,
        show_rating_key = row.showRatingKey,
        show_title = row.showTitle,
        child_count = row.childCount,
        leaf_count = row.leafCount,
        viewed_leaf_count = row.viewedLeafCount,
        refreshed_at = row.refreshedAt,
    )

    private fun isStale(refreshedAtMs: Long?): Boolean =
        refreshedAtMs == null || nowMs() - refreshedAtMs >= staleAfterMs

    /**
     * A background refresh must never take down the caller. The cached answer has already
     * been returned by the time this runs, so a failure here means the next read simply
     * finds the same stale data and tries again.
     */
    private fun refreshInBackground(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }

    private fun LibraryKind.itemKind(): ItemKind? = when (this) {
        LibraryKind.MOVIE -> ItemKind.MOVIE
        LibraryKind.SHOW -> ItemKind.SHOW
        LibraryKind.UNSUPPORTED -> null
    }

    private companion object {
        const val DEFAULT_STALE_AFTER_MS = 10 * 60 * 1000L
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_LIMIT = 60
        const val GROUP_LIMIT = 20
    }
}
