package com.thotapalli.plex.ui.shared

import androidx.compose.runtime.Composable

/**
 * While [hidden] is true, hide the system bars (status + navigation) so the player is truly full
 * bleed; the viewer swipes from an edge to reveal them transiently (Android
 * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE). Restores the bars when [hidden] is false or when this
 * leaves composition.
 *
 * Android hides the framework bars; other platforms no-op — the desktop player has its own
 * full-screen path (§8 / the Windows full-screen toggle). See CLAUDE.md section 14 item 7.
 */
@Composable
expect fun ImmersiveSystemBars(hidden: Boolean)
