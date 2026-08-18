package com.thotapalli.plex.ui.shared

import com.thotapalli.plex.ui.shared.input.BackTarget
import com.thotapalli.plex.ui.shared.input.HeldSeek
import com.thotapalli.plex.ui.shared.input.resolveBack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CLAUDE.md section 13, television input. */
class BackNavigationTest {

    @Test
    fun backNeverExitsFromBelowTheHomeScreen() {
        // Every level below home resolves to something other than exiting.
        val belowHome = listOf(
            resolveBack(true, false, false, false, false, true),
            resolveBack(false, true, false, false, false, true),
            resolveBack(false, false, true, false, false, true),
            resolveBack(false, false, false, true, false, true),
            resolveBack(false, false, false, false, true, true),
            resolveBack(false, false, false, false, false, false),
        )

        assertTrue(belowHome.none { it == BackTarget.EXIT_APPLICATION })
    }

    @Test
    fun onlyTheHomeScreenItselfMayExit() {
        assertEquals(
            BackTarget.EXIT_APPLICATION,
            resolveBack(
                overlayOpen = false,
                inPlayer = false,
                detailOpen = false,
                collectionOpen = false,
                libraryOpen = false,
                atHomeTab = true,
            ),
        )
    }

    @Test
    fun backMovesUpExactlyOneLevelAtATime() {
        // A detail screen opened from inside a collection goes back to the collection, not
        // all the way out to the library.
        assertEquals(
            BackTarget.CLOSE_DETAIL,
            resolveBack(false, false, detailOpen = true, collectionOpen = true, libraryOpen = true, atHomeTab = true),
        )
        assertEquals(
            BackTarget.CLOSE_COLLECTION,
            resolveBack(false, false, detailOpen = false, collectionOpen = true, libraryOpen = true, atHomeTab = true),
        )
        assertEquals(
            BackTarget.CLOSE_LIBRARY,
            resolveBack(false, false, detailOpen = false, collectionOpen = false, libraryOpen = true, atHomeTab = true),
        )
    }

    @Test
    fun anOpenOverlayIsDismissedBeforeAnythingElse() {
        assertEquals(
            BackTarget.DISMISS_OVERLAY,
            resolveBack(overlayOpen = true, inPlayer = true, detailOpen = true, collectionOpen = true, libraryOpen = true, atHomeTab = false),
        )
    }

    @Test
    fun aNonHomeTabReturnsToHomeRatherThanExiting() {
        assertEquals(
            BackTarget.GO_HOME,
            resolveBack(false, false, false, false, false, atHomeTab = false),
        )
    }
}

class HeldSeekTest {

    private var now = 0L
    private val seek = HeldSeek { now }

    @Test
    fun aShortPressIsNotAHold() {
        seek.onKeyDown(forward = true)
        now = 200

        // Below the threshold this stays an ordinary single seek rather than a scrub.
        assertEquals(0L, seek.stepMs())
    }

    @Test
    fun holdingSeeksThirtySecondsEveryFourHundredMilliseconds() {
        seek.onKeyDown(forward = true)

        now = 400
        assertEquals(30_000L, seek.stepMs())

        now = 600
        assertEquals(0L, seek.stepMs(), "inside the interval")

        now = 800
        assertEquals(30_000L, seek.stepMs())
    }

    @Test
    fun holdingLeftSeeksBackwards() {
        seek.onKeyDown(forward = false)
        now = 400

        assertEquals(-30_000L, seek.stepMs())
    }

    @Test
    fun releasingStopsTheSeek() {
        seek.onKeyDown(forward = true)
        now = 400
        seek.stepMs()

        seek.onKeyUp()
        now = 1_200

        assertFalse(seek.isHeld)
        assertEquals(0L, seek.stepMs())
    }

    @Test
    fun aSecondKeyDownWhileHeldDoesNotRestartTheTimer() {
        seek.onKeyDown(forward = true)
        now = 300
        // Key repeat from the remote fires again; the hold must keep its original start so
        // the speed does not depend on the remote's repeat rate.
        seek.onKeyDown(forward = true)

        now = 400
        assertEquals(30_000L, seek.stepMs())
    }
}

class FormattingTest {

    @Test
    fun durationsReadAsHoursAndMinutes() {
        assertEquals("2h 1m", formatDuration(7_260_000))
        assertEquals("45m", formatDuration(2_700_000))
        assertEquals("2h", formatDuration(7_200_000))
        assertEquals("", formatDuration(0))
    }

    @Test
    fun positionsReadAsAClock() {
        assertEquals("1:47:12", formatPosition(6_432_000))
        assertEquals("42:07", formatPosition(2_527_000))
        assertEquals("0:00", formatPosition(0))
        assertEquals("0:00", formatPosition(-500))
    }
}
