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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cache-first reads with a background refresh.
 *
 * Every read answers from the database immediately when it holds anything, and starts a
 * refresh in the background. A cold cache waits on the network, because there is nothing
 * else to show. The database is a cache and never the source of truth for anything the
 * server also knows. See CLAUDE.md section 7.
 *
 * SQLDelight calls block the calling thread, so every database access here is confined to
 * [dbContext] — a single-slot dispatcher shared with the download and timeline stores. That keeps
 * the library grid and continue-watching queries off the UI thread (they are launched from
 * viewModelScope, which is the main thread) and serialises access to the desktop's single JDBC
 * connection so two threads never touch it at once. Network work stays off [dbContext].
 */
class LibraryRepository(
    private val api: PlexServerSource,
    private val database: PlexDatabase,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    private val dbContext: CoroutineDispatcher = defaultDbDispatcher(),
) {

    private val libraries = database.libraryQueries

    /**
     * The catalogue. Written ONLY by the refresh methods below, each of which writes a complete set
     * in one transaction — every title in a library, every season, every episode, every child of a
     * collection. Nothing in this class writes a single row into it, and nothing outside this class
     * touches it at all. That invariant is what lets every read below treat "the cache holds rows for
     * this parent" as "a complete fetch for this parent happened".
     */
    private val items = database.mediaItemQueries

    /** Where the viewer got to, keyed by rating key. Point facts, no catalogue side effects. */
    private val watch = database.watchStateQueries

    /** The offline snapshot of the continue-watching hub. Read by nothing else. */
    private val onDeck = database.continueWatchingQueries

    /** Libraries on the server, movie and show only, sorted by title ascending. */
    suspend fun libraries(server: ServerScope, machineIdentifier: String): List<Library> {
        val (cached, refreshedAt) = withContext(dbContext) {
            libraries.selectAll(machineIdentifier).executeAsList().map { it.toLibrary() } to
                libraries.oldestRefresh(machineIdentifier).executeAsOne().MIN
        }

        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) refreshInBackground { refreshLibraries(server, machineIdentifier) }
            return cached
        }

        return refreshLibraries(server, machineIdentifier)
    }

    suspend fun refreshLibraries(server: ServerScope, machineIdentifier: String): List<Library> {
        val fresh = api.libraries(server)
        val at = nowMs()
        withContext(dbContext) {
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

        val (cached, refreshedAt) = withContext(dbContext) {
            readContents(library.key, kind, unwatchedOnly) to
                items.newestRefreshInLibrary(library.key, kind.name).executeAsOne().MAX
        }

        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) {
                refreshInBackground { refreshLibraryContents(server, library) }
            }
            return cached
        }

        refreshLibraryContents(server, library)
        return withContext(dbContext) { readContents(library.key, kind, unwatchedOnly) }
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

        withContext(dbContext) {
            database.transaction {
                items.deleteInLibrary(library.key, kind.name)
                items.deleteInLibrary(library.key, ItemKind.COLLECTION.name)
                (fresh + collections).forEach { write(it, library.key, at) }
            }
        }
    }

    // Must be called inside a withContext(dbContext) block.
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
        val (cached, refreshedAt) = withContext(dbContext) {
            items.selectChildren(show.ratingKey).executeAsList().toMediaItems().filterIsInstance<Season>() to
                items.newestRefreshOfChildren(show.ratingKey).executeAsOne().MAX
        }

        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) refreshInBackground { refreshSeasons(server, show) }
            return cached
        }

        // A failed refresh (offline) must not empty the screen — fall through to whatever is cached.
        runCatching { refreshSeasons(server, show) }
        return withContext(dbContext) {
            items.selectChildren(show.ratingKey).executeAsList().toMediaItems().filterIsInstance<Season>()
        }
    }

    suspend fun refreshSeasons(server: ServerScope, show: Show) {
        val fresh = api.seasons(server, show.ratingKey)
        val at = nowMs()
        withContext(dbContext) { database.transaction { fresh.forEach { write(it, show.libraryKey, at) } } }
    }

    /** Every episode of a show, which is how the next unwatched one is found. */
    suspend fun episodes(server: ServerScope, show: Show): List<Episode> {
        val (cached, refreshedAt) = withContext(dbContext) {
            items.selectEpisodesOfShow(show.ratingKey).executeAsList().toMediaItems().filterIsInstance<Episode>() to
                items.newestRefreshOfShowEpisodes(show.ratingKey).executeAsOne().MAX
        }

        // Rows here mean [refreshEpisodes] ran, which fetches every episode of the show in one
        // all-or-nothing request. Nothing else can put an episode in the catalogue — continue
        // watching writes watch_state and its own snapshot, never media_item — so a non-empty cache
        // is a complete one and needs no size check to prove it.
        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) refreshInBackground { refreshEpisodes(server, show) }
            return cached
        }

        // A failed refresh (offline) must not empty the screen — fall through to whatever is cached.
        runCatching { refreshEpisodes(server, show) }
        return withContext(dbContext) {
            items.selectEpisodesOfShow(show.ratingKey).executeAsList().toMediaItems().filterIsInstance<Episode>()
        }
    }

    suspend fun refreshEpisodes(server: ServerScope, show: Show) {
        val fresh = api.allEpisodes(server, show.ratingKey)
        val at = nowMs()
        withContext(dbContext) { database.transaction { fresh.forEach { write(it, show.libraryKey, at) } } }
    }

    /** The next unwatched episode, which is the show detail screen's primary action. */
    suspend fun nextUnwatchedEpisode(server: ServerScope, show: Show): Episode? {
        val all = episodes(server, show)
        // A partly watched episode is resumed before an untouched later one.
        return all.firstOrNull { it.viewOffsetMs > 0 && it.viewCount == 0 }
            ?: all.firstOrNull { it.viewCount == 0 }
    }

    suspend fun collectionChildren(server: ServerScope, collection: MediaCollection): List<MediaItem> {
        val (cached, refreshedAt) = withContext(dbContext) {
            items.selectChildren(collection.ratingKey).executeAsList().toMediaItems() to
                items.newestRefreshOfChildren(collection.ratingKey).executeAsOne().MAX
        }

        if (cached.isNotEmpty()) {
            if (isStale(refreshedAt)) refreshInBackground { refreshCollectionChildren(server, collection) }
            return cached
        }

        refreshCollectionChildren(server, collection)
        return withContext(dbContext) {
            items.selectChildren(collection.ratingKey).executeAsList().toMediaItems()
        }
    }

    suspend fun refreshCollectionChildren(server: ServerScope, collection: MediaCollection) {
        val fresh = api.children(server, collection.ratingKey)
        val at = nowMs()
        withContext(dbContext) {
            database.transaction {
                fresh.forEach { item ->
                    write(item.toRow(collection.libraryKey, at).copy(parentKey = collection.ratingKey))
                }
            }
        }
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
            withContext(dbContext) {
                database.transaction {
                    // Two writes, neither of which touches the catalogue.
                    //
                    // The hub's resume positions are point facts and go to watch_state, where the
                    // episode list and the library badges pick them up through the join. The hub's
                    // own shape — which items, in which order — is a snapshot of one endpoint and
                    // goes to its own table, replaced wholesale so an item finished elsewhere leaves
                    // the row instead of lingering.
                    //
                    // What used to happen here was a write into media_item, and that is the bug this
                    // separation exists to make impossible: a handful of hub entries landing in the
                    // catalogue were indistinguishable from a browsed set, so one on-deck episode
                    // could stand in for a show's entire episode list.
                    onDeck.deleteAll()
                    fresh.forEachIndexed { index, item ->
                        writeWatchState(item, at)
                        writeSnapshot(item, index, at)
                    }
                }
            }
            return fresh
        }

        // Offline. The last snapshot still knows what was part way through, including items the
        // catalogue has never held because they were never browsed.
        return withContext(dbContext) { onDeck.selectAll().executeAsList().toSnapshotItems() }
    }

    /** Search across every library from one field. See CLAUDE.md section 14. */
    suspend fun search(server: ServerScope, query: String): SearchResults {
        if (query.length < MIN_QUERY_LENGTH) return SearchResults(emptyList(), emptyList(), emptyList())

        return runCatching { api.search(server, query) }.getOrElse {
            // Offline search falls back to the cache rather than showing nothing. A substring LIKE on a
            // small, disposable cache is a deliberate fallback-only full scan (prefix-only would miss
            // mid-title matches, which matters more here than the scan on a few thousand rows).
            val cached = withContext(dbContext) {
                items.search(query, SEARCH_LIMIT.toLong()).executeAsList().toMediaItems()
            }
            SearchResults(
                movies = cached.filterIsInstance<Movie>().take(GROUP_LIMIT),
                shows = cached.filterIsInstance<Show>().take(GROUP_LIMIT),
                episodes = cached.filterIsInstance<Episode>().take(GROUP_LIMIT),
            )
        }
    }

    /** Records a local watch state change so the interface updates before the server replies. */
    suspend fun recordProgress(ratingKey: String, positionMs: Long, viewCount: Int) {
        withContext(dbContext) {
            runCatching { watch.upsert(ratingKey, positionMs, viewCount.toLong(), nowMs()) }
        }
    }

    /**
     * Keeps the cache in step with what is being watched right now.
     *
     * Timeline reports go to the server, but the detail screen's "next unwatched", the continue-
     * watching fallback and the library watched badges read this cache, which is otherwise only
     * refreshed on a browse. Without this, an episode you just finished still looks unwatched with a
     * stale resume point until the next refresh — so "Play" resumes an already-watched episode, or an
     * earlier point in the current one. Called from the same place progress is reported (§5).
     */
    suspend fun recordLocalOffset(ratingKey: String, positionMs: Long) {
        withContext(dbContext) { runCatching { watch.setOffset(ratingKey, positionMs, nowMs()) } }
    }

    /** Marks an item watched in the cache once it passes the scrobble threshold (§5). */
    suspend fun recordLocalWatched(ratingKey: String) {
        withContext(dbContext) { runCatching { watch.markWatched(ratingKey, nowMs()) } }
    }

    suspend fun cachedItem(ratingKey: String): MediaItem? = withContext(dbContext) {
        items.selectByRatingKey(ratingKey).executeAsOneOrNull()?.toItemRow()?.toMediaItem()
    }

    private fun MediaItem.libraryKeyOrEmpty(): String = when (this) {
        is Movie -> libraryKey
        is Show -> libraryKey
        is MediaCollection -> libraryKey
        else -> ""
    }

    private fun write(item: MediaItem, libraryKey: String, atMs: Long) =
        write(item.toRow(libraryKey, atMs))

    /**
     * Writes one item of a complete set: the catalogue row, and the server's watch state for it.
     *
     * The watch state half matters — the server's view counts are what the unwatched filter and the
     * badges read, and they now live in their own table. Splitting the write does not weaken it:
     * both halves run inside the caller's transaction, so a set still lands whole or not at all.
     */
    private fun write(row: ItemRow) {
        items.upsert(
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
            season_index = row.seasonIndex,
            episode_index = row.episodeIndex,
            show_rating_key = row.showRatingKey,
            show_title = row.showTitle,
            child_count = row.childCount,
            leaf_count = row.leafCount,
            viewed_leaf_count = row.viewedLeafCount,
            refreshed_at = row.refreshedAt,
        )
        watch.upsert(
            rating_key = row.ratingKey,
            view_offset_ms = row.viewOffsetMs,
            view_count = row.viewCount,
            updated_at = row.refreshedAt,
        )
    }

    /** The server's resume position for one item. Carries no catalogue meaning whatsoever. */
    private fun writeWatchState(item: MediaItem, atMs: Long) = watch.upsert(
        rating_key = item.ratingKey,
        view_offset_ms = item.viewOffsetMs,
        view_count = item.viewCount.toLong(),
        updated_at = atMs,
    )

    /** One entry of the continue-watching hub snapshot, in the server's own order. */
    private fun writeSnapshot(item: MediaItem, sortIndex: Int, atMs: Long) {
        val row = item.toRow(item.libraryKeyOrEmpty(), atMs)
        onDeck.upsert(
            rating_key = row.ratingKey,
            sort_index = sortIndex.toLong(),
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
            season_index = row.seasonIndex,
            episode_index = row.episodeIndex,
            show_rating_key = row.showRatingKey,
            show_title = row.showTitle,
            child_count = row.childCount,
            leaf_count = row.leafCount,
            viewed_leaf_count = row.viewedLeafCount,
            refreshed_at = row.refreshedAt,
        )
    }

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

/**
 * The single-slot dispatcher for all database work: one process-wide instance, so every component
 * that takes the default (the repository and the download/timeline stores) serialises against the
 * SAME slot rather than getting its own. There is one database, so one slot is right — it keeps DB
 * work off the main thread and guarantees exactly one operation at a time, which is what the
 * desktop's single JDBC connection needs. AppContainer passes this same instance explicitly.
 *
 * It must be a shared singleton, not a fresh limitedParallelism(1) per call: two independent
 * limiters would each admit one task, so two default-using components could touch the connection
 * concurrently — the very race this exists to prevent.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val sharedDbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

fun defaultDbDispatcher(): CoroutineDispatcher = sharedDbDispatcher
