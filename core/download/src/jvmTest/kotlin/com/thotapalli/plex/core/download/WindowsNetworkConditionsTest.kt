package com.thotapalli.plex.core.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CLAUDE.md section 11 rule 6: the setting defaults off on Windows, and why. */
class WindowsNetworkConditionsTest {

    @Test
    fun theSettingDefaultsOffAndNothingBlocksADownload() {
        val network = WindowsNetworkConditions()

        assertFalse(network.unmeteredOnly())
        assertFalse(network.isMetered())
        assertTrue(network.isDownloadAllowed())
    }

    @Test
    fun turningTheSettingOnStillAllowsDownloadsBecauseTheDesktopReportsNoMeteredSignal() {
        // Recorded deliberately: with isMetered permanently false the switch has no effect
        // on Windows, which is exactly why it defaults off there.
        val network = WindowsNetworkConditions(unmeteredOnlySetting = { true })

        assertTrue(network.isDownloadAllowed())
    }

    @Test
    fun theSettingIsReadOnEachCheckRatherThanCaptured() {
        var unmeteredOnly = false
        val network = WindowsNetworkConditions { unmeteredOnly }

        assertFalse(network.unmeteredOnly())
        unmeteredOnly = true
        // A queue blocked on the setting is released at its next wake-up, not at the next
        // restart of the application.
        assertTrue(network.unmeteredOnly())
    }
}
