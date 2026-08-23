package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout adapts to available space rather than to a list of known devices.
 * See CLAUDE.md section 13.
 *
 * Television is not a width. A television is identified by the platform, because a
 * 1920 by 1080 television and a 1920 by 1080 desktop window need entirely different type
 * scales and focus behaviour despite being the same size.
 */
enum class SizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    TELEVISION,
    ;

    /** Compact uses a bottom bar, medium and expanded a side rail, television a top row. */
    val navigation: NavigationStyle
        get() = when (this) {
            COMPACT -> NavigationStyle.BOTTOM_BAR
            MEDIUM, EXPANDED -> NavigationStyle.SIDE_RAIL
            TELEVISION -> NavigationStyle.TOP_ROW
        }

    /** Expanded and television show a detail screen in two panes. */
    val twoPaneDetail: Boolean get() = this == EXPANDED || this == TELEVISION

    val isTelevision: Boolean get() = this == TELEVISION

    /** The minimum poster width the adaptive grid is built from. */
    val posterMinWidth: Dp
        get() = when (this) {
            COMPACT -> 150.dp
            // Larger minimums than before so each poster is bigger (fewer, larger columns) on
            // tablet, desktop and TV. See CLAUDE.md section 13 (requests #4/#6/#7).
            MEDIUM -> 184.dp
            EXPANDED -> 220.dp
            TELEVISION -> 220.dp
        }

    val screenPadding: Dp
        get() = when (this) {
            COMPACT -> Spacing.md
            // Tighter screen edges on tablet/desktop so posters get more of the width (#6/#7).
            MEDIUM -> Spacing.md
            EXPANDED -> Spacing.lg
            // Television keeps the wider inset for the 5% overscan margin (§13).
            TELEVISION -> Spacing.xl
        }

    companion object {
        /**
         * On Windows the window drives the size class and is recomputed during the resize
         * drag, so this is called on every width change rather than once at start up.
         */
        fun fromWidth(width: Dp, isTelevision: Boolean = false): SizeClass = when {
            isTelevision -> TELEVISION
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

enum class NavigationStyle { BOTTOM_BAR, SIDE_RAIL, TOP_ROW }

@Immutable
data class WindowSize(
    val widthDp: Dp,
    val heightDp: Dp,
    val sizeClass: SizeClass,
)

val LocalSizeClass = staticCompositionLocalOf { SizeClass.COMPACT }
