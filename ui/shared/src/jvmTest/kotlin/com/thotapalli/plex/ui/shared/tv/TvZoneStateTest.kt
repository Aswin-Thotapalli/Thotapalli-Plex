package com.thotapalli.plex.ui.shared.tv

import androidx.compose.ui.focus.FocusRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The memory behind every television screen: where focus lands when a zone is re-entered.
 * These pin the contract in TvFocus.kt without a device — the part a remote depends on most.
 */
class TvZoneStateTest {

    @Test
    fun aFreshZoneEntersAtItsFirstRegisteredItem() {
        val zone = TvZoneState(parent = null).also { it.attached = true }
        val first = FocusRequester()
        val second = FocusRequester()
        zone.register("a", first)
        zone.register("b", second)

        assertSame(first, zone.restoreTarget())
    }

    @Test
    fun reEnteringLandsOnTheLastFocusedItem() {
        val zone = TvZoneState(parent = null).also { it.attached = true }
        val first = FocusRequester()
        val second = FocusRequester()
        zone.register("a", first)
        zone.register("b", second)

        zone.noteFocused("b")

        assertSame(second, zone.restoreTarget())
    }

    @Test
    fun anItemThatScrolledOutOfCompositionFallsBackToWhatIsThere() {
        val zone = TvZoneState(parent = null).also { it.attached = true }
        val first = FocusRequester()
        val second = FocusRequester()
        zone.register("a", first)
        zone.register("b", second)
        zone.noteFocused("b")

        // A lazy row recycled the card; it unregisters and is no longer a live node.
        zone.unregister("b", second)

        assertSame(first, zone.restoreTarget())
    }

    @Test
    fun aParentRemembersWhichChildZoneWasLast() {
        val content = TvZoneState(parent = null).also { it.attached = true }
        val row1 = TvZoneState(parent = content).also { it.attached = true }
        val row2 = TvZoneState(parent = content).also { it.attached = true }
        val hero = FocusRequester()
        content.register("hero-play", hero)
        row2.register("card-9", FocusRequester())

        row2.noteFocused("card-9")

        assertSame(row2.entry, content.restoreTarget())
        assertSame(row1.entry !== content.restoreTarget(), true)
    }

    @Test
    fun aChildZoneThatLeftCompositionIsNeverHandedToFocusSearch() {
        val content = TvZoneState(parent = null).also { it.attached = true }
        val screen = TvZoneState(parent = content).also { it.attached = true }
        screen.register("x", FocusRequester())
        screen.noteFocused("x")
        assertSame(screen.entry, content.restoreTarget())

        // The screen was navigated away from: asking for its entry would throw inside focus search.
        screen.attached = false

        assertEquals(FocusRequester.Default, content.restoreTarget())
    }

    @Test
    fun resetForgetsThePositionButNotTheItems() {
        val zone = TvZoneState(parent = null).also { it.attached = true }
        val first = FocusRequester()
        val second = FocusRequester()
        zone.register("a", first)
        zone.register("b", second)
        zone.noteFocused("b")

        zone.reset()

        assertSame(first, zone.restoreTarget())
    }

    @Test
    fun unregisteringAStaleRequesterDoesNotDropAReplacement() {
        val zone = TvZoneState(parent = null).also { it.attached = true }
        val old = FocusRequester()
        val replacement = FocusRequester()
        zone.register("a", old)
        // The item recomposed with a new requester before the old one's dispose ran.
        zone.register("a", replacement)
        zone.unregister("a", old)

        assertSame(replacement, zone.target("a"))
    }

    @Test
    fun requestFocusOnAnUnattachedZoneIsAQuietNo() {
        val zone = TvZoneState(parent = null)
        assertEquals(false, zone.requestFocus())
    }
}
