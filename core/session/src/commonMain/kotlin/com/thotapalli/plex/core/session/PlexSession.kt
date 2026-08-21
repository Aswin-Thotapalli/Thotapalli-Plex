package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.SelectedConnection

/**
 * One place to ask "who is signed in, which server, over which connection".
 *
 * Everything above this layer takes a [PlexSession] rather than assembling tokens and
 * base URIs itself, which is what keeps the account token from ever reaching a server.
 */
class PlexSession(
    private val api: PlexTvApi,
    private val tokens: TokenStore,
    private val directory: ServerDirectory,
    val identity: IdentityHeaderProvider,
) {

    val isSignedIn: Boolean get() = tokens.isSignedIn()

    fun accountToken(): String? = tokens.accountToken()

    fun signOut() = tokens.signOut()

    /**
     * Plex Home users. More than one shows a picker, exactly one skips it silently.
     * See CLAUDE.md section 2.
     */
    suspend fun homeUsers(): List<HomeUser> {
        val token = tokens.accountToken() ?: return emptyList()
        return api.homeUsers(token)
    }

    suspend fun switchHomeUser(user: HomeUser, pin: String? = null) {
        val token = tokens.accountToken() ?: error("Not signed in")
        val switched = api.switchHomeUser(token, user.uuid, pin)
        tokens.storeSwitchedToken(switched, user.uuid)
    }

    suspend fun servers(refresh: Boolean = false): List<PlexServer> {
        val token = tokens.accountToken() ?: return emptyList()
        return directory.servers(token, refresh)
    }

    suspend fun activeServer(): PlexServer? {
        val token = tokens.accountToken() ?: return null
        return directory.activeServer(token)
    }

    fun selectServer(server: PlexServer) = directory.selectServer(server)

    suspend fun connection(server: PlexServer): SelectedConnection? = directory.connection(server)

    suspend fun onNetworkChanged() = directory.onNetworkChanged()

    /**
     * Base URI and access token for the active server.
     *
     * The token here is always the server's own access token, never the account token.
     * See CLAUDE.md section 5.
     */
    suspend fun activeTarget(): ServerTarget? {
        val server = activeServer() ?: return null
        val uri = directory.baseUri(server) ?: return null
        val target = ServerTarget(server = server, baseUri = uri, accessToken = server.accessToken)
        // Remember it so the next launch can render Home from it instantly (see [cachedTarget]).
        directory.persistActiveTargetMeta(server.machineIdentifier, server.name, uri)
        tokens.storeServerToken(server.accessToken)
        return target
    }

    /**
     * The last active target, rebuilt from persisted state with **no network call** — the basis of
     * the optimistic start: Home renders from this immediately while [activeTarget] reconciles with
     * plex.tv in the background. Null on the very first run, before any successful connect.
     */
    fun cachedTarget(): ServerTarget? {
        val meta = directory.cachedActiveTargetMeta() ?: return null
        val token = tokens.serverToken() ?: return null
        val server = PlexServer(
            machineIdentifier = meta.machineIdentifier,
            name = meta.name,
            accessToken = token,
            owned = false,
            connections = listOf(
                com.thotapalli.plex.core.model.ServerConnection(
                    uri = meta.baseUri,
                    local = false,
                    relay = false,
                ),
            ),
        )
        return ServerTarget(server = server, baseUri = meta.baseUri, accessToken = token)
    }
}

data class ServerTarget(
    val server: PlexServer,
    val baseUri: String,
    val accessToken: String,
)
