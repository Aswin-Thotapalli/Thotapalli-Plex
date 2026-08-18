package com.thotapalli.plex.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * "Download on unmetered networks only", the Android half. Defaults on, per CLAUDE.md
 * section 11 rule 6.
 *
 * @param unmeteredOnlySetting reads the setting. A lambda rather than the settings object
 *   itself, because the setting lives in ui/shared and core never depends on ui. It is read
 *   on each check rather than captured, so turning the setting off releases a blocked queue
 *   at the next wake-up instead of at the next restart.
 */
class AndroidNetworkConditions(
    context: Context,
    private val unmeteredOnlySetting: () -> Boolean = { true },
) : NetworkConditions {

    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * Metered unless the active network says otherwise.
     *
     * No network, or capabilities the system will not report, counts as metered: the cost of
     * being wrong that way is a download that waits, and the cost of being wrong the other
     * way is a viewer's mobile data spent on a film.
     */
    override fun isMetered(): Boolean {
        val manager = connectivity ?: return true
        val active = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(active) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    override fun unmeteredOnly(): Boolean = unmeteredOnlySetting()
}
