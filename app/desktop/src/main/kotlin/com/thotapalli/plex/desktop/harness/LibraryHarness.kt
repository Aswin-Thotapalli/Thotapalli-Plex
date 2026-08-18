package com.thotapalli.plex.desktop.harness

import com.thotapalli.plex.core.api.ConnectionSelector
import com.thotapalli.plex.core.api.PlexHttp
import com.thotapalli.plex.core.api.PlexServerApi
import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.data.LibraryRepository
import com.thotapalli.plex.core.data.createPlexDatabase
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.core.session.FileKeyValueStore
import com.thotapalli.plex.core.session.IdentityHeaderProvider
import com.thotapalli.plex.core.session.PlexSession
import com.thotapalli.plex.core.session.ServerDirectory
import com.thotapalli.plex.core.session.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The phase 3 half of the harness: libraries, continue watching and search against the
 * real server, through the real repository so the cache path is exercised too.
 */
internal class LibraryHarness(
    private val identity: IdentityHeaderProvider,
    private val tokens: TokenStore,
    private val plain: FileKeyValueStore,
) {

    suspend fun run(command: String, argument: String?) {
        if (tokens.accountToken().isNullOrBlank()) {
            println("Not signed in. Run the signin command first.")
            return
        }

        val http = PlexHttp.create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val tvApi = PlexTvApi(http, identity)
            val directory = ServerDirectory(
                api = tvApi,
                selector = ConnectionSelector(http, identity, System::currentTimeMillis) {
                    System.nanoTime() / 1_000_000
                },
                store = plain,
                nowMs = System::currentTimeMillis,
            )
            val session = PlexSession(tvApi, tokens, directory, identity)

            val target = session.activeTarget()
            if (target == null) {
                println("No server answered inside the probe timeout.")
                return
            }

            println("Server: ${target.server.name}  ->  ${target.baseUri}")
            println()

            val serverScope = ServerScope(target.baseUri, target.accessToken)
            val urls = PlexUrls(target.baseUri, target.accessToken)
            val repository = LibraryRepository(
                api = PlexServerApi(http, identity),
                database = createPlexDatabase(DatabaseDriverFactory()),
                scope = scope,
                nowMs = System::currentTimeMillis,
            )

            when (command) {
                "libraries" -> libraries(repository, serverScope, target.server.machineIdentifier)
                "continue" -> continueWatching(repository, serverScope, urls)
                "search" -> search(repository, serverScope, argument)
                else -> println("Unknown library command: $command")
            }
        } finally {
            http.close()
        }
    }

    private suspend fun libraries(
        repository: LibraryRepository,
        scope: ServerScope,
        machineIdentifier: String,
    ) {
        val libraries = repository.libraries(scope, machineIdentifier)
        println("Libraries: ${libraries.size}")

        for (library in libraries) {
            println("  [${library.key}] ${library.title}  (${library.kind})")
            val contents = repository.libraryContents(scope, library)
            val collections = contents.filterIsInstance<MediaCollection>()
            println("      ${contents.size} entries, ${collections.size} of them collections")
            contents.take(5).forEach { println("        ${describe(it)}") }
            if (contents.size > 5) println("        ... and ${contents.size - 5} more")
        }
    }

    /** Phase 3 step 4: continue watching entries with their resume positions. */
    private suspend fun continueWatching(
        repository: LibraryRepository,
        scope: ServerScope,
        urls: PlexUrls,
    ) {
        val items = repository.continueWatching(scope)

        if (items.isEmpty()) {
            println("Continue Watching is empty, so the row is hidden and the library cards move up.")
            return
        }

        println("Continue Watching: ${items.size} entries")
        println()
        for (item in items) {
            println(describe(item))
            println("    resume position: ${formatMs(item.viewOffsetMs)} of ${formatMs(item.durationMs)}")
            println("    progress:        ${(item.progress * 100).toInt()}%")
            urls.artwork(item.thumbPath, width = 300, height = 450)?.let {
                println("    artwork:         ${it.take(110)}...")
            }
            println()
        }
    }

    private suspend fun search(repository: LibraryRepository, scope: ServerScope, query: String?) {
        if (query.isNullOrBlank()) {
            println("Usage: search <query>")
            return
        }

        val results = repository.search(scope, query)
        println("Search for \"$query\"")
        println()
        printGroup("Movies", results.movies)
        printGroup("Shows", results.shows)
        printGroup("Episodes", results.episodes)
        if (results.isEmpty) println("  no results")
    }

    private fun printGroup(title: String, items: List<MediaItem>) {
        if (items.isEmpty()) return
        println("$title (${items.size})")
        items.forEach { println("  ${describe(it)}") }
        println()
    }

    private fun describe(item: MediaItem): String = when (item) {
        is Movie -> "${item.title}${item.year?.let { " ($it)" }.orEmpty()}"
        is Show -> "${item.title}  ${item.viewedLeafCount}/${item.leafCount} watched"
        is Episode -> "${item.showTitle}  S${pad(item.seasonIndex)}E${pad(item.episodeIndex)}  ${item.title}"
        is MediaCollection -> "${item.title}  [collection of ${item.childCount}]"
        else -> item.title
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
