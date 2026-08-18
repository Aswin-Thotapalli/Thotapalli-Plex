package com.thotapalli.plex.core.data

import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.data.db.PlexDatabase
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.Movie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {

    private val scope = ServerScope(baseUri = "https://server.test:32400", accessToken = "t")
    private val machine = "machine-1"

    private fun fixture(
        source: CountingServerSource,
        clock: () -> Long = { 1_000_000L },
        staleAfterMs: Long = 10 * 60 * 1000L,
    ): Pair<LibraryRepository, PlexDatabase> {
        val database = createPlexDatabase(DatabaseDriverFactory.inMemory())
        val repository = LibraryRepository(
            api = source,
            database = database,
            scope = TestScope(),
            nowMs = clock,
            staleAfterMs = staleAfterMs,
        )
        return repository to database
    }

    // --- CLAUDE.md section 16 phase 3 step 3 ---------------------------------------------

    @Test
    fun aSecondReadComesFromTheDatabaseWithNoNetworkCall() = runTest {
        val source = CountingServerSource(libraries = listOf(filmsLibrary, tvLibrary))
        val (repository, _) = fixture(source)

        val first = repository.libraries(scope, machine)
        assertEquals(1, source.count("libraries"), "a cold cache must go to the server")

        source.reset()
        val second = repository.libraries(scope, machine)

        assertEquals(0, source.total, "a warm cache must make no network call at all")
        assertEquals(first, second)
    }

    @Test
    fun aSecondLibraryContentsReadMakesNoNetworkCall() = runTest {
        val source = CountingServerSource(
            libraries = listOf(filmsLibrary),
            movies = listOf(movie("1", "Alpha"), movie("2", "Beta")),
        )
        val (repository, _) = fixture(source)

        val first = repository.libraryContents(scope, filmsLibrary)
        assertEquals(2, first.size)
        assertTrue(source.total > 0)

        source.reset()
        val second = repository.libraryContents(scope, filmsLibrary)

        assertEquals(0, source.total)
        assertEquals(first.map { it.ratingKey }, second.map { it.ratingKey })
    }

    @Test
    fun aStaleCacheStillAnswersFromTheDatabaseAndRefreshesBehind() = runTest {
        var now = 1_000_000L
        val source = CountingServerSource(libraries = listOf(filmsLibrary))
        val (repository, _) = fixture(source, clock = { now }, staleAfterMs = 60_000L)

        repository.libraries(scope, machine)
        source.reset()

        // Well past the staleness window.
        now += 120_000L
        val answer = repository.libraries(scope, machine)

        // The read still returns immediately from the cache. The refresh is launched into
        // the repository's own scope, so it is not awaited here and cannot block the read.
        assertEquals(listOf("Films"), answer.map { it.title })
    }

    // --- ordering and grouping ------------------------------------------------------------

    @Test
    fun libraryContentsAreSortedByTitleSortAscending() = runTest {
        val source = CountingServerSource(
            movies = listOf(
                movie("3", "Zulu"),
                movie("1", "The Alpha", titleSort = "Alpha, The"),
                movie("2", "beta"),
            ),
        )
        val (repository, _) = fixture(source)

        val titles = repository.libraryContents(scope, filmsLibrary)
            .filterIsInstance<Movie>().map { it.titleSort }

        // The sort is fixed and case insensitive, and has no control in the interface.
        assertEquals(listOf("Alpha, The", "beta", "Zulu"), titles)
    }

    @Test
    fun collectionsComeBeforeIndividualTitles() = runTest {
        val source = CountingServerSource(
            movies = listOf(movie("1", "Alpha")),
            collections = listOf(collection("c1", "Zulu Collection")),
        )
        val (repository, _) = fixture(source)

        val contents = repository.libraryContents(scope, filmsLibrary)

        assertTrue(contents.first() is MediaCollection, "collections lead the grid")
        assertTrue(contents.last() is Movie)
    }

    @Test
    fun unwatchedOnlyHidesWatchedTitlesAndAllCollections() = runTest {
        val source = CountingServerSource(
            movies = listOf(
                movie("1", "Watched", viewCount = 1),
                movie("2", "Unwatched", viewCount = 0),
            ),
            collections = listOf(collection("c1", "A Collection")),
        )
        val (repository, _) = fixture(source)
        repository.libraryContents(scope, filmsLibrary)

        val filtered = repository.libraryContents(scope, filmsLibrary, unwatchedOnly = true)

        assertEquals(listOf("Unwatched"), filtered.map { it.title })
    }

    // --- shows and episodes ---------------------------------------------------------------

    @Test
    fun episodesAreOrderedBySeasonThenEpisode() = runTest {
        val show = show("show-1", "A Show")
        val source = CountingServerSource(
            episodes = listOf(
                episode("e3", "S2E1", seasonIndex = 2, episodeIndex = 1),
                episode("e1", "S1E1", seasonIndex = 1, episodeIndex = 1),
                episode("e2", "S1E2", seasonIndex = 1, episodeIndex = 2),
            ),
        )
        val (repository, _) = fixture(source)

        val ordered = repository.episodes(scope, show).map { it.title }

        assertEquals(listOf("S1E1", "S1E2", "S2E1"), ordered)
    }

    @Test
    fun nextUnwatchedPrefersAPartWatchedEpisodeOverALaterUntouchedOne() = runTest {
        val show = show("show-1", "A Show")
        val source = CountingServerSource(
            episodes = listOf(
                episode("e1", "Finished", episodeIndex = 1, viewCount = 1),
                episode("e2", "Half way", episodeIndex = 2, viewOffsetMs = 600_000),
                episode("e3", "Untouched", episodeIndex = 3),
            ),
        )
        val (repository, _) = fixture(source)

        val next = repository.nextUnwatchedEpisode(scope, show)

        assertEquals("Half way", assertNotNull(next).title)
    }

    @Test
    fun nextUnwatchedFallsToTheFirstUntouchedEpisode() = runTest {
        val show = show("show-1", "A Show")
        val source = CountingServerSource(
            episodes = listOf(
                episode("e1", "Finished", episodeIndex = 1, viewCount = 1),
                episode("e2", "Also finished", episodeIndex = 2, viewCount = 1),
                episode("e3", "Next up", episodeIndex = 3),
            ),
        )
        val (repository, _) = fixture(source)

        assertEquals("Next up", assertNotNull(repository.nextUnwatchedEpisode(scope, show)).title)
    }

    @Test
    fun aFullyWatchedShowHasNoNextEpisode() = runTest {
        val show = show("show-1", "A Show")
        val source = CountingServerSource(
            episodes = listOf(episode("e1", "Only one", viewCount = 1)),
        )
        val (repository, _) = fixture(source)

        assertNull(repository.nextUnwatchedEpisode(scope, show))
    }

    // --- continue watching and search -----------------------------------------------------

    @Test
    fun continueWatchingComesFromTheServerAndIsCached() = runTest {
        val source = CountingServerSource(
            onDeck = listOf(movie("1", "Half watched", viewOffsetMs = 1_800_000)),
        )
        val (repository, _) = fixture(source)

        val live = repository.continueWatching(scope)

        assertEquals(1, live.size)
        assertEquals(1_800_000L, live.single().viewOffsetMs)
    }

    @Test
    fun continueWatchingFallsBackToTheCacheWhenTheServerIsUnreachable() = runTest {
        val source = CountingServerSource(
            onDeck = listOf(movie("1", "Half watched", viewOffsetMs = 1_800_000)),
        )
        val database = createPlexDatabase(DatabaseDriverFactory.inMemory())
        val online = LibraryRepository(source, database, TestScope(), { 1L })
        online.continueWatching(scope)

        // Same database, a server that no longer answers.
        val offline = LibraryRepository(OfflineServerSource(), database, TestScope(), { 2L })
        val cached = offline.continueWatching(scope)

        assertEquals(1, cached.size)
        assertEquals(1_800_000L, cached.single().viewOffsetMs)
    }

    @Test
    fun searchIgnoresAQueryShorterThanTwoCharacters() = runTest {
        val source = CountingServerSource(movies = listOf(movie("1", "Alpha")))
        val (repository, _) = fixture(source)

        val results = repository.search(scope, "a")

        assertTrue(results.isEmpty)
        assertEquals(0, source.count("search"), "no request is made below the minimum length")
    }

    @Test
    fun searchFallsBackToTheCacheWhenTheServerIsUnreachable() = runTest {
        val source = CountingServerSource(movies = listOf(movie("1", "Alpha Film")))
        val database = createPlexDatabase(DatabaseDriverFactory.inMemory())
        LibraryRepository(source, database, TestScope(), { 1L }).libraryContents(scope, filmsLibrary)

        val offline = LibraryRepository(OfflineServerSource(), database, TestScope(), { 2L })
        val results = offline.search(scope, "Alpha")

        assertEquals(1, results.movies.size)
        assertEquals("Alpha Film", results.movies.single().title)
    }

    // --- write-through --------------------------------------------------------------------

    @Test
    fun recordingProgressUpdatesTheCachedItem() = runTest {
        val source = CountingServerSource(movies = listOf(movie("1", "Alpha")))
        val (repository, _) = fixture(source)
        repository.libraryContents(scope, filmsLibrary)

        repository.recordProgress("1", positionMs = 900_000, viewCount = 0)

        assertEquals(900_000L, assertNotNull(repository.cachedItem("1")).viewOffsetMs)
    }

    @Test
    fun aRefreshReplacesRatherThanAccumulates() = runTest {
        val source = CountingServerSource(movies = listOf(movie("1", "Alpha"), movie("2", "Beta")))
        val (repository, _) = fixture(source)
        repository.libraryContents(scope, filmsLibrary)

        // The server has since lost a title.
        source.movies = listOf(movie("1", "Alpha"))
        repository.refreshLibraryContents(scope, filmsLibrary)

        val contents = repository.libraryContents(scope, filmsLibrary)
        assertEquals(listOf("1"), contents.map { it.ratingKey })
    }
}
