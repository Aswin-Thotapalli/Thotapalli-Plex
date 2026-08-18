package com.thotapalli.plex.core.playback

import com.thotapalli.plex.core.model.Marker
import com.thotapalli.plex.core.model.MarkerType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// --- CLAUDE.md section 9, refresh rate --------------------------------------------------

class RefreshRateTest {

    private fun mode(hz: Float, id: Int = 0, w: Int = 3840, h: Int = 2160) =
        DisplayMode(modeId = id, widthPx = w, heightPx = h, refreshRateHz = hz)

    /** The table in CLAUDE.md section 9, at 120 Hz. */
    @Test
    fun theSection9TableHolds() {
        assertTrue(RefreshRate.divides(120f, 23.976f), "23.976 into 120 is 5, within drift")
        assertTrue(RefreshRate.divides(120f, 24f), "24 into 120 is 5")
        assertFalse(RefreshRate.divides(120f, 25f), "25 into 120 is 4.8, uneven")
        assertTrue(RefreshRate.divides(120f, 29.97f), "29.97 into 120 is 4")
        assertTrue(RefreshRate.divides(120f, 30f), "30 into 120 is 4")
        assertFalse(RefreshRate.divides(120f, 50f), "50 into 120 is 2.4, uneven")
        assertTrue(RefreshRate.divides(120f, 59.94f), "59.94 into 120 is 2")
        assertTrue(RefreshRate.divides(120f, 60f), "60 into 120 is 2")
    }

    @Test
    fun twentyFourOnSixtyIsTheClassicJudderCase() {
        // 2:3 pulldown. This is the case the whole section exists for.
        assertFalse(RefreshRate.divides(60f, 24f))
        assertTrue(RefreshRate.divides(24f, 24f))
        assertTrue(RefreshRate.divides(48f, 24f))
        assertTrue(RefreshRate.divides(120f, 24f))
    }

    @Test
    fun aRefreshRateBelowTheContentRateNeverDivides() {
        assertFalse(RefreshRate.divides(24f, 60f))
        assertFalse(RefreshRate.divides(50f, 60f))
    }

    @Test
    fun anAlreadyCorrectModeIsLeftAlone() {
        val current = mode(120f)

        val decision = RefreshRate.decide(
            contentFrameRate = 24f,
            current = current,
            available = listOf(current, mode(60f, id = 1), mode(24f, id = 2)),
            enabled = true,
        )

        // An HDMI mode change blanks the screen for one to two seconds. Not doing it when
        // the current mode already works is the point of step 3.
        val keep = assertIs<RefreshRate.Decision.KeepCurrent>(decision)
        assertEquals(120f, keep.mode.refreshRateHz)
    }

    @Test
    fun theHighestEvenlyDividingRateWins() {
        val current = mode(60f, id = 0)

        val decision = RefreshRate.decide(
            contentFrameRate = 25f,
            current = current,
            available = listOf(current, mode(50f, id = 1), mode(100f, id = 2), mode(120f, id = 3)),
            enabled = true,
        )

        val switch = assertIs<RefreshRate.Decision.SwitchTo>(decision)
        assertEquals(100f, switch.target.refreshRateHz, "100 divides 25 evenly and beats 50")
    }

    @Test
    fun onlyModesAtTheCurrentResolutionAreConsidered() {
        val current = mode(60f, id = 0, w = 3840, h = 2160)
        val wrongResolution = DisplayMode(modeId = 1, widthPx = 1920, heightPx = 1080, refreshRateHz = 24f)

        val decision = RefreshRate.decide(
            contentFrameRate = 24f,
            current = current,
            available = listOf(current, wrongResolution),
            enabled = true,
        )

        // Dropping resolution to fix judder trades one visible problem for a worse one.
        assertIs<RefreshRate.Decision.NoSuitableMode>(decision)
    }

    @Test
    fun theSettingBeingOffSkipsEverything() {
        val decision = RefreshRate.decide(24f, mode(60f), listOf(mode(24f, 1)), enabled = false)
        assertIs<RefreshRate.Decision.Disabled>(decision)
    }

    @Test
    fun anUnknownFrameRateAttemptsNoModeChange() {
        val decision = RefreshRate.decide(null, mode(60f), listOf(mode(24f, 1)), enabled = true)
        assertIs<RefreshRate.Decision.UnknownFrameRate>(decision)
    }
}

// --- CLAUDE.md section 5, progress reporting --------------------------------------------

private class RecordingSink : TimelineSink {
    val timelines = mutableListOf<Triple<String, TimelineState, Long>>()
    val scrobbles = mutableListOf<String>()

    override suspend fun timeline(
        ratingKey: String,
        state: TimelineState,
        positionMs: Long,
        durationMs: Long,
    ) {
        timelines += Triple(ratingKey, state, positionMs)
    }

    override suspend fun scrobble(ratingKey: String) {
        scrobbles += ratingKey
    }
}

class TimelineReporterTest {

    private var now = 0L
    private val sink = RecordingSink()
    private val reporter = TimelineReporter(sink) { now }

    private val duration = 100_000L

    @Test
    fun playingReportsEveryTenSeconds() = runTest {
        reporter.startItem("k")

        assertEquals(TimelineAction.SENT, reporter.onTick("k", TimelineState.PLAYING, 0, duration))

        now = 5_000
        assertEquals(TimelineAction.SKIPPED, reporter.onTick("k", TimelineState.PLAYING, 5_000, duration))

        now = 10_000
        assertEquals(TimelineAction.SENT, reporter.onTick("k", TimelineState.PLAYING, 10_000, duration))

        assertEquals(2, sink.timelines.size)
    }

    @Test
    fun pauseReportsImmediatelyRegardlessOfTheInterval() = runTest {
        reporter.startItem("k")
        reporter.onTick("k", TimelineState.PLAYING, 0, duration)

        now = 1_000
        val action = reporter.onTick("k", TimelineState.PAUSED, 1_000, duration)

        assertEquals(TimelineAction.SENT, action)
        assertEquals(TimelineState.PAUSED, sink.timelines.last().second)
    }

    @Test
    fun seekCompletionReportsImmediately() = runTest {
        reporter.startItem("k")
        reporter.onTick("k", TimelineState.PLAYING, 0, duration)

        now = 500
        assertEquals(
            TimelineAction.SENT,
            reporter.onImmediate("k", TimelineState.PLAYING, 45_000, duration),
        )
        assertEquals(45_000L, sink.timelines.last().third)
    }

    @Test
    fun pastNinetyTwoPercentScrobblesOnceAndThenGoesQuiet() = runTest {
        reporter.startItem("k")

        now = 0
        assertEquals(
            TimelineAction.SCROBBLED,
            reporter.onTick("k", TimelineState.PLAYING, 92_000, duration),
        )

        // Further position updates would drag the server's position back into the item and
        // undo the watched state.
        now = 20_000
        assertEquals(
            TimelineAction.SUPPRESSED,
            reporter.onTick("k", TimelineState.PLAYING, 95_000, duration),
        )
        now = 40_000
        assertEquals(
            TimelineAction.SUPPRESSED,
            reporter.onImmediate("k", TimelineState.PAUSED, 96_000, duration),
        )

        assertEquals(listOf("k"), sink.scrobbles)
    }

    @Test
    fun justBelowNinetyTwoPercentDoesNotScrobble() = runTest {
        reporter.startItem("k")

        val action = reporter.onTick("k", TimelineState.PLAYING, 91_000, duration)

        assertEquals(TimelineAction.SENT, action)
        assertTrue(sink.scrobbles.isEmpty())
    }

    @Test
    fun theStopReportIsSentEvenAfterAScrobble() = runTest {
        reporter.startItem("k")
        reporter.onTick("k", TimelineState.PLAYING, 95_000, duration)

        reporter.onStop("k", 99_000, duration)

        // The scrobble suppression stops position updates, not the end of the session.
        assertEquals(TimelineState.STOPPED, sink.timelines.last().second)
    }

    @Test
    fun aNewItemClearsTheScrobbleLatch() = runTest {
        reporter.startItem("first")
        reporter.onTick("first", TimelineState.PLAYING, 95_000, duration)

        val action = reporter.onTick("second", TimelineState.PLAYING, 0, duration)

        assertEquals(TimelineAction.SENT, action)
    }

    @Test
    fun aZeroDurationNeverScrobbles() = runTest {
        reporter.startItem("k")

        // A live or still-analysing item reports no duration. Dividing by it would either
        // crash or mark everything watched instantly.
        assertEquals(TimelineAction.SENT, reporter.onTick("k", TimelineState.PLAYING, 5_000, 0))
        assertTrue(sink.scrobbles.isEmpty())
    }
}

// --- CLAUDE.md section 2, markers and auto-play -----------------------------------------

class MarkerControllerTest {

    private val intro = Marker(MarkerType.INTRO, startMs = 12_000, endMs = 92_000)
    private val credits = Marker(MarkerType.CREDITS, startMs = 2_610_000, endMs = 2_712_000)
    private val duration = 2_712_000L

    private fun controller(hasNext: Boolean = true) =
        MarkerController(listOf(intro, credits), duration, hasNext)

    @Test
    fun theSkipButtonShowsOnlyWhileTheIntroIsActive() {
        val c = controller()

        assertFalse(c.showSkipIntro(11_999))
        assertTrue(c.showSkipIntro(12_000))
        assertTrue(c.showSkipIntro(91_999))
        assertFalse(c.showSkipIntro(92_000))
    }

    @Test
    fun skippingTheIntroJumpsToItsEnd() {
        assertEquals(92_000L, controller().skipIntroTargetMs())
    }

    @Test
    fun creditsSkipAutomaticallyWhenThereIsANextEpisode() {
        assertTrue(controller(hasNext = true).shouldAutoSkipCredits(2_620_000))
    }

    @Test
    fun theLastEpisodeOfAShowPlaysItsCreditsOut() {
        // Nothing to skip into, so skipping would just end playback early.
        assertFalse(controller(hasNext = false).shouldAutoSkipCredits(2_620_000))
    }

    @Test
    fun theNextEpisodePromptFollowsTheCreditsMarker() {
        val c = controller()

        assertFalse(c.showNextEpisodePrompt(2_609_000))
        assertTrue(c.showNextEpisodePrompt(2_610_000))
    }

    @Test
    fun withoutACreditsMarkerThePromptUsesTheFinalThirtySeconds() {
        val c = MarkerController(listOf(intro), duration, hasNextEpisode = true)

        assertFalse(c.showNextEpisodePrompt(duration - 31_000))
        assertTrue(c.showNextEpisodePrompt(duration - 30_000))
    }

    @Test
    fun anItemWithNoMarkersOffersNothing() {
        val c = MarkerController(emptyList(), duration, hasNextEpisode = false)

        assertFalse(c.showSkipIntro(1_000))
        assertNull(c.skipIntroTargetMs())
        assertFalse(c.showNextEpisodePrompt(duration - 1))
    }
}

class AutoPlayCountdownTest {

    private var now = 0L
    private val countdown = AutoPlayCountdown { now }

    @Test
    fun countsTenSecondsDown() {
        countdown.start()

        assertEquals(10, countdown.remainingSeconds())
        now = 4_000
        assertEquals(6, countdown.remainingSeconds())
        now = 10_000
        assertEquals(0, countdown.remainingSeconds())
        assertTrue(countdown.isElapsed())
    }

    @Test
    fun anyInputCancelsIt() {
        countdown.start()
        now = 3_000

        countdown.cancel()

        assertFalse(countdown.isRunning)
        assertFalse(countdown.isElapsed())
    }

    @Test
    fun cancellingIsOneWayForTheCurrentItem() {
        countdown.start()
        countdown.cancel()

        countdown.start()

        // Re-arming would make the countdown reappear every time the viewer moved the mouse.
        assertFalse(countdown.isRunning)
    }

    @Test
    fun theNextItemStartsFresh() {
        countdown.start()
        countdown.cancel()

        countdown.reset()
        countdown.start()

        assertTrue(countdown.isRunning)
    }
}

// --- CLAUDE.md section 10, fallback chain -----------------------------------------------

class PlaybackFallbackChainTest {

    @Test
    fun androidPhoneRetriesThroughTheSecondEngineBeforeAnyTranscode() {
        val chain = PlaybackFallbackChain(hasSecondaryEngine = true)

        assertEquals(
            PlaybackFallbackChain.Attempt.DIRECT_SECONDARY,
            chain.next(PlaybackFailure.DECODER_INITIALISATION),
        )
        assertEquals(
            PlaybackFallbackChain.Attempt.TRANSCODE,
            chain.next(PlaybackFailure.DECODER_INITIALISATION),
        )
        assertNull(chain.next(PlaybackFailure.DECODER_INITIALISATION))
    }

    @Test
    fun windowsGoesStraightToTranscode() {
        val chain = PlaybackFallbackChain(hasSecondaryEngine = false)

        assertEquals(
            PlaybackFallbackChain.Attempt.TRANSCODE,
            chain.next(PlaybackFailure.UNSUPPORTED_TRACK),
        )
    }

    @Test
    fun noFirstFrameWithinTheTimeoutCountsAsAFailure() {
        assertTrue(PlaybackFailure.NO_FIRST_FRAME.warrantsTranscode)
        assertEquals(8_000L, FIRST_FRAME_TIMEOUT_MS)
    }

    @Test
    fun aNetworkFailureEndsTheChain() {
        val chain = PlaybackFallbackChain(hasSecondaryEngine = true)

        // A transcode replaces the decoder's problem with the server's. It cannot fix a
        // connection that is down, and trying burns the server's transcoder for nothing.
        assertNull(chain.next(PlaybackFailure.NETWORK))
        assertNull(chain.next(PlaybackFailure.SOURCE_NOT_FOUND))
    }
}

class PlaybackCapabilitiesTest {

    @Test
    fun theSection10ListsAreSentVerbatim() {
        assertEquals(
            listOf("mkv", "mp4", "mov", "avi", "ts", "m2ts", "webm"),
            PlaybackCapabilities.containers,
        )
        assertEquals(
            listOf("h264", "hevc", "av1", "vp9", "mpeg2video", "vc1"),
            PlaybackCapabilities.video,
        )
        assertEquals(
            listOf("srt", "ass", "ssa", "pgs", "vobsub", "dvb_subtitle"),
            PlaybackCapabilities.subtitles,
        )
    }

    @Test
    fun withoutPassthroughTheBitstreamOnlyFormatsAreDropped() {
        val narrowed = PlaybackCapabilities.audioFor(supportsPassthrough = false)

        // Claiming a format the device cannot bitstream makes the server send it untouched
        // and the device then fails to decode it.
        assertFalse("truehd" in narrowed)
        assertFalse("dts" in narrowed)
        assertFalse("eac3" in narrowed)
        assertFalse("ac3" in narrowed)
        assertTrue("aac" in narrowed)
        assertTrue("flac" in narrowed)
    }

    @Test
    fun withPassthroughTheFullListIsSent() {
        assertEquals(PlaybackCapabilities.audio, PlaybackCapabilities.audioFor(true))
    }
}
