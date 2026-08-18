package com.thotapalli.plex.core.download

/**
 * "Download on unmetered networks only", the Windows half. Defaults off, per CLAUDE.md
 * section 11 rule 6.
 *
 * Windows exposes a per-connection metered flag, but only through WinRT
 * `NetworkInformation`, which is not reachable from the JVM without a native bridge, and it
 * is unset on most fixed connections even when it is reachable. There is therefore no
 * reliable metered signal on the desktop, so [isMetered] answers false rather than guessing.
 * That is also why the setting defaults off here: a default of on, resting on a signal that
 * is always false, would be a switch that does nothing.
 *
 * @param unmeteredOnlySetting reads the setting. A lambda rather than the settings object
 *   itself, because the setting lives in ui/shared and core never depends on ui.
 */
class WindowsNetworkConditions(
    private val unmeteredOnlySetting: () -> Boolean = { false },
) : NetworkConditions {

    override fun isMetered(): Boolean = false

    override fun unmeteredOnly(): Boolean = unmeteredOnlySetting()
}
