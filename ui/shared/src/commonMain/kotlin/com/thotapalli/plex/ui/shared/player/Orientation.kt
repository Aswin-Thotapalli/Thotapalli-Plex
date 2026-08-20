package com.thotapalli.plex.ui.shared.player

import androidx.compose.runtime.Composable

/**
 * While this is in the composition the device is held in landscape; when it leaves — the player
 * closes, or the app returns to Home mid-playback — the orientation goes back to the system
 * default. A no-op on the desktop, where the window is not orientation-locked. See CLAUDE.md §14.
 */
@Composable
expect fun LandscapeWhilePlaying()
