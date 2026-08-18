package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.api.ConnectionSelector
import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.api.isStale
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.SelectedConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Discovers the servers this account can reach and keeps a chosen connection per server.
 *
 * The chosen connection is cached against the server machine identifier for thirty
 * minutes, then re-probed. A device network change invalidates it immediately, because a
 * local address that was reachable on one network is usually wrong on the next.
 * See CLAUDE.md section 5.
 */
class ServerDirectory(
    private val api: PlexTvApi,
    private val selector: ConnectionSelector,
    private val store: KeyValueStore,
    private val nowMs: () -> Long,
) {

    private val lock = Mutex()
    private val selections = mutableMapOf<String, SelectedConnection>()
    private var servers: List<PlexServer> = emptyList()

    /** Every reachable media server, from the account's resource list. */
    suspend fun servers(accountToken: String, refresh: Boolean = false): List<PlexServer> =
        lock.withLock {
            if (refresh || servers.isEmpty()) {
                servers = api.servers(accountToken)
            }
            servers
        }

    /**
     * The server the user is on. The stored choice wins when it is still reachable;
     * otherwise an owned server wins over a shared one, since a shared library that has
     * gone away should not silently become the default.
     */
    suspend fun activeServer(accountToken: String): PlexServer? {
        val all = servers(accountToken)
        if (all.isEmpty()) return null

        val stored = store.getString(StorageKeys.SELECTED_SERVER)
        return all.firstOrNull { it.machineIdentifier == stored }
            ?: all.firstOrNull { it.owned }
            ?: all.first()
    }

    fun selectServer(server: PlexServer) {
        store.putString(StorageKeys.SELECTED_SERVER, server.machineIdentifier)
    }

    /**
     * The connection to use for [server], probing when there is no live cached choice.
     * Returns null when no connection answered inside the probe timeout.
     */
    suspend fun connection(server: PlexServer, forceReprobe: Boolean = false): SelectedConnection? {
        if (!forceReprobe) {
            val cached = lock.withLock { selections[server.machineIdentifier] }
            if (cached != null && !cached.isStale(nowMs())) return cached
        }

        val fresh = selector.select(server)
        lock.withLock {
            if (fresh == null) {
                selections.remove(server.machineIdentifier)
            } else {
                selections[server.machineIdentifier] = fresh
            }
        }
        return fresh
    }

    /**
     * Called when the device network changes. Drops every cached choice so the next
     * request re-probes rather than trying a local address from the previous network.
     */
    suspend fun onNetworkChanged() {
        lock.withLock { selections.clear() }
    }

    /** The base URI for server requests, or null when the server is unreachable. */
    suspend fun baseUri(server: PlexServer): String? =
        connection(server)?.uri?.trimEnd('/')
}
