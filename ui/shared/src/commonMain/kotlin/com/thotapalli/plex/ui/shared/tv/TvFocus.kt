package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.thotapalli.plex.ui.shared.motion.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The television focus contract. Every TV screen is built on exactly this, and nothing else.
 *
 * THE ONE RULE: no composable in the TV tree consumes a D-pad direction key. Up, down, left and
 * right belong to Compose's focus search. Where geometry would make it guess — leaving a content
 * area for the nav rail, entering a row that was scrolled — the answer is declared with focus
 * properties on a [tvZone], never by intercepting the key. The single exception is the player's
 * idle surface, which owns the remote only while the controls are hidden (see TvPlayer).
 *
 * That rule is what makes every element on screen reachable: if a control is drawn and it is
 * [tvInteractive], the remote can get to it, because nothing between it and the focused node is
 * swallowing the key that would take you there.
 *
 * The rest of the contract:
 *  - A [tvInteractive] node is the only kind of target. Select (centre / enter) activates it on
 *    key-up, a held select long-presses it. Nothing uses `clickable` on TV.
 *  - A [tvZone] is a focus group that remembers the last child the viewer was on and puts focus
 *    back there when the zone is re-entered — from the rail, from a detail, from another row.
 *    Zones nest: the content zone remembers which row you were in, the row remembers which card.
 *  - Every screen requests first focus on entry ([TvFirstFocus]), and [TvFocusGuard] re-seats focus
 *    if it is ever lost, so a television never has nothing focused. See CLAUDE.md section 13.
 */

// --- zones ----------------------------------------------------------------------------------

/**
 * The memory of one focus group. Items register their key while composed; the zone records the
 * last one focused (or the last child zone) and hands it back on re-entry.
 */
@Stable
class TvZoneState internal constructor(internal val parent: TvZoneState?) {
    /** Request focus on this to enter the zone at its remembered position. */
    val entry: FocusRequester = FocusRequester()

    private val requesters = LinkedHashMap<Any, FocusRequester>()

    /** The last thing focused directly in this zone: an item key, or a nested [TvZoneState]. */
    private var last: Any? by mutableStateOf(null)

    /**
     * True while the zone's layout node is composed. A zone that has left composition (a screen the
     * viewer navigated away from) must never be handed to focus search: its requester has no node
     * and asking it for focus throws. Registered items track this for themselves by unregistering.
     */
    internal var attached: Boolean = false

    internal fun register(key: Any, requester: FocusRequester) {
        requesters[key] = requester
    }

    internal fun unregister(key: Any, requester: FocusRequester) {
        if (requesters[key] === requester) requesters.remove(key)
    }

    internal fun noteFocused(key: Any) {
        last = key
        parent?.noteChildZone(this)
    }

    private fun noteChildZone(zone: TvZoneState) {
        last = zone
        parent?.noteChildZone(this)
    }

    /** The requester of a registered item, if it is currently composed. */
    fun target(key: Any): FocusRequester? = requesters[key]

    /** Where focus should land when this zone is entered. Only ever a live node, or Default. */
    internal fun restoreTarget(): FocusRequester {
        val remembered = last
        if (remembered is TvZoneState) {
            if (remembered.attached) return remembered.entry
        } else if (remembered != null) {
            requesters[remembered]?.let { return it }
        }
        return requesters.values.firstOrNull() ?: FocusRequester.Default
    }

    /** Forget the remembered position, so the next entry lands on the first item. */
    fun reset() {
        last = null
    }

    /** Enter the zone at its remembered position. Safe to call before the zone is composed. */
    fun requestFocus(): Boolean = attached && runCatching { entry.requestFocus() }.isSuccess
}

/** The zone the current composable's items register with, if any. */
internal val LocalTvZone = compositionLocalOf<TvZoneState?> { null }

/**
 * Zones that must outlive their screen. Kept at the shell, so a screen the viewer leaves and comes
 * back to (Home after Settings, a library after a detail) still remembers the card they were on.
 * Keyed by a stable string per screen or row.
 */
internal class TvZoneRegistry {
    private val zones = HashMap<String, TvZoneState>()
    fun getOrCreate(key: String, parent: TvZoneState?): TvZoneState =
        zones.getOrPut(key) { TvZoneState(parent) }
}

internal val LocalTvZoneRegistry = compositionLocalOf<TvZoneRegistry?> { null }

/** A zone nested under whatever zone encloses the call site, forgotten when the call site leaves. */
@Composable
fun rememberTvZone(): TvZoneState {
    val parent = LocalTvZone.current
    return remember(parent) { TvZoneState(parent) }
}

/**
 * A zone with a persistent memory: the same [key] hands back the same zone for the life of the
 * shell, so re-entering a screen restores the exact item the viewer left from.
 */
@Composable
fun rememberTvZone(key: String): TvZoneState {
    val parent = LocalTvZone.current
    val registry = LocalTvZoneRegistry.current
    return remember(key, parent, registry) {
        registry?.getOrCreate(key, parent) ?: TvZoneState(parent)
    }
}

/**
 * Makes this node a focus group with memory. Requesting focus on [zone] enters at the remembered
 * child; leaving in a direction with a declared neighbour goes exactly there, otherwise focus
 * search proceeds as normal. Must wrap the content in [TvZone] so items can register.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvZone(
    zone: TvZoneState,
    exitLeft: (() -> FocusRequester?)? = null,
    exitRight: (() -> FocusRequester?)? = null,
    exitUp: (() -> FocusRequester?)? = null,
    exitDown: (() -> FocusRequester?)? = null,
    /**
     * A trapped zone never lets focus search leave it: a dialog, the player's countdown card or
     * track picker. Without this, DOWN past the last row of a dialog would land on the screen
     * behind the scrim.
     */
    trap: Boolean = false,
): Modifier = this
    .focusRequester(zone.entry)
    .focusProperties {
        enter = { zone.restoreTarget() }
        exit = { direction ->
            val target = when (direction) {
                FocusDirection.Left -> exitLeft?.invoke()
                FocusDirection.Right -> exitRight?.invoke()
                FocusDirection.Up -> exitUp?.invoke()
                FocusDirection.Down -> exitDown?.invoke()
                else -> null
            }
            target ?: if (trap) FocusRequester.Cancel else FocusRequester.Default
        }
    }
    .focusGroup()

/** Scopes [content]'s items to [zone] and marks the zone live while composed. Pair with [tvZone]. */
@Composable
fun TvZone(zone: TvZoneState, content: @Composable () -> Unit) {
    DisposableEffect(zone) {
        zone.attached = true
        onDispose { zone.attached = false }
    }
    CompositionLocalProvider(LocalTvZone provides zone, content = content)
}

// --- targets --------------------------------------------------------------------------------

/**
 * A D-pad target. Focusable, activated by select on key-up, long-pressed by a held select, and
 * registered with the enclosing [TvZone] under [key] so the zone can put focus back on it later.
 *
 * Direction keys are deliberately untouched — see the file comment. Press state is mirrored into
 * [interaction] so the visual can squash while select is held.
 */
fun Modifier.tvInteractive(
    interaction: MutableInteractionSource,
    key: Any,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    /** Called when this node gains focus — rails use it to feature the card, seasons to select. */
    onFocused: (() -> Unit)? = null,
): Modifier = composed {
    val zone = LocalTvZone.current
    val requester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var pressed by remember { mutableStateOf<PressInteraction.Press?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var longJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(zone, key) {
        zone?.register(key, requester)
        onDispose { zone?.unregister(key, requester) }
    }

    this
        .focusRequester(requester)
        .onFocusChanged { state ->
            if (state.isFocused) {
                zone?.noteFocused(key)
                onFocused?.invoke()
            }
        }
        .onKeyEvent { event ->
            if (!enabled || !event.isSelect()) return@onKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    // The first down of a hold; the key repeats while held and those are ignored.
                    if (pressed == null) {
                        val press = PressInteraction.Press(Offset.Zero)
                        pressed = press
                        longFired = false
                        scope.launch { interaction.emit(press) }
                        if (onLongClick != null) {
                            longJob = scope.launch {
                                delay(LONG_PRESS_MS)
                                longFired = true
                                haptics.press()
                                onLongClick()
                            }
                        }
                    }
                    true
                }

                KeyEventType.KeyUp -> {
                    longJob?.cancel()
                    longJob = null
                    val press = pressed
                    pressed = null
                    if (press != null) {
                        scope.launch { interaction.emit(PressInteraction.Release(press)) }
                        if (!longFired) {
                            haptics.press()
                            onClick()
                        }
                    }
                    true
                }

                else -> false
            }
        }
        .focusable(enabled = enabled, interactionSource = interaction)
}

private fun KeyEvent.isSelect(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

private const val LONG_PRESS_MS = 550L

// --- first focus and the guard --------------------------------------------------------------

/**
 * Puts focus into [zone] when the screen appears and whenever [key] changes — a new destination,
 * a detail opening, a list finishing its load. No screen opens with nothing focused.
 */
@Composable
fun TvFirstFocus(zone: TvZoneState, key: Any? = Unit, enabled: Boolean = true) {
    LaunchedEffect(zone, key, enabled) {
        if (!enabled) return@LaunchedEffect
        // Let the frame that composed the target land before asking for it.
        delay(FIRST_FOCUS_DELAY_MS)
        zone.requestFocus()
    }
}

/**
 * The safety net: if nothing under this node holds focus for a moment — a focused item was
 * removed, a list re-keyed, a dialog closed — focus is re-seated in [fallback]. A remote with no
 * focus has no way to get any, so this is what stops every dead end.
 */
@Composable
fun Modifier.tvFocusGuard(fallback: TvZoneState, enabled: Boolean = true): Modifier {
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus, enabled) {
        if (!enabled || hasFocus) return@LaunchedEffect
        delay(GUARD_DELAY_MS)
        fallback.requestFocus()
    }
    return this.onFocusChanged { hasFocus = it.hasFocus }
}

private const val FIRST_FOCUS_DELAY_MS = 48L
private const val GUARD_DELAY_MS = 260L
