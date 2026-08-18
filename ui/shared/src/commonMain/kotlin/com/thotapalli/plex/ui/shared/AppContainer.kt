package com.thotapalli.plex.ui.shared

import com.thotapalli.plex.core.api.ConnectionSelector
import com.thotapalli.plex.core.api.PlexHttp
import com.thotapalli.plex.core.api.PlexServerApi
import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.data.LibraryRepository
import com.thotapalli.plex.core.data.createPlexDatabase
import com.thotapalli.plex.core.session.DeviceInfo
import com.thotapalli.plex.core.session.IdentityHeaderProvider
import com.thotapalli.plex.core.session.KeyValueStore
import com.thotapalli.plex.core.session.PlexSession
import com.thotapalli.plex.core.session.SecureStore
import com.thotapalli.plex.core.session.ServerDirectory
import com.thotapalli.plex.core.session.SignInController
import com.thotapalli.plex.core.session.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Everything the interface needs, wired once.
 *
 * Assembled here rather than by each application module so phone, television and Windows
 * cannot drift apart in how they build the same objects. Each target supplies only what
 * genuinely differs: its stores, its device info and its database driver.
 */
class AppContainer(
    keyValueStore: KeyValueStore,
    secureStore: SecureStore,
    device: DeviceInfo,
    driverFactory: DatabaseDriverFactory,
    val isTelevision: Boolean = false,
    private val nowMs: () -> Long,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    val http: PlexHttp = PlexHttp.create()

    val identity = IdentityHeaderProvider(keyValueStore, device)

    private val tvApi = PlexTvApi(http, identity)
    private val serverApi = PlexServerApi(http, identity)
    private val tokens = TokenStore(secureStore, keyValueStore)

    private val directory = ServerDirectory(
        api = tvApi,
        selector = ConnectionSelector(http, identity, nowMs),
        store = keyValueStore,
        nowMs = nowMs,
    )

    val session = PlexSession(tvApi, tokens, directory, identity)

    val signIn = SignInController(tvApi, tokens)

    private val database = createPlexDatabase(driverFactory)

    val repository = LibraryRepository(
        api = serverApi,
        database = database,
        scope = scope,
        nowMs = nowMs,
    )

    /** The database, exposed for the download queue and the offline timeline queue. */
    val plexDatabase = database

    val settings = SettingsStore(keyValueStore, isTelevision)

    fun close() {
        http.close()
    }
}

/** The active server, as the interface needs it: where to send requests and how to build URLs. */
data class ActiveServer(
    val name: String,
    val machineIdentifier: String,
    val scope: ServerScope,
    val urls: PlexUrls,
)
