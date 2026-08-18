package com.thotapalli.plex.player.exo

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import com.thotapalli.plex.core.playback.DisplayMode
import com.thotapalli.plex.core.playback.DisplayModeController
import com.thotapalli.plex.core.playback.RefreshRate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Refresh rate matching on Android, from CLAUDE.md section 9.
 *
 * Two separate mechanisms. `Surface.setFrameRate` tells the platform what the content rate
 * is so frame pacing is correct, and is always applied. Changing the display mode is what
 * fixes judder, blanks the screen for one to two seconds, and is behind the setting.
 */
class AndroidDisplayModeController(
    private val activity: Activity,
    private val surfaceViewProvider: () -> SurfaceView?,
) : DisplayModeController {

    override fun currentMode(): DisplayMode? {
        val display = activity.display ?: return null
        val mode = display.mode ?: return null
        return DisplayMode(
            modeId = mode.modeId,
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            refreshRateHz = mode.refreshRate,
        )
    }

    override fun availableModes(): List<DisplayMode> {
        val display = activity.display ?: return emptyList()
        return display.supportedModes.map {
            DisplayMode(
                modeId = it.modeId,
                widthPx = it.physicalWidth,
                heightPx = it.physicalHeight,
                refreshRateHz = it.refreshRate,
            )
        }
    }

    /**
     * Applies the mode and holds until the display reports the new rate.
     *
     * The first frame is held until this returns, because an HDMI mode change blanks the
     * screen and starting playback into a blank screen loses the opening of the item.
     */
    override suspend fun apply(mode: DisplayMode): Boolean {
        val window = activity.window ?: return false

        activity.runOnUiThread {
            window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }

        val settled = withTimeoutOrNull(MODE_CHANGE_TIMEOUT_MS) {
            while (true) {
                val now = currentMode()
                if (now != null && kotlin.math.abs(now.refreshRateHz - mode.refreshRateHz) < 0.5f) {
                    return@withTimeoutOrNull true
                }
                delay(POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false

        Log.i(TAG, "display mode ${mode.refreshRateHz}Hz applied=$settled")
        return settled
    }

    /**
     * Hands the content rate to the platform.
     *
     * FRAME_RATE_COMPATIBILITY_FIXED_SOURCE says the content has a fixed rate and the
     * platform should not resample it. CHANGE_FRAME_RATE_ALWAYS permits a mode change even
     * when it will be visible, which is correct here because the caller has already decided
     * a change is worth it.
     */
    override fun setContentFrameRate(frameRate: Float) {
        val surface: Surface = surfaceViewProvider()?.holder?.surface ?: return
        if (!surface.isValid) return

        runCatching {
            surface.setFrameRate(
                frameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        }.onFailure { Log.w(TAG, "setFrameRate($frameRate) failed", it) }
    }

    /**
     * The whole section 9 sequence, and the log line phase 5 step 3 asks for.
     *
     * Returns true once it is safe to render the first frame.
     */
    suspend fun matchForContent(contentFrameRate: Float?, enabled: Boolean): Boolean {
        val current = currentMode()
        if (current == null) {
            Log.i(TAG, "no display available, playing at whatever the display is doing")
            return true
        }

        contentFrameRate?.let(::setContentFrameRate)

        val decision = RefreshRate.decide(
            contentFrameRate = contentFrameRate,
            current = current,
            available = availableModes(),
            enabled = enabled,
        )

        when (decision) {
            is RefreshRate.Decision.Disabled ->
                Log.i(TAG, "match display rate is off; display ${current.refreshRateHz}Hz unchanged")

            is RefreshRate.Decision.UnknownFrameRate ->
                Log.i(TAG, "content rate unknown; display ${current.refreshRateHz}Hz unchanged")

            is RefreshRate.Decision.KeepCurrent -> Log.i(
                TAG,
                "content ${decision.contentFrameRate}fps  display before ${current.refreshRateHz}Hz  " +
                    "display after ${current.refreshRateHz}Hz  (already divides evenly)",
            )

            is RefreshRate.Decision.NoSuitableMode -> Log.i(
                TAG,
                "content ${decision.contentFrameRate}fps  display before ${current.refreshRateHz}Hz  " +
                    "display after ${current.refreshRateHz}Hz  (no mode divides evenly)",
            )

            is RefreshRate.Decision.SwitchTo -> {
                val applied = apply(decision.target)
                val after = currentMode()?.refreshRateHz ?: current.refreshRateHz
                Log.i(
                    TAG,
                    "content ${decision.contentFrameRate}fps  display before ${current.refreshRateHz}Hz  " +
                        "display after ${after}Hz  (applied=$applied)",
                )
            }
        }

        return true
    }

    private companion object {
        const val TAG = "ThotapalliFrameRate"

        /** An HDMI link takes one to two seconds to come back. Three is generous. */
        const val MODE_CHANGE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 100L
    }
}
