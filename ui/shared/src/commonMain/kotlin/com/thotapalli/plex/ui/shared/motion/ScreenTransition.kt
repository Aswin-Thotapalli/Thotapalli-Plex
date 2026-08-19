package com.thotapalli.plex.ui.shared.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thotapalli.plex.ui.design.Motion

/**
 * A tasteful cross-fade between screen states: the arriving state fades and scales up from a hair
 * under full size while rising a touch, and the leaving state fades out quickly and settles back.
 *
 * Built on [AnimatedContent] so the two states are alive together for the length of the change,
 * which lets the fade actually cross rather than blink. Timings come from CLAUDE.md section 12: the
 * enter rides [Motion.screen] (220 ms standard) so the eye can follow the change; the exit rides
 * [Motion.exit] (100 ms accelerate) so the old screen clears out of the way. The size transform is
 * left unclipped so the subtle slide and scale are never cropped at the container edge.
 *
 * Use it to move between top-level places — Home, Library, Detail — where a hard cut would feel
 * abrupt. [target] is the current state; [content] renders whichever state is being shown.
 */
@Composable
fun <T> AnimatedContentCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(animationSpec = Motion.screen()) +
                scaleIn(initialScale = 0.98f, animationSpec = Motion.screen()) +
                slideInVertically(animationSpec = Motion.screen()) { height -> height / 24 }
            val exit = fadeOut(animationSpec = Motion.exit()) +
                scaleOut(targetScale = 1.01f, animationSpec = Motion.exit())
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "screen-crossfade",
    ) { state ->
        content(state)
    }
}
