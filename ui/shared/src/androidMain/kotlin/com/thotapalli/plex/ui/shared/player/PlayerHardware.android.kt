package com.thotapalli.plex.ui.shared.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Android brightness and volume.
 *
 * Brightness rides the current window's [android.view.WindowManager.LayoutParams.screenBrightness]
 * (0f..1f), which is a per-window override — leaving the player restores the system brightness with
 * no cleanup. The Activity is unwrapped from the composition's view context. Volume is a one-step
 * adjust of the music stream with the platform's own volume UI, so the viewer gets the familiar
 * slider. See CLAUDE.md section 18.
 */
@Composable
actual fun rememberPlayerHardware(): PlayerHardware {
    val view = LocalView.current
    return remember(view) {
        val activity = view.context.findActivity()
        val audio = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        AndroidPlayerHardware(activity, audio)
    }
}

/** Walk the context-wrapper chain to the hosting Activity, or null if there is none. */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private class AndroidPlayerHardware(
    private val activity: Activity?,
    private val audio: AudioManager?,
) : PlayerHardware {

    override val supportsBrightness: Boolean get() = activity != null

    override fun setBrightness(fraction: Float) {
        val window = activity?.window ?: return
        // Gesture callbacks run on the main thread, so mutating window attributes here is safe.
        window.attributes = window.attributes.apply {
            screenBrightness = fraction.coerceIn(0f, 1f)
        }
    }

    override fun nudgeVolume(up: Boolean) {
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }
}
