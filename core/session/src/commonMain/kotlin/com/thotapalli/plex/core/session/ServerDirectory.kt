package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.api.ConnectionSelector
import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.api.isStale
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.SelectedConnection
import com.thotapalli.plex.core.model.ServerConnection
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
     *
     * The in-memory cache is checked first, then the connection persisted across process
     * restarts: while it is still inside its thirty-minute window it is confirmed with a
     * single fast identity probe and reused immediately, so the common relaunch does one
     * quick check instead of waiting out a probe of every connection. Only a failed
     * verification or a stale entry falls through to the full parallel probe.
     */
    suspend fun connection(server: PlexServer, forceReprobe: Boolean = false): SelectedConnection? {
        if (!forceReprobe) {
            val cached = lock.withLock { selections[server.machineIdentifier] }
            if (cached != null && !cached.isStale(nowMs())) return cached

            val persisted = loadPersisted(server.machineIdentifier)
            if (persisted != null && !persisted.isStale(nowMs())) {
                val verified = selector.verify(server, persisted.connection)
                if (verified != null) {
                    lock.withLock { selections[server.machineIdentifier] = verified }
                    persist(verified)
                    return verified
                }
            }
        }

        val fresh = selector.select(server)
        lock.withLock {
            if (fresh == null) {
                selections.remove(server.machineIdentifier)
            } else {
                selections[server.machineIdentifier] = fresh
            }
        }
        if (fresh != null) {
            persist(fresh)
        } else {
            store.remove(persistKey(server.machineIdentifier))
        }
        return fresh
    }

    /**
     * Called when the device network changes. Drops every cached choice, in memory and on
     * disk, so the next request re-probes rather than trying a local address from the
     * previous network.
     */
    suspend fun onNetworkChanged() {
        lock.withLock {
            selections.clear()
            servers.forEach { store.remove(persistKey(it.machineIdentifier)) }
        }
    }

    private fun persistKey(machineIdentifier: String): String =
        "${StorageKeys.CONNECTION_PREFIX}$machineIdentifier"

    private fun persist(selection: SelectedConnection) {
        // A newline delimiter is safe: a connection URI never contains one. Fields:
        // uri, local, relay, roundTripMs, selectedAtMs. The machine identifier is the key.
        val encoded = listOf(
            selection.connection.uri,
            selection.connection.local.toString(),
            selection.connection.relay.toString(),
            selection.roundTripMs.toString(),
            selection.selectedAtMs.toString(),
        ).joinToString("\n")
        store.putString(persistKey(selection.machineIdentifier), encoded)
    }

    private fun loadPersisted(machineIdentifier: String): SelectedConnection? {
        val raw = store.getString(persistKey(machineIdentifier)) ?: return null
        val parts = raw.split("\n")
        if (parts.size != 5) return null
        val local = parts[1].toBooleanStrictOrNull() ?: return null
        val relay = parts[2].toBooleanStrictOrNull() ?: return null
        val roundTripMs = parts[3].toLongOrNull() ?: return null
        val selectedAtMs = parts[4].toLongOrNull() ?: return null
        return SelectedConnection(
            machineIdentifier = machineIdentifier,
            connection = ServerConnection(uri = parts[0], local = local, relay = relay),
            roundTripMs = roundTripMs,
            selectedAtMs = selectedAtMs,
        )
    }

    /** The base URI for server requests, or null when the server is unreachable. */
    suspend fun baseUri(server: PlexServer): String? =
        connection(server)?.uri?.trimEnd('/')

    // --- optimistic start: the last active target's non-secret parts -------------------------

    /** Persist the machine identifier, name and base URI of the current target (no secret). */
    fun persistActiveTargetMeta(machineIdentifier: String, name: String, baseUri: String) {
        store.putString(
            StorageKeys.ACTIVE_TARGET_META,
            listOf(machineIdentifier, name, baseUri).joinToString("\n"),
        )
    }

    /** The persisted target meta, read synchronously with no network. */
    fun cachedActiveTargetMeta(): TargetMeta? {
        val raw = store.getString(StorageKeys.ACTIVE_TARGET_META) ?: return null
        val parts = raw.split("\n")
        if (parts.size != 3) return null
        return TargetMeta(machineIdentifier = parts[0], name = parts[1], baseUri = parts[2])
    }
}

/** The non-secret half of a cached active target (see [ServerDirectory.cachedActiveTargetMeta]). */
data class TargetMeta(val machineIdentifier: String, val name: String, val baseUri: String)
