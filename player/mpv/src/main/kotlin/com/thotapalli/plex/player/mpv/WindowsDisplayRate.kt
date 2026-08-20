package com.thotapalli.plex.player.mpv

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APIOptions
import com.thotapalli.plex.core.playback.DisplayMode
import com.thotapalli.plex.core.playback.RefreshRate

/**
 * Windows refresh-rate matching from CLAUDE.md section 9, driven through Win32.
 *
 * The arithmetic — whether a mode divides evenly, and which mode to pick — lives in
 * [RefreshRate] in core:playback, shared with Android, so the two platforms cannot choose
 * different modes for the same file. This file is only the Windows mechanism: enumerating the
 * monitor's modes and switching its refresh rate with `ChangeDisplaySettingsEx`.
 *
 * Everything is wrapped in runCatching. A failure to switch, or to restore, must never crash
 * or freeze playback; the picture simply keeps playing at whatever rate the display is at.
 */

/** `EnumDisplaySettings` / `ChangeDisplaySettingsEx`, bound from user32.dll through JNA. */
private interface User32Display : com.sun.jna.Library {
    fun EnumDisplaySettingsW(deviceName: String?, modeNum: Int, devMode: DEVMODE): Int
    fun ChangeDisplaySettingsExW(
        deviceName: String?,
        devMode: DEVMODE?,
        hwnd: Pointer?,
        flags: Int,
        lParam: Pointer?,
    ): Int

    companion object {
        val INSTANCE: User32Display =
            Native.load("user32", User32Display::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/**
 * The Win32 DEVMODEW structure, mapped as far as the fields this client sets and reads.
 *
 * The two internal unions are collapsed to their display variant. Both variants are the same
 * width — the printer variant is eight 16-bit fields and the display variant is a POINTL plus
 * two DWORDs, sixteen bytes either way; the second union is a single DWORD both ways — so a
 * flat layout has the correct offsets. The fixed WCHAR name buffers are modelled as raw byte
 * arrays (two bytes per WCHAR) because their contents are never needed, only their size. The
 * total works out to the canonical 220 bytes on a 64-bit build.
 */
@Suppress("PropertyName", "unused")
internal class DEVMODE : Structure() {
    @JvmField var dmDeviceName: ByteArray = ByteArray(64)
    @JvmField var dmSpecVersion: Short = 0
    @JvmField var dmDriverVersion: Short = 0
    @JvmField var dmSize: Short = 0
    @JvmField var dmDriverExtra: Short = 0
    @JvmField var dmFields: Int = 0
    @JvmField var dmPositionX: Int = 0
    @JvmField var dmPositionY: Int = 0
    @JvmField var dmDisplayOrientation: Int = 0
    @JvmField var dmDisplayFixedOutput: Int = 0
    @JvmField var dmColor: Short = 0
    @JvmField var dmDuplex: Short = 0
    @JvmField var dmYResolution: Short = 0
    @JvmField var dmTTOption: Short = 0
    @JvmField var dmCollate: Short = 0
    @JvmField var dmFormName: ByteArray = ByteArray(64)
    @JvmField var dmLogPixels: Short = 0
    @JvmField var dmBitsPerPel: Int = 0
    @JvmField var dmPelsWidth: Int = 0
    @JvmField var dmPelsHeight: Int = 0
    @JvmField var dmDisplayFlags: Int = 0
    @JvmField var dmDisplayFrequency: Int = 0
    @JvmField var dmICMMethod: Int = 0
    @JvmField var dmICMIntent: Int = 0
    @JvmField var dmMediaType: Int = 0
    @JvmField var dmDitherType: Int = 0
    @JvmField var dmReserved1: Int = 0
    @JvmField var dmReserved2: Int = 0
    @JvmField var dmPanningWidth: Int = 0
    @JvmField var dmPanningHeight: Int = 0

    override fun getFieldOrder(): List<String> = FIELD_ORDER

    private companion object {
        val FIELD_ORDER = listOf(
            "dmDeviceName", "dmSpecVersion", "dmDriverVersion", "dmSize", "dmDriverExtra",
            "dmFields", "dmPositionX", "dmPositionY", "dmDisplayOrientation", "dmDisplayFixedOutput",
            "dmColor", "dmDuplex", "dmYResolution", "dmTTOption", "dmCollate",
            "dmFormName", "dmLogPixels", "dmBitsPerPel", "dmPelsWidth", "dmPelsHeight",
            "dmDisplayFlags", "dmDisplayFrequency", "dmICMMethod", "dmICMIntent",
            "dmMediaType", "dmDitherType", "dmReserved1", "dmReserved2",
            "dmPanningWidth", "dmPanningHeight",
        )
    }
}

/**
 * Reads and switches the primary monitor's display mode on Windows.
 *
 * The `apply` uses `CDS_FULLSCREEN`, a temporary change that is not written to the registry,
 * so the user's saved display configuration is never altered. It is put back explicitly with
 * [restore], which asks Windows to re-apply the registry values.
 */
internal class WindowsDisplayModeController {

    private val user32 get() = User32Display.INSTANCE

    fun currentMode(): DisplayMode? = runCatching {
        val dm = DEVMODE().also { it.dmSize = it.size().toShort() }
        if (user32.EnumDisplaySettingsW(null, ENUM_CURRENT_SETTINGS, dm) == 0) return null
        DisplayMode(
            modeId = dm.dmDisplayFrequency,
            widthPx = dm.dmPelsWidth,
            heightPx = dm.dmPelsHeight,
            refreshRateHz = dm.dmDisplayFrequency.toFloat(),
        )
    }.getOrNull()

    fun availableModes(): List<DisplayMode> = runCatching {
        val modes = mutableListOf<DisplayMode>()
        var index = 0
        while (true) {
            val dm = DEVMODE().also { it.dmSize = it.size().toShort() }
            if (user32.EnumDisplaySettingsW(null, index, dm) == 0) break
            modes += DisplayMode(
                modeId = index,
                widthPx = dm.dmPelsWidth,
                heightPx = dm.dmPelsHeight,
                refreshRateHz = dm.dmDisplayFrequency.toFloat(),
            )
            index++
        }
        modes
    }.getOrDefault(emptyList())

    /** Switches to [mode]'s refresh rate at its resolution. Returns whether Windows accepted it. */
    fun apply(mode: DisplayMode): Boolean = runCatching {
        val dm = DEVMODE().also { it.dmSize = it.size().toShort() }
        // Seed from the current mode so every field Windows expects is present, then override
        // only the resolution and refresh rate.
        if (user32.EnumDisplaySettingsW(null, ENUM_CURRENT_SETTINGS, dm) == 0) return false
        dm.dmPelsWidth = mode.widthPx
        dm.dmPelsHeight = mode.heightPx
        dm.dmDisplayFrequency = mode.refreshRateHz.toInt()
        dm.dmFields = DM_PELSWIDTH or DM_PELSHEIGHT or DM_DISPLAYFREQUENCY
        user32.ChangeDisplaySettingsExW(null, dm, null, CDS_FULLSCREEN, null) == DISP_CHANGE_SUCCESSFUL
    }.getOrDefault(false)

    /** Re-applies the display's saved (registry) settings, undoing any temporary change. */
    fun restore(): Boolean = runCatching {
        user32.ChangeDisplaySettingsExW(null, null, null, 0, null) == DISP_CHANGE_SUCCESSFUL
    }.getOrDefault(false)

    private companion object {
        const val ENUM_CURRENT_SETTINGS = -1
        const val DM_PELSWIDTH = 0x0008_0000
        const val DM_PELSHEIGHT = 0x0010_0000
        const val DM_DISPLAYFREQUENCY = 0x0040_0000
        const val CDS_FULLSCREEN = 0x0000_0004
        const val DISP_CHANGE_SUCCESSFUL = 0
    }
}

/**
 * Applies section 9's refresh-rate matching for one playback session, and always puts the
 * display back afterwards.
 *
 * A single shared instance so the shutdown hook, the player's release path and the mpv engine
 * all act on the same saved state. [enabled] is supplied by the desktop application from the
 * "Match display rate to content" setting (off by default on Windows, per section 9); when it
 * is off, nothing is ever changed and [restore] is a no-op.
 */
object DisplayRateMatcher {

    /** Set by the desktop app from `settings.matchDisplayRate`. Off until then, matching Windows. */
    @Volatile
    var enabled: () -> Boolean = { false }

    private val controller = WindowsDisplayModeController()

    /** True once a mode change has been applied and not yet restored, so restore is only done then. */
    @Volatile
    private var changed = false

    private val lock = Any()

    /**
     * Switches the display to the best whole-number multiple of [contentFps], if the setting is
     * on and a suitable mode exists. Safe to call more than once; a no-op when already matched to
     * the same rate. Every failure is swallowed.
     */
    fun matchForContentRate(contentFps: Double?) {
        synchronized(lock) {
            runCatching {
                if (!enabled()) return
                val current = controller.currentMode() ?: return

                val decision = RefreshRate.decide(
                    contentFrameRate = contentFps?.toFloat(),
                    current = current,
                    available = controller.availableModes(),
                    enabled = true,
                )

                when (decision) {
                    is RefreshRate.Decision.SwitchTo -> {
                        val applied = controller.apply(decision.target)
                        if (applied) changed = true
                        val after = controller.currentMode()?.refreshRateHz ?: current.refreshRateHz
                        log(
                            "content ${decision.contentFrameRate}fps  display before " +
                                "${current.refreshRateHz}Hz  display after ${after}Hz  (applied=$applied)",
                        )
                    }

                    is RefreshRate.Decision.KeepCurrent ->
                        log("content ${decision.contentFrameRate}fps  display ${current.refreshRateHz}Hz already divides evenly")

                    is RefreshRate.Decision.NoSuitableMode ->
                        log("content ${decision.contentFrameRate}fps  display ${current.refreshRateHz}Hz  no mode divides evenly")

                    RefreshRate.Decision.UnknownFrameRate ->
                        log("content rate unknown; display ${current.refreshRateHz}Hz unchanged")

                    RefreshRate.Decision.Disabled -> Unit
                }
            }
        }
    }

    /** Puts the display back to its saved settings if this session changed it. Always safe. */
    fun restore() {
        synchronized(lock) {
            runCatching {
                if (changed) {
                    controller.restore()
                    changed = false
                    log("display restored to saved settings")
                }
            }
        }
    }

    private fun log(message: String) = println("[ThotapalliFrameRate] $message")
}
