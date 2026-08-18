package com.thotapalli.plex.core.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Refresh rate matching from CLAUDE.md section 9.
 *
 * Two separate problems. Drift is the display clock and the media clock running at
 * slightly different speeds, and is solved by locking playback to the display clock and
 * correcting in audio; that is always on and has no setting. Judder is a refresh rate that
 * does not divide evenly by the content frame rate, and is what this file addresses.
 *
 * The arithmetic is here rather than in each platform so Android and Windows cannot choose
 * different modes for the same file.
 */
object RefreshRate {

    /**
     * Within half a percent of a whole number counts as an even division.
     *
     * The tolerance exists for 23.976 and 59.94, which are 24 and 60 divided by 1.001 and
     * so never divide exactly into anything. Rejecting them would change the display mode
     * for the most common content there is.
     */
    const val TOLERANCE = 0.005

    fun divides(refreshRateHz: Float, contentFrameRate: Float): Boolean {
        if (contentFrameRate <= 0f || refreshRateHz <= 0f) return false
        val ratio = refreshRateHz / contentFrameRate
        if (ratio < 1f) return false
        val nearest = ratio.roundToInt()
        if (nearest < 1) return false
        return abs(ratio - nearest) / nearest <= TOLERANCE
    }

    /**
     * Decides what to do before playback starts.
     *
     * Returns [Decision.KeepCurrent] when the current mode already divides evenly, which is
     * the common case and avoids an HDMI mode change that blanks the screen for one to two
     * seconds for no gain.
     */
    fun decide(
        contentFrameRate: Float?,
        current: DisplayMode,
        available: List<DisplayMode>,
        enabled: Boolean,
    ): Decision {
        if (!enabled) return Decision.Disabled
        if (contentFrameRate == null || contentFrameRate <= 0f) return Decision.UnknownFrameRate

        if (divides(current.refreshRateHz, contentFrameRate)) {
            return Decision.KeepCurrent(current, contentFrameRate)
        }

        // Only modes at the current resolution. Changing resolution to fix judder would
        // trade one visible problem for a worse one.
        val candidate = available
            .filter { it.widthPx == current.widthPx && it.heightPx == current.heightPx }
            .filter { divides(it.refreshRateHz, contentFrameRate) }
            .maxByOrNull { it.refreshRateHz }

        return if (candidate == null) {
            Decision.NoSuitableMode(current, contentFrameRate)
        } else {
            Decision.SwitchTo(candidate, current, contentFrameRate)
        }
    }

    sealed interface Decision {
        /** The setting is off. See CLAUDE.md section 9. */
        data object Disabled : Decision

        /** The server reported no frame rate, so no mode change is attempted. */
        data object UnknownFrameRate : Decision

        data class KeepCurrent(val mode: DisplayMode, val contentFrameRate: Float) : Decision

        data class SwitchTo(
            val target: DisplayMode,
            val from: DisplayMode,
            val contentFrameRate: Float,
        ) : Decision

        /** Nothing divides evenly. Play at the current rate rather than blanking for nothing. */
        data class NoSuitableMode(val mode: DisplayMode, val contentFrameRate: Float) : Decision
    }
}

data class DisplayMode(
    val modeId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRateHz: Float,
)

/**
 * Applies a display mode. Android sets it through the window's preferred mode id, Windows
 * through ChangeDisplaySettingsEx with a DEVMODE carrying dmDisplayFrequency.
 */
interface DisplayModeController {
    fun currentMode(): DisplayMode?
    fun availableModes(): List<DisplayMode>

    /**
     * Applies the mode and reports whether the surface came back at the new rate.
     *
     * The first frame is held until this returns, because an HDMI mode change blanks the
     * screen for one to two seconds and starting playback into a blank screen loses the
     * opening of the item.
     */
    suspend fun apply(mode: DisplayMode): Boolean

    /** Hands the content rate to the platform frame pacing, separately from any mode change. */
    fun setContentFrameRate(frameRate: Float)
}
