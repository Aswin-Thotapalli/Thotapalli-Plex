package com.thotapalli.plex.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CLAUDE.md section 17 points 3 and 4. Everything the update check decides is exercised here
 * with no network: the manifest arrives as a string and the clock arrives as a lambda.
 */
class UpdateCheckerTest {

    private val day = UpdateChecker.CHECK_INTERVAL_MS

    // --- Version comparison -------------------------------------------------------------

    @Test
    fun aNewerVersionCodeIsOffered() = runTest {
        val result = checkerFor(currentVersionCode = 4).check()

        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals(UpdateTarget.MOBILE, available.update.target)
        assertEquals(7, available.update.versionCode)
        assertEquals("0.7.0", available.update.versionName)
        assertEquals(MOBILE_URL, available.update.downloadUrl)
        assertEquals(41_884_672L, available.update.sizeBytes)
    }

    @Test
    fun anOlderVersionCodeIsNotOffered() = runTest {
        // A build ahead of the published manifest, which happens on a local release build.
        assertEquals(UpdateCheckResult.UpToDate, checkerFor(currentVersionCode = 9).check())
    }

    @Test
    fun anEqualVersionCodeIsNotOffered() = runTest {
        assertEquals(UpdateCheckResult.UpToDate, checkerFor(currentVersionCode = 7).check())
    }

    /**
     * The comparison is on the version code, never the name. A name is a display string and
     * "0.10.0" sorts below "0.9.0" as text, which is exactly the trap this avoids.
     */
    @Test
    fun theVersionNameIsNotWhatIsCompared() = runTest {
        val manifest = manifest(mobileVersionCode = 11, mobileVersionName = "0.10.0")
        val result = checkerFor(currentVersionCode = 10, body = manifest).check()

        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("0.10.0", available.update.versionName)
    }

    @Test
    fun eachTargetReadsItsOwnEntry() = runTest {
        val onDesktop = checkerFor(currentVersionCode = 4, target = UpdateTarget.DESKTOP).check()
        val onTelevision = checkerFor(currentVersionCode = 4, target = UpdateTarget.TELEVISION).check()

        assertEquals(
            "https://example.invalid/download/v0.7.0/ThotapalliPlex-0.7.0.msi",
            assertIs<UpdateCheckResult.Available>(onDesktop).update.downloadUrl,
        )
        assertEquals(
            UpdateTarget.TELEVISION,
            assertIs<UpdateCheckResult.Available>(onTelevision).update.target,
        )
    }

    // --- The twenty four hour throttle --------------------------------------------------

    @Test
    fun theFirstEverCheckRuns() = runTest {
        val source = CountingSource(manifest())
        val checker = UpdateChecker(
            store = FakeKeyValueStore(),
            source = source,
            target = UpdateTarget.MOBILE,
            currentVersionCode = 4,
            manifestUrl = MANIFEST_URL,
            nowMs = { 0L },
        )

        assertIs<UpdateCheckResult.Available>(checker.check())
        assertEquals(1, source.calls)
    }

    @Test
    fun aSecondCheckInsideTheIntervalIsThrottledAndFetchesNothing() = runTest {
        val source = CountingSource(manifest())
        var now = 1_000L
        val checker = checker(source = source, currentVersionCode = 4, nowMs = { now })

        assertIs<UpdateCheckResult.Available>(checker.check())
        now += day - 1L
        assertEquals(UpdateCheckResult.Throttled, checker.check())

        assertEquals(1, source.calls, "a throttled check must not touch the network")
    }

    @Test
    fun aCheckAfterTheIntervalRunsAgain() = runTest {
        val source = CountingSource(manifest())
        var now = 1_000L
        val checker = checker(source = source, currentVersionCode = 4, nowMs = { now })

        checker.check()
        now += day
        assertIs<UpdateCheckResult.Available>(checker.check())

        assertEquals(2, source.calls)
    }

    /** The throttle is persisted, so relaunching the application does not re-check. */
    @Test
    fun theThrottleSurvivesARestart() = runTest {
        val store = FakeKeyValueStore()
        val source = CountingSource(manifest())
        val now = 5_000L

        checker(store = store, source = source, currentVersionCode = 4, nowMs = { now }).check()
        val afterRestart = checker(
            store = store.reopen(),
            source = source,
            currentVersionCode = 4,
            nowMs = { now + 60_000L },
        )

        assertEquals(UpdateCheckResult.Throttled, afterRestart.check())
        assertEquals(1, source.calls)
    }

    @Test
    fun forceIgnoresTheThrottle() = runTest {
        val source = CountingSource(manifest())
        val checker = checker(source = source, currentVersionCode = 4, nowMs = { 1_000L })

        checker.check()
        assertIs<UpdateCheckResult.Available>(checker.check(force = true))
        assertEquals(2, source.calls)
    }

    /** A clock that moved backwards must not lock the check out until it catches up. */
    @Test
    fun aBackwardsClockDoesNotSuppressTheCheckForever() = runTest {
        val source = CountingSource(manifest())
        var now = 10L * day
        val checker = checker(source = source, currentVersionCode = 4, nowMs = { now })

        checker.check()
        now = day
        assertIs<UpdateCheckResult.Available>(checker.check())
    }

    @Test
    fun forgetClearsTheThrottle() = runTest {
        val store = FakeKeyValueStore()
        val checker = checker(store = store, currentVersionCode = 4, nowMs = { 1_000L })

        checker.check()
        assertFalse(checker.isDue())
        checker.forget()

        assertTrue(checker.isDue())
        assertNull(store.getString(UpdateChecker.KEY_LAST_CHECKED_AT))
    }

    // --- Failure paths, none of which may throw ------------------------------------------

    @Test
    fun malformedJsonIsUnavailable() = runTest {
        val result = checkerFor(currentVersionCode = 4, body = "{ \"schema\": 1, ").check()

        assertIs<UpdateCheckResult.Unavailable>(result)
    }

    @Test
    fun aMissingTargetEntryIsUnavailable() = runTest {
        val withoutDesktop = """
            {
              "schema": 1,
              "versionName": "0.7.0",
              "targets": {
                "mobile": { "versionCode": 7, "versionName": "0.7.0", "url": "$MOBILE_URL" }
              }
            }
        """.trimIndent()

        val result = checkerFor(
            currentVersionCode = 4,
            target = UpdateTarget.DESKTOP,
            body = withoutDesktop,
        ).check()

        assertContains(assertIs<UpdateCheckResult.Unavailable>(result).reason, "desktop")
    }

    @Test
    fun aFailedFetchIsUnavailableAndDoesNotStartTheThrottle() = runTest {
        val store = FakeKeyValueStore()
        var fail = true
        val source = ManifestSource {
            if (fail) throw IllegalStateException("no route to host") else manifest()
        }
        val checker = checker(store = store, source = source, currentVersionCode = 4, nowMs = { 1_000L })

        assertIs<UpdateCheckResult.Unavailable>(checker.check())
        // The next launch retries rather than sitting out a day over one dropped request.
        fail = false
        assertIs<UpdateCheckResult.Available>(checker.check())
    }

    @Test
    fun aMalformedManifestDoesNotStartTheThrottle() = runTest {
        var body = "not json at all"
        val source = ManifestSource { body }
        val checker = checker(source = source, currentVersionCode = 4, nowMs = { 1_000L })

        assertIs<UpdateCheckResult.Unavailable>(checker.check())
        body = manifest()
        assertIs<UpdateCheckResult.Available>(checker.check())
    }

    @Test
    fun anUnknownSchemaIsRefused() = runTest {
        val future = manifest().replace("\"schema\": 1", "\"schema\": 2")

        val result = checkerFor(currentVersionCode = 4, body = future).check()

        assertContains(assertIs<UpdateCheckResult.Unavailable>(result).reason, "schema")
    }

    @Test
    fun theRequestedUrlIsTheOneConfigured() = runTest {
        var seen: String? = null
        val source = ManifestSource { url -> seen = url; manifest() }
        checker(source = source, currentVersionCode = 4, nowMs = { 0L }).check()

        assertEquals(MANIFEST_URL, seen)
    }

    // --- Manifest parsing ----------------------------------------------------------------

    @Test
    fun everyPublishedTargetIsRead() {
        val parsed = parseUpdateManifest(manifest())

        assertEquals(UPDATE_MANIFEST_SCHEMA, parsed.schema)
        assertEquals("0.7.0", parsed.versionName)
        assertEquals(3, parsed.entries.size)
        assertEquals(
            "https://example.invalid/notes/v0.7.0",
            parsed.entries.getValue(UpdateTarget.MOBILE).notesUrl,
        )
    }

    @Test
    fun anEntryMissingItsUrlIsRefused() {
        val body = """
            {
              "schema": 1,
              "versionName": "0.7.0",
              "targets": { "mobile": { "versionCode": 7, "versionName": "0.7.0" } }
            }
        """.trimIndent()

        val failure = assertFailsWith<ManifestFormatException> { parseUpdateManifest(body) }
        assertContains(failure.message.orEmpty(), "url")
    }

    @Test
    fun optionalFieldsMayBeAbsent() {
        val body = """
            {
              "schema": 1,
              "versionName": "0.7.0",
              "targets": { "tv": { "versionCode": 7, "versionName": "0.7.0", "url": "$TV_URL" } }
            }
        """.trimIndent()

        val entry = parseUpdateManifest(body).entries.getValue(UpdateTarget.TELEVISION)

        assertNull(entry.sizeBytes)
        assertNull(entry.notesUrl)
    }

    @Test
    fun theReaderHandlesEscapesAndWhitespace() {
        val body = "\n\t{ \"schema\":1 ,\"versionName\" : \"0.7.0\\u002d1\" , \"targets\" : { } }\n"

        val parsed = parseUpdateManifest(body)

        assertEquals("0.7.0-1", parsed.versionName)
        assertTrue(parsed.entries.isEmpty())
    }

    @Test
    fun trailingContentIsRefused() {
        assertFailsWith<ManifestFormatException> { parseUpdateManifest(manifest() + "}") }
    }

    @Test
    fun anArrayRootIsRefused() {
        assertFailsWith<ManifestFormatException> { parseUpdateManifest("[]") }
    }

    @Test
    fun theExampleManifestShapeMatchesTheDocumentedOne() {
        // The fixture above is a copy of what release.yml generates and of
        // docs/update-manifest.example.json. Asserting every target reads back keeps the three
        // in step: a field renamed on one side fails here rather than at a user's launch.
        val parsed = parseUpdateManifest(manifest())

        UpdateTarget.entries.forEach { target ->
            val entry = parsed.entries.getValue(target)
            assertEquals(target, entry.target)
            assertEquals(7, entry.versionCode)
            assertTrue(entry.downloadUrl.startsWith("https://"))
        }
    }

    // --- Fixtures -------------------------------------------------------------------------

    private fun checkerFor(
        currentVersionCode: Int,
        target: UpdateTarget = UpdateTarget.MOBILE,
        body: String = manifest(),
    ) = checker(
        source = ManifestSource { body },
        currentVersionCode = currentVersionCode,
        target = target,
        nowMs = { 0L },
    )

    private fun checker(
        store: FakeKeyValueStore = FakeKeyValueStore(),
        source: ManifestSource = ManifestSource { manifest() },
        currentVersionCode: Int,
        target: UpdateTarget = UpdateTarget.MOBILE,
        nowMs: () -> Long,
    ) = UpdateChecker(
        store = store,
        source = source,
        target = target,
        currentVersionCode = currentVersionCode,
        manifestUrl = MANIFEST_URL,
        nowMs = nowMs,
    )

    /** Counts fetches, which is how "the throttle fetched nothing" is asserted. */
    private class CountingSource(private val body: String) : ManifestSource {
        var calls: Int = 0
            private set

        override suspend fun fetch(url: String): String {
            calls++
            return body
        }
    }

    private fun manifest(
        mobileVersionCode: Int = 7,
        mobileVersionName: String = "0.7.0",
    ): String = """
        {
          "schema": 1,
          "versionName": "0.7.0",
          "releasedAt": "2026-08-18T09:15:00Z",
          "targets": {
            "mobile": {
              "versionCode": $mobileVersionCode,
              "versionName": "$mobileVersionName",
              "url": "$MOBILE_URL",
              "sizeBytes": 41884672,
              "notesUrl": "https://example.invalid/notes/v0.7.0"
            },
            "tv": {
              "versionCode": 7,
              "versionName": "0.7.0",
              "url": "$TV_URL",
              "sizeBytes": 41902080,
              "notesUrl": "https://example.invalid/notes/v0.7.0"
            },
            "desktop": {
              "versionCode": 7,
              "versionName": "0.7.0",
              "url": "https://example.invalid/download/v0.7.0/ThotapalliPlex-0.7.0.msi",
              "sizeBytes": 118293504,
              "notesUrl": "https://example.invalid/notes/v0.7.0"
            }
          }
        }
    """.trimIndent()

    private companion object {
        const val MANIFEST_URL = "https://example.invalid/releases/latest/download/update-manifest.json"
        const val MOBILE_URL = "https://example.invalid/download/v0.7.0/thotapalli-plex-mobile-0.7.0.apk"
        const val TV_URL = "https://example.invalid/download/v0.7.0/thotapalli-plex-tv-0.7.0.apk"
    }
}

class GithubManifestUrlTest {

    @Test
    fun theUrlIsTheGithubLatestReleaseAsset() {
        assertEquals(
            "https://github.com/example/thotapalli-plex/releases/latest/download/update-manifest.json",
            githubLatestManifestUrl("example/thotapalli-plex"),
        )
    }
}
