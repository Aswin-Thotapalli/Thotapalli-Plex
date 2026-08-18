package com.thotapalli.plex.ui.shared.input

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.thotapalli.plex.ui.design.Layout
import com.thotapalli.plex.ui.design.SizeClass

/**
 * Television input, from CLAUDE.md section 13.
 *
 * 1. Every focusable element carries an explicit focus order.
 * 2. Overscan margin five percent on all four edges of every television screen.
 * 3. First focus requested on entry to every screen; no screen opens with nothing focused.
 * 4. Back moves up one level and never exits the application from below the home screen.
 * 5. Long press on the directional pad seeks at thirty seconds per 400 ms while held.
 */

/**
 * Requests focus once when a screen appears.
 *
 * No screen opens with nothing focused, because a television with no focus has no way to
 * get any: there is no pointer to click with. See CLAUDE.md section 13.
 */
@Composable
fun rememberFirstFocus(enabled: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) runCatching { requester.requestFocus() }
    }
    return requester
}

/**
 * An explicit focus order.
 *
 * Compose's spatial search picks whatever is geometrically nearest, which on a grid with a
 * short last row means pressing down from the end of one row can land somewhere
 * unexpected. Naming the neighbours removes the guess.
 */
fun Modifier.focusOrder(
    self: FocusRequester,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Modifier = this
    .focusRequester(self)
    .focusProperties {
        up?.let { this.up = it }
        down?.let { this.down = it }
        left?.let { this.left = it }
        right?.let { this.right = it }
    }

/** Five percent on all four edges of every television screen. */
fun overscanFor(sizeClass: SizeClass, widthDp: Float, heightDp: Float): PaddingValues =
    if (!sizeClass.isTelevision) {
        PaddingValues(sizeClass.screenPadding)
    } else {
        PaddingValues(
            horizontal = androidx.compose.ui.unit.Dp(widthDp * Layout.TELEVISION_OVERSCAN_FRACTION),
            vertical = androidx.compose.ui.unit.Dp(heightDp * Layout.TELEVISION_OVERSCAN_FRACTION),
        )
    }

/**
 * Long press on the directional pad seeks at thirty seconds per 400 ms while held.
 *
 * Held rather than repeated: the key repeat rate differs between remotes, so deriving the
 * seek from elapsed time makes the speed the same on every device.
 */
class HeldSeek(private val nowMs: () -> Long) {

    private var heldSince: Long? = null
    private var lastStepAtMs: Long = 0
    private var direction: Int = 0

    val isHeld: Boolean get() = heldSince != null

    fun onKeyDown(forward: Boolean) {
        if (heldSince == null) {
            heldSince = nowMs()
            lastStepAtMs = nowMs()
            direction = if (forward) 1 else -1
        }
    }

    fun onKeyUp() {
        heldSince = null
        direction = 0
    }

    /**
     * The seek to apply now, or zero.
     *
     * Returns nothing until the key has been held past [LONG_PRESS_THRESHOLD_MS], so a
     * short press stays an ordinary single seek rather than becoming a scrub.
     */
    fun stepMs(): Long {
        val since = heldSince ?: return 0
        if (nowMs() - since < LONG_PRESS_THRESHOLD_MS) return 0
        if (nowMs() - lastStepAtMs < STEP_INTERVAL_MS) return 0

        lastStepAtMs = nowMs()
        return STEP_MS * direction
    }

    companion object {
        const val STEP_MS = 30_000L
        const val STEP_INTERVAL_MS = 400L

        /** Below this it is a press, not a hold. */
        const val LONG_PRESS_THRESHOLD_MS = 400L
    }
}

/**
 * Where a back press goes.
 *
 * Back moves up one level and never exits the application from below the home screen.
 * See CLAUDE.md section 13.
 */
enum class BackTarget {
    /** Dismiss an open sheet or overlay. */
    DISMISS_OVERLAY,

    /** Leave the player. */
    EXIT_PLAYER,

    /** Detail back to the list it came from. */
    CLOSE_DETAIL,

    /** Inside a collection back to the whole library. */
    CLOSE_COLLECTION,

    /** Library back to home. */
    CLOSE_LIBRARY,

    /** Any other tab back to home. */
    GO_HOME,

    /** Already at home. Only here may the application exit. */
    EXIT_APPLICATION,
}

/**
 * Resolves a back press to exactly one action.
 *
 * Written as a pure function so the rule is testable, and so phone, television and Windows
 * cannot disagree about what back means.
 */
fun resolveBack(
    overlayOpen: Boolean,
    inPlayer: Boolean,
    detailOpen: Boolean,
    collectionOpen: Boolean,
    libraryOpen: Boolean,
    atHomeTab: Boolean,
): BackTarget = when {
    overlayOpen -> BackTarget.DISMISS_OVERLAY
    inPlayer -> BackTarget.EXIT_PLAYER
    detailOpen -> BackTarget.CLOSE_DETAIL
    collectionOpen -> BackTarget.CLOSE_COLLECTION
    libraryOpen -> BackTarget.CLOSE_LIBRARY
    !atHomeTab -> BackTarget.GO_HOME
    else -> BackTarget.EXIT_APPLICATION
}

/**
 * Keyboard shortcuts for Windows, so every screen is operable with the keyboard alone.
 * See CLAUDE.md section 16 phase 7 step 2.
 */
fun keyToPlayerAction(event: KeyEvent): PlayerKeyAction? {
    if (event.type != KeyEventType.KeyDown) return null

    return when (event.key) {
        Key.Spacebar, Key.MediaPlayPause, Key.Enter, Key.NumPadEnter -> PlayerKeyAction.PLAY_PAUSE
        Key.DirectionLeft -> PlayerKeyAction.SEEK_BACK
        Key.DirectionRight -> PlayerKeyAction.SEEK_FORWARD
        Key.F, Key.F11 -> PlayerKeyAction.TOGGLE_FULL_SCREEN
        Key.Escape, Key.Back -> PlayerKeyAction.BACK
        Key.S -> PlayerKeyAction.CYCLE_SUBTITLES
        Key.A -> PlayerKeyAction.CYCLE_AUDIO
        else -> null
    }
}

enum class PlayerKeyAction {
    PLAY_PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    TOGGLE_FULL_SCREEN,
    BACK,
    CYCLE_SUBTITLES,
    CYCLE_AUDIO,
}
