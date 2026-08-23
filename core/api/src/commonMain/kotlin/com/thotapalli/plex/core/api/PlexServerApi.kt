package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.api.dto.ActivityContainer
import com.thotapalli.plex.core.api.dto.DirectoryContainer
import com.thotapalli.plex.core.api.dto.HubContainer
import com.thotapalli.plex.core.api.dto.IdentityContainer
import com.thotapalli.plex.core.api.dto.MediaContainerResponse
import com.thotapalli.plex.core.api.dto.MetadataContainer
import com.thotapalli.plex.core.api.dto.UpdaterStatusContainer
import com.thotapalli.plex.core.api.mapper.toLibraries
import com.thotapalli.plex.core.api.mapper.toMediaDetail
import com.thotapalli.plex.core.api.mapper.toMediaItems
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.ServerUpdate
import com.thotapalli.plex.core.model.Show
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText

/**
 * Every server endpoint this client uses, from CLAUDE.md section 5.
 *
 * A [ServerScope] carries the base URI and the server access token, which is the only
 * token ever sent to a server. The account token never reaches here.
 */
class PlexServerApi(
    http: PlexHttp,
    private val identityHeaders: PlexHeaders,
) : PlexServerSource {

    private val client = http.client

    /** Confirms a connection is alive and is the server it claims to be. */
    override suspend fun identity(scope: ServerScope): String {
        val response = client.get("${scope.baseUri}/identity") { scope.apply(this) }
        response.requireSuccess("identity")
        return response.body<MediaContainerResponse<IdentityContainer>>()
            .mediaContainer.machineIdentifier
    }

    /** Every library the server exposes, movie and show only, sorted by title ascending. */
    override suspend fun libraries(scope: ServerScope): List<Library> {
        val response = client.get("${scope.baseUri}/library/sections") { scope.apply(this) }
        response.requireSuccess("library sections")
        return response.body<MediaContainerResponse<DirectoryContainer>>()
            .mediaContainer.directory.toLibraries()
    }

    /**
     * A library's contents, sorted by title ascending. The sort is fixed and has no
     * control in the interface. See CLAUDE.md section 14.
     */
    override suspend fun libraryContents(
        scope: ServerScope,
        libraryKey: String,
        kind: LibraryContentType,
        unwatchedOnly: Boolean,
    ): List<MediaItem> {
        val response = client.get("${scope.baseUri}/library/sections/$libraryKey/all") {
            parameter("type", kind.code)
            parameter("sort", "titleSort:asc")
            // The one filter the library screen offers.
            if (unwatchedOnly) parameter("unwatched", "1")
            scope.apply(this)
        }
        response.requireSuccess("library $libraryKey contents")
        return response.body<MediaContainerResponse<MetadataContainer>>()
            .mediaContainer.metadata.toMediaItems(libraryKey)
    }

    /** Collections in a library, shown first in the grid with a stacked poster treatment. */
    override suspend fun collections(scope: ServerScope, libraryKey: String): List<MediaCollection> {
        val response = client.get("${scope.baseUri}/library/sections/$libraryKey/collections") {
            scope.apply(this)
        }
        response.requireSuccess("library $libraryKey collections")
        return response.body<MediaContainerResponse<MetadataContainer>>()
            .mediaContainer.metadata.toMediaItems(libraryKey)
            .filterIsInstance<MediaCollection>()
            .sortedBy { it.titleSort.lowercase() }
    }

    /** Full metadata for one item, including the markers and chapters playback needs. */
    override suspend fun metadata(scope: ServerScope, ratingKey: String): MediaDetail? {
        val response = client.get("${scope.baseUri}/library/metadata/$ratingKey") {
            parameter("includeMarkers", "1")
            parameter("includeChapters", "1")
            scope.apply(this)
        }
        response.requireSuccess("metadata $ratingKey")
        return response.body<MediaContainerResponse<MetadataContainer>>()
            .mediaContainer.metadata.firstOrNull()?.toMediaDetail()
    }

    /** Direct children: seasons of a show, or items of a collection. */
    override suspend fun children(scope: ServerScope, ratingKey: String): List<MediaItem> {
        val response = client.get("${scope.baseUri}/library/metadata/$ratingKey/children") {
            scope.apply(this)
        }
        response.requireSuccess("children of $ratingKey")
        return response.body<MediaContainerResponse<MetadataContainer>>()
            .mediaContainer.metadata.toMediaItems()
    }

    override suspend fun seasons(scope: ServerScope, showRatingKey: String): List<Season> =
        children(scope, showRatingKey).filterIsInstance<Season>().sortedBy { it.index }

    /** Every episode of a show in one request, which is how the next unwatched is found. */
    override suspend fun allEpisodes(scope: ServerScope, showRatingKey: String): List<Episode> {
        val response = client.get("${scope.baseUri}/library/metadata/$showRatingKey/allLeaves") {
            scope.apply(this)
        }
        response.requireSuccess("allLeaves of $showRatingKey")
        return response.body<MediaContainerResponse<MetadataContainer>>()
            .mediaContainer.metadata.toMediaItems()
            .filterIsInstance<Episode>()
            .sortedWith(compareBy({ it.seasonIndex }, { it.episodeIndex }))
    }

    /**
     * Continue watching, driven entirely by server-side resume positions so a position set
     * on one device is the resume point on another. See CLAUDE.md section 1.
     *
     * Falls back to on deck, which older servers expose instead.
     */
    override suspend fun continueWatching(scope: ServerScope): List<MediaItem> {
        val hub = runCatching {
            val response = client.get("${scope.baseUri}/hubs/continueWatching/items") {
                scope.apply(this)
            }
            if (!response.status.value.let { it in 200..299 }) return@runCatching null
            response.body<MediaContainerResponse<MetadataContainer>>()
                .mediaContainer.metadata.toMediaItems()
        }.getOrNull()

        if (!hub.isNullOrEmpty()) return hub

        val response = client.get("${scope.baseUri}/library/onDeck") { scope.apply(this) }
        response.requireSuccess("on deck")
        return response.body<MediaContainerResponse<MetadataContainer>>()
            .mediaContainer.metadata.toMediaItems()
    }

    /**
     * Search across every library from one field, grouped into movies, shows and episodes
     * by the caller. See CLAUDE.md section 14.
     */
    override suspend fun search(scope: ServerScope, query: String, limit: Int): SearchResults {
        val response = client.get("${scope.baseUri}/hubs/search") {
            parameter("query", query)
            parameter("limit", limit)
            scope.apply(this)
        }
        response.requireSuccess("search")

        val container = response.body<MediaContainerResponse<HubContainer>>().mediaContainer
        val items = (container.hub.flatMap { it.metadata } + container.metadata).toMediaItems()

        return SearchResults(
            movies = items.filterIsInstance<Movie>().take(GROUP_LIMIT),
            shows = items.filterIsInstance<Show>().take(GROUP_LIMIT),
            episodes = items.filterIsInstance<Episode>().take(GROUP_LIMIT),
        )
    }

    /** Progress report. See CLAUDE.md section 5. */
    override suspend fun timeline(
        scope: ServerScope,
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
        sessionIdentifier: String,
    ) {
        val response = client.get("${scope.baseUri}/:/timeline") {
            parameter("ratingKey", ratingKey)
            parameter("key", "/library/metadata/$ratingKey")
            parameter("state", state.wire)
            parameter("time", positionMs)
            parameter("duration", durationMs)
            parameter("hasMDE", 1)
            parameter(PlexHeaderNames.SESSION_IDENTIFIER, sessionIdentifier)
            scope.apply(this)
        }
        response.requireSuccess("timeline $ratingKey")
    }

    override suspend fun scrobble(scope: ServerScope, ratingKey: String) {
        val response = client.get("${scope.baseUri}/:/scrobble") {
            parameter("key", ratingKey)
            parameter("identifier", LIBRARY_IDENTIFIER)
            scope.apply(this)
        }
        response.requireSuccess("scrobble $ratingKey")
    }

    override suspend fun unscrobble(scope: ServerScope, ratingKey: String) {
        val response = client.get("${scope.baseUri}/:/unscrobble") {
            parameter("key", ratingKey)
            parameter("identifier", LIBRARY_IDENTIFIER)
            scope.apply(this)
        }
        response.requireSuccess("unscrobble $ratingKey")
    }

    // --- server administration ------------------------------------------------------------
    //
    // These are management actions, not browsing. The owner runs the server and asked for the
    // controls the official app exposes: scan a library, refresh or analyze an item, remove it
    // from Continue Watching, or delete it. Each is a single documented server call.

    /**
     * The server's running background jobs, narrowed to library-scan work.
     *
     * Drives the live scan-progress indicator and the auto-pickup of newly scanned items.
     * Polled on a loop, so it must never take the interface down: any failure — no
     * connection, a shape the mapper does not recognise — returns an empty list rather than
     * throwing. See CLAUDE.md section 18 point 2.
     */
    suspend fun activities(scope: ServerScope): List<ServerActivity> = runCatching {
        val response = client.get("${scope.baseUri}/activities") { scope.apply(this) }
        if (response.status.value !in 200..299) return emptyList()
        response.body<MediaContainerResponse<ActivityContainer>>()
            .mediaContainer.activity
            .filter { it.type?.startsWith("library.") == true }
            .map { dto ->
                ServerActivity(
                    type = dto.type.orEmpty(),
                    title = dto.title.orEmpty(),
                    subtitle = dto.subtitle,
                    progress = (dto.progress.coerceIn(0, 100)) / 100f,
                    librarySectionId = dto.context?.librarySectionID,
                )
            }
    }.getOrDefault(emptyList())

    /** Trigger a scan of one library section, so newly added files are picked up. */
    suspend fun scanLibrary(scope: ServerScope, libraryKey: String) {
        val response = client.get("${scope.baseUri}/library/sections/$libraryKey/refresh") {
            scope.apply(this)
        }
        response.requireSuccess("scan library $libraryKey")
    }

    /** Refresh one item's metadata from its agents. */
    suspend fun refreshMetadata(scope: ServerScope, ratingKey: String) {
        val response = client.put("${scope.baseUri}/library/metadata/$ratingKey/refresh") {
            scope.apply(this)
        }
        response.requireSuccess("refresh metadata $ratingKey")
    }

    /** Analyze one item's media (bitrate, duration, resolution, and so on). */
    suspend fun analyze(scope: ServerScope, ratingKey: String) {
        val response = client.put("${scope.baseUri}/library/metadata/$ratingKey/analyze") {
            scope.apply(this)
        }
        response.requireSuccess("analyze $ratingKey")
    }

    /** Remove one item from the Continue Watching hub without changing its watched state. */
    suspend fun removeFromContinueWatching(scope: ServerScope, ratingKey: String) {
        val response = client.put("${scope.baseUri}/actions/removeFromContinueWatching") {
            parameter("ratingKey", ratingKey)
            scope.apply(this)
        }
        response.requireSuccess("remove from continue watching $ratingKey")
    }

    /** Permanently delete one item's media from the server. Irreversible. */
    suspend fun deleteItem(scope: ServerScope, ratingKey: String) {
        val response = client.delete("${scope.baseUri}/library/metadata/$ratingKey") {
            scope.apply(this)
        }
        response.requireSuccess("delete $ratingKey")
    }

    // --- server updates -------------------------------------------------------------------
    //
    // The server product updating itself, as the official app exposes. Distinct from the
    // client's own update flow, which lives in core/session (CLAUDE.md section 17).

    /**
     * The server's own update state. A pending release maps to an available [ServerUpdate];
     * an up-to-date server, or any failure, returns null. Polled on connect, so it must never
     * take the interface down — every failure is swallowed. See CLAUDE.md section 18 point 2.
     */
    suspend fun serverUpdateStatus(scope: ServerScope): ServerUpdate? = runCatching {
        val response = client.get("${scope.baseUri}/updater/status") { scope.apply(this) }
        if (response.status.value !in 200..299) return null
        val container = response.body<MediaContainerResponse<UpdaterStatusContainer>>().mediaContainer
        val release = container.release.firstOrNull()
        // A Release entry means the server has a newer build than the one running.
        val available = release != null
        ServerUpdate(
            available = available,
            version = release?.version ?: container.version,
            notes = release?.fixed,
            canApply = container.canInstall,
        )
    }.getOrNull()

    /** Ask the server to check for, and download, an available update. */
    suspend fun checkServerUpdate(scope: ServerScope) {
        val response = client.put("${scope.baseUri}/updater/check") {
            parameter("download", "1")
            scope.apply(this)
        }
        response.requireSuccess("check server update")
    }

    /**
     * Install the downloaded update and restart the server.
     *
     * Applying restarts the server, so the connection may drop before a response arrives:
     * a dropped or non-success response means "applying started", not a failure, and is
     * swallowed. The caller surfaces that the update is under way rather than an error.
     */
    suspend fun applyServerUpdate(scope: ServerScope) {
        runCatching {
            client.put("${scope.baseUri}/updater/apply") { scope.apply(this) }
        }
    }

    /**
     * The raw JSON body of a server endpoint, exactly as it came off the wire.
     *
     * Exists only to record fixtures. CLAUDE.md working rule 5 asks for real recordings
     * rather than authored ones, and a mapper is only worth as much as the response it was
     * written against. Nothing in the application calls this.
     */
    suspend fun rawJson(
        scope: ServerScope,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val response = client.get("${'$'}{scope.baseUri}${'$'}path") {
            query.forEach { (name, value) -> parameter(name, value) }
            scope.apply(this)
        }
        response.requireSuccess("raw ${'$'}path")
        return response.bodyAsText()
    }

    private suspend fun ServerScope.apply(builder: HttpRequestBuilder) {
        val identity = identityHeaders.headers()
        builder.headers {
            identity.forEach { (name, value) -> append(name, value) }
            append("Accept", "application/json")
            append(PlexHeaderNames.TOKEN, accessToken)
        }
    }

    private companion object {
        const val LIBRARY_IDENTIFIER = "com.plexapp.plugins.library"

        /** At most twenty per group on the search screen. See CLAUDE.md section 14. */
        const val GROUP_LIMIT = 20
    }
}

/** Where a server request goes and which token it carries. */
data class ServerScope(
    val baseUri: String,
    val accessToken: String,
) {
    init {
        require(!baseUri.endsWith("/")) { "baseUri must not end with a slash" }
    }
}

/** Plex type codes. See CLAUDE.md section 5. */
enum class LibraryContentType(val code: Int) {
    MOVIE(1),
    SHOW(2),
    SEASON(3),
    EPISODE(4),
    COLLECTION(18),
}

enum class TimelineState(val wire: String) {
    PLAYING("playing"),
    PAUSED("paused"),
    STOPPED("stopped"),
}

data class SearchResults(
    val movies: List<Movie>,
    val shows: List<Show>,
    val episodes: List<Episode>,
) {
    val isEmpty: Boolean get() = movies.isEmpty() && shows.isEmpty() && episodes.isEmpty()
}
