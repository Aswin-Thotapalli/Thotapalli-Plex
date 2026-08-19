package com.thotapalli.plex.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale from CLAUDE.md section 12. Four, eight, twelve, sixteen, twenty four,
 * thirty two, forty eight. No other value.
 *
 * Every dimension in this codebase is in dp. There is no raw pixel value anywhere.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

/** The corner radii from CLAUDE.md section 12. */
object Radius {
    /** Poster tiles. */
    val poster = RoundedCornerShape(8.dp)

    /** Cards and dialogues. */
    val card = RoundedCornerShape(12.dp)

    /** Sheets. */
    val sheet = RoundedCornerShape(16.dp)

    /** Pills. */
    val pill = RoundedCornerShape(999.dp)

    val posterDp: Dp = 8.dp
    val cardDp: Dp = 12.dp
    val sheetDp: Dp = 16.dp
}

/**
 * The elevation scale. A shadow is the only depth cue that survives against arbitrary poster
 * artwork, so raised elements read as physical cards by how far they lift off the ground rather
 * than by a change of colour. One named step per role keeps the wall coherent: every poster
 * lifts by the same amount, every card by the same, and nothing picks a number of its own.
 */
object Elevation {
    /** A poster tile in the grid. */
    val tile: Dp = 10.dp

    /** A wide 16:9 continue-watching tile, lifted a touch more than a poster. */
    val wide: Dp = 14.dp

    /** An episode thumbnail or a library card. */
    val card: Dp = 6.dp

    /** A filled primary button, so the one action on a screen sits proud of the surface. */
    val button: Dp = 8.dp

    /** A control floating over a backdrop, and the raised state of a selected row. */
    val floating: Dp = 12.dp
}

/**
 * Layout sizes that follow from section 13.
 */
@Immutable
object Layout {
    /**
     * Beyond this the grid adds columns and the cap keeps content centred. Posters never
     * stretch. See CLAUDE.md section 13.
     */
    val maxContentWidth: Dp = 1600.dp

    /** The fixed left pane of a two pane detail screen. */
    val detailPaneWidth: Dp = 380.dp

    /** A poster is 2:3, which is what Plex stores. */
    const val POSTER_ASPECT_RATIO = 2f / 3f

    /** A wide progress tile is 16:9. */
    const val WIDE_ASPECT_RATIO = 16f / 9f

    val gridHorizontalGap: Dp = 12.dp
    val gridVerticalGap: Dp = 20.dp

    /** Every television screen carries a five percent overscan margin on all four edges. */
    const val TELEVISION_OVERSCAN_FRACTION = 0.05f

    /** Television focus grows the tile by this much. See CLAUDE.md section 12. */
    const val TELEVISION_FOCUS_SCALE = 1.08f

    val focusRingWidth: Dp = 3.dp

    /** A floating icon control (the back button). Large enough to be an obvious target. */
    val iconButton: Dp = 44.dp
    val iconButtonTelevision: Dp = 52.dp

    /** The circular play control laid over an episode thumbnail. */
    val playToken: Dp = 44.dp

    /** A library card on the Home screen. Taller than a list row, so it reads as a place. */
    val libraryCardHeight: Dp = 108.dp
}
