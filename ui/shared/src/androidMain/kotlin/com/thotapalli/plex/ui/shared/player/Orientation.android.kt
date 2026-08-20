package com.thotapalli.plex.ui.shared.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Forces the hosting activity into landscape for as long as the player is on screen, then hands
 * orientation back to the system when the player leaves. Sensor landscape so the picture still
 * flips the right way up when the phone is turned the other way.
 */
@Composable
actual fun LandscapeWhilePlaying() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            // Back to the system default (which follows the user's rotation setting), so exiting
            // the player — or going Home mid-play — returns the phone to portrait/whatever it was.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
