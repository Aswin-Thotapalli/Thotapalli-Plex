package com.thotapalli.plex.ui.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.shared.resources.Res
import com.thotapalli.plex.ui.shared.resources.brand_mark
import org.jetbrains.compose.resources.painterResource

/**
 * The Thotapalli Plex brand mark — the interlocked T/P monogram with the play triangle and
 * equalizer bars, drawn by the artwork in `brand/Files` and bundled as a Compose resource
 * (`commonMain/composeResources/drawable/brand_mark.png`). The same image is revealed by the
 * animated launch splash ([BrandSplash]).
 */
@Composable
fun AppLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.brand_mark),
        contentDescription = "Thotapalli Plex",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
