package com.thotapalli.plex.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.thotapalli.plex.ui.design.resources.Res
import com.thotapalli.plex.ui.design.resources.inter_bold
import com.thotapalli.plex.ui.design.resources.inter_medium
import com.thotapalli.plex.ui.design.resources.inter_regular
import com.thotapalli.plex.ui.design.resources.inter_semibold
import com.thotapalli.plex.ui.design.resources.sora_bold
import com.thotapalli.plex.ui.design.resources.sora_semibold
import org.jetbrains.compose.resources.Font

/**
 * The bundled typefaces. Both are TrueType files shipped inside the application under
 * src/commonMain/composeResources/font, so the interface renders identically offline and on
 * every target rather than borrowing whatever the platform happens to install.
 *
 * Inter carries the body and interface text: a neutral, high legibility grotesque, and the same
 * face the wordmark is drawn in (CLAUDE.md section 15). Sora carries the display and title text:
 * a geometric companion with more character in the large sizes where a headline anchors the page.
 *
 * Both are licensed under the SIL Open Font License 1.1, which permits bundling and redistribution.
 *
 * The families are built through the composable resource [Font] loader, so these are composable
 * accessors rather than top level values.
 */

/** Inter, the body and interface face. Weights map to the section 12 scale. */
@Composable
fun interFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

/** Sora, the display and title face. Only the heavy weights are carried; large text is never light. */
@Composable
fun soraFamily(): FontFamily = FontFamily(
    Font(Res.font.sora_semibold, FontWeight.SemiBold),
    Font(Res.font.sora_bold, FontWeight.Bold),
)
