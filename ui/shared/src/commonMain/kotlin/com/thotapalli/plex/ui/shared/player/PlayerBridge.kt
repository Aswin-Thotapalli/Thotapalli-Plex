package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shares the player's state and actions between two windows on the desktop.
 *
 * The desktop video is a heavyweight native window that Compose cannot paint over, so the
 * controls are drawn in a separate transparent window floating above the picture. That
 * window lives in a different composition from the one that owns the [PlaybackController],
 * so the two cannot share `remember`ed state directly — they meet here instead.
 *
 * On Android this is unused: the overlay composites over the SurfaceView in one window.
 */
class PlayerBridge {
    /** The player state the floating overlay renders. Set by the video window. */
    var stateFlow: StateFlow<PlayerScreenState> by mutableStateOf(MutableStateFlow(PlayerScreenState()))

    /** The actions the floating overlay drives. Set by the video window. */
    var actions: PlayerActions by mutableStateOf(PlayerActions())

    /** Pointer movement over the overlay reveals the controls, the way a tap does. */
    var onActivity: () -> Unit by mutableStateOf({})

    /** True while a video is on screen, so the overlay window is shown only then. */
    var active: Boolean by mutableStateOf(false)

    fun publish(state: StateFlow<PlayerScreenState>, actions: PlayerActions, onActivity: () -> Unit) {
        this.stateFlow = state
        this.actions = actions
        this.onActivity = onActivity
        this.active = true
    }

    fun clear() {
        active = false
        stateFlow = MutableStateFlow(PlayerScreenState())
        actions = PlayerActions()
        onActivity = {}
    }
}
