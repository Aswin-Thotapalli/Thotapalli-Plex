package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.api.dto.DirectoryContainer
import com.thotapalli.plex.core.api.dto.HubContainer
import com.thotapalli.plex.core.api.dto.MediaContainerResponse
import com.thotapalli.plex.core.api.dto.MetadataContainer
import com.thotapalli.plex.core.api.dto.ResourceDto
import com.thotapalli.plex.core.api.mapper.toLibraries
import com.thotapalli.plex.core.api.mapper.toMediaDetail
import com.thotapalli.plex.core.api.mapper.toMediaItems
import com.thotapalli.plex.core.api.mapper.toPlexServers
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MarkerType
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.partiallyWatched
import com.thotapalli.plex.core.model.progress
import com.thotapalli.plex.core.model.watched
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private inline fun <reified T> container(name: String): T =
    plexJson.decodeFromString<MediaContainerResponse<T>>(fixture(name)).mediaContainer

class LibraryMapperTest {

    @Test
    fun mapsMovieAndShowLibrariesAndDropsTheRest() {
        val libraries = container<DirectoryContainer>("library_sections.json").directory.toLibraries()

        // Music and photo libraries are permanently out of scope, so they never survive.
        assertEquals(2, libraries.size)
        assertEquals(listOf("Films", "Television"), libraries.map { it.title })
        assertEquals(LibraryKind.MOVIE, libraries[0].kind)
        assertEquals(LibraryKind.SHOW, libraries[1].kind)
    }

    @Test
    fun carriesTheKeyAndUuid() {
        val films = container<DirectoryContainer>("library_sections.json").directory.toLibraries().first()

        assertEquals("1", films.key)
        assertEquals("b1f0b1a2-0000-4000-8000-000000000001", films.uuid)
    }

    @Test
    fun sortsByTitleAscending() {
        val titles = container<DirectoryContainer>("library_sections.json")
            .directory.toLibraries().map { it.title }

        assertEquals(titles.sortedBy { it.lowercase() }, titles)
    }
}

class MovieMapperTest {

    private val movies = container<MetadataContainer>("library_movies.json")
        .metadata.toMediaItems("1").filterIsInstance<Movie>()

    @Test
    fun mapsEveryMovie() {
        assertEquals(3, movies.size)
    }

    @Test
    fun mapsTheFieldsSectionSixDeclares() {
        val first = movies.first()

        assertEquals("10241", first.ratingKey)
        assertEquals("An Ordinary Film", first.title)
        assertEquals(2019, first.year)
        assertEquals("A film that exists so the mapper has something to map.", first.summary)
        assertEquals("/library/metadata/10241/thumb/1755100000", first.thumbPath)
        assertEquals("/library/metadata/10241/art/1755100000", first.artPath)
        assertEquals(7_241_000L, first.durationMs)
        assertEquals(0L, first.viewOffsetMs)
        assertEquals(1, first.viewCount)
        assertEquals("1", first.libraryKey)
    }

    @Test
    fun titleSortFallsBackToTheTitleWhenPlexOmitsIt() {
        assertEquals("An Ordinary Film", movies[0].titleSort)
        assertEquals("Second Feature, The", movies[1].titleSort)
    }

    @Test
    fun watchStateDerivesFromViewCountAndOffset() {
        assertTrue(movies[0].watched)
        assertFalse(movies[0].partiallyWatched)

        assertFalse(movies[1].watched)
        assertTrue(movies[1].partiallyWatched)
        assertEquals(0.25f, movies[1].progress)
    }

    @Test
    fun aZeroDurationDoesNotDivideByZero() {
        assertEquals(0f, movies[2].progress)
        assertFalse(movies[2].partiallyWatched)
    }

    @Test
    fun missingFieldsBecomeEmptyRatherThanNull() {
        assertEquals("", movies[2].summary)
        assertNull(movies[2].year)
        assertNull(movies[2].thumbPath)
    }
}

class ShowMapperTest {

    private val shows = container<MetadataContainer>("library_shows.json")
        .metadata.toMediaItems("2").filterIsInstance<Show>()

    @Test
    fun mapsCountsUsedByTheShowDetailScreen() {
        val long = shows.first()

        assertEquals(8, long.childCount)
        assertEquals(92, long.leafCount)
        assertEquals(61, long.viewedLeafCount)
        assertEquals(31, long.unwatchedLeafCount)
    }

    @Test
    fun anEntirelyUnwatchedShowReportsEveryLeafUnwatched() {
        val newcomer = shows[1]

        assertEquals(0, newcomer.viewedLeafCount)
        assertEquals(6, newcomer.unwatchedLeafCount)
    }
}

class SeasonMapperTest {

    private val seasons = container<MetadataContainer>("show_children.json")
        .metadata.toMediaItems().filterIsInstance<Season>()

    @Test
    fun mapsSeasonsIncludingSpecialsAtIndexZero() {
        assertEquals(3, seasons.size)
        assertEquals(listOf(0, 3, 1), seasons.map { it.index })
    }

    @Test
    fun carriesTheShowItBelongsTo() {
        val third = seasons.single { it.index == 3 }

        assertEquals("20501", third.showRatingKey)
        assertEquals("A Long Running Series", third.showTitle)
    }

    @Test
    fun aSeasonWithoutItsOwnArtFallsBackToTheShows() {
        val specials = seasons.single { it.index == 0 }
        val third = seasons.single { it.index == 3 }

        assertEquals("/library/metadata/20501/thumb/1755110000", specials.thumbPath)
        assertEquals("/library/metadata/20550/thumb/1755110500", third.thumbPath)
    }
}

class EpisodeMapperTest {

    private val detail = container<MetadataContainer>("episode_metadata.json")
        .metadata.single().toMediaDetail()

    @Test
    fun mapsTheShowAndSeasonItHangsFrom() {
        val episode = assertNotNull(detail).item as Episode

        assertEquals("20601", episode.ratingKey)
        assertEquals("The One With The Markers", episode.title)
        assertEquals("20501", episode.showRatingKey)
        assertEquals("20550", episode.seasonRatingKey)
        assertEquals("A Long Running Series", episode.showTitle)
        assertEquals(3, episode.seasonIndex)
        assertEquals(7, episode.episodeIndex)
    }

    @Test
    fun mapsIntroAndCreditsMarkersAndIgnoresUnknownTypes() {
        val markers = assertNotNull(detail).markers

        // The fixture also carries a "commercial" marker, which this client does not act
        // on. Guessing at it would skip the wrong part of the episode.
        assertEquals(2, markers.size)
        assertEquals(MarkerType.INTRO, markers[0].type)
        assertEquals(12_000L, markers[0].startMs)
        assertEquals(92_000L, markers[0].endMs)
        assertEquals(MarkerType.CREDITS, markers[1].type)
    }

    @Test
    fun markerContainsUsesAHalfOpenRange() {
        val intro = assertNotNull(detail).intro!!

        assertFalse(intro.contains(11_999))
        assertTrue(intro.contains(12_000))
        assertTrue(intro.contains(91_999))
        assertFalse(intro.contains(92_000))
    }

    @Test
    fun readsTheFrameRateThatDrivesRefreshRateMatching() {
        val part = assertNotNull(detail).primaryPart!!

        assertEquals(25.0f, part.frameRate)
    }

    @Test
    fun detectsHdrFromTheTransferCharacteristic() {
        val video = assertNotNull(detail).primaryPart!!.videoStreams.single()

        assertTrue(video.hdr, "smpte2084 is PQ, which is HDR")
        assertEquals(10, video.bitDepth)
    }
}

class MediaPartMapperTest {

    private val detail = assertNotNull(
        container<MetadataContainer>("movie_metadata.json").metadata.single().toMediaDetail(),
    )

    @Test
    fun mapsThePartAndItsContainer() {
        val part = assertNotNull(detail.primaryPart)

        assertEquals("88001", part.partId)
        assertEquals("/library/parts/88001/1700000100/file.mkv", part.fileKey)
        assertEquals("mkv", part.container)
        assertEquals(7_429_183_488L, part.sizeBytes)
    }

    @Test
    fun splitsStreamsByType() {
        val part = assertNotNull(detail.primaryPart)

        assertEquals(1, part.videoStreams.size)
        assertEquals(2, part.audioStreams.size)
        assertEquals(2, part.subtitleStreams.size)
    }

    @Test
    fun flagsFormatsThatOnlyReachAReceiverThroughPassthrough() {
        val audio = assertNotNull(detail.primaryPart).audioStreams

        assertTrue(audio.single { it.codec == "eac3" }.bitstreamOnly)
        assertTrue(audio.single { it.codec == "truehd" }.bitstreamOnly)
    }

    @Test
    fun distinguishesExternalSubtitlesFromEmbeddedOnes() {
        val subtitles = assertNotNull(detail.primaryPart).subtitleStreams

        // Only a sidecar carries a key of its own, and only a sidecar downloads separately.
        assertTrue(subtitles.single { it.codec == "srt" }.external)
        assertFalse(subtitles.single { it.codec == "pgs" }.external)
        assertTrue(subtitles.single { it.codec == "pgs" }.forced)
    }

    @Test
    fun a23976StreamIsNotMistakenForHdr() {
        val video = assertNotNull(detail.primaryPart).videoStreams.single()

        assertFalse(video.hdr, "bt709 is standard range")
        assertEquals(23.976f, video.frameRate)
    }

    @Test
    fun mapsChapters() {
        assertEquals(2, detail.chapters.size)
        assertEquals("Opening", detail.chapters.first().title)
    }

    @Test
    fun aMovieWithNoMarkersHasNoIntroOrCredits() {
        assertNull(detail.intro)
        assertNull(detail.credits)
    }
}

class CollectionMapperTest {

    @Test
    fun mapsCollectionsWithTheirChildCount() {
        val collections = container<MetadataContainer>("collections.json")
            .metadata.toMediaItems("1").filterIsInstance<MediaCollection>()

        assertEquals(2, collections.size)
        assertEquals("The Trilogy", collections[0].title)
        assertEquals("Trilogy, The", collections[0].titleSort)
        assertEquals(3, collections[0].childCount)
        assertEquals(11, collections[1].childCount)
    }
}

class ContinueWatchingMapperTest {

    private val items = container<MetadataContainer>("continue_watching.json").metadata.toMediaItems()

    @Test
    fun mapsAMixedListOfEpisodesAndMovies() {
        assertEquals(3, items.size)
        assertEquals(2, items.filterIsInstance<Episode>().size)
        assertEquals(1, items.filterIsInstance<Movie>().size)
    }

    @Test
    fun carriesTheResumePositionThatDrivesTheProgressBar() {
        val partial = items.first()

        assertEquals(900_000L, partial.viewOffsetMs)
        assertEquals(2_712_000L, partial.durationMs)
        assertTrue(partial.partiallyWatched)
    }

    @Test
    fun anUnstartedNextEpisodeIsNotPartiallyWatched() {
        val next = items.last()

        assertEquals(0L, next.viewOffsetMs)
        assertFalse(next.partiallyWatched)
    }
}

class SearchMapperTest {

    private val hubs = container<HubContainer>("search.json")

    @Test
    fun mapsEachHubIntoItsGroupAndDropsOutOfScopeTypes() {
        val items = hubs.hub.flatMap { it.metadata }.toMediaItems()

        assertEquals(1, items.filterIsInstance<Movie>().size)
        assertEquals(1, items.filterIsInstance<Show>().size)
        assertEquals(1, items.filterIsInstance<Episode>().size)
        // The fixture carries an artist hub. Music is out of scope and must not survive.
        assertEquals(3, items.size)
    }
}

class ResourceMapperTest {

    private val resources = plexJson.decodeFromString(
        ListSerializer(ResourceDto.serializer()),
        fixture("resources.json"),
    )

    @Test
    fun keepsOnlyMediaServersThatHandedBackAnAccessToken() {
        val servers = resources.toPlexServers()

        // A player client is not a server, and a server with no access token cannot be
        // reached, so keeping it would produce a server that fails every request.
        assertEquals(2, servers.size)
        assertEquals(listOf("Home Server", "Shared Library"), servers.map { it.name })
    }

    @Test
    fun aSharedLibraryArrivesUnownedWithItsOwnToken() {
        val shared = resources.toPlexServers().single { !it.owned }

        assertEquals("Shared Library", shared.name)
        assertEquals("server-access-token-shared", shared.accessToken)
    }

    @Test
    fun mapsEveryConnectionWithItsLocalAndRelayFlags() {
        val owned = resources.toPlexServers().single { it.owned }

        assertEquals(3, owned.connections.size)
        assertEquals(1, owned.connections.count { it.local })
        assertEquals(1, owned.connections.count { it.relay })
        assertEquals(
            "https://192-168-1-50.aaaa.plex.direct:32400",
            owned.connections.single { it.local }.uri,
        )
    }
}
