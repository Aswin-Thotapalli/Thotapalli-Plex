package com.thotapalli.plex.ui.shared

/**
 * True on the Windows desktop (JVM) target. Used to switch off continuous decorative animations —
 * the endless ken-burns pan and the animated film grain — which force a full-window Skia redraw
 * every frame and make the desktop UI stutter even while idle. The effects fall back to a single
 * static frame there, which costs nothing after the first draw. See CLAUDE.md sections 9 and 12.
 */
expect fun isDesktopPlatform(): Boolean
