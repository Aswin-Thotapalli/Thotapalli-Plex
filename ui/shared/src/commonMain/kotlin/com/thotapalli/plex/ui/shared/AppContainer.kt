package com.thotapalli.plex.ui.shared

import com.thotapalli.plex.core.api.ConnectionSelector
import com.thotapalli.plex.core.api.PlexHttp
import com.thotapalli.plex.core.api.PlexServerApi
import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.RangedDownloadSource
import com.thotapalli.plex.core.api.RecordedDownloadKeys
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.data.DatabaseDriverFactory
import com.thotapalli.plex.core.data.LibraryRepository
import com.thotapalli.plex.core.data.SqlDownloadStore
import com.thotapalli.plex.core.data.SqlPendingTimelineStore
import com.thotapalli.plex.core.data.createPlexDatabase
import com.thotapalli.plex.core.download.DownloadFileSystem
import com.thotapalli.plex.core.download.DownloadQueue
import com.thotapalli.plex.core.download.DownloadStore
import com.thotapalli.plex.core.download.DownloadTransport
import com.thotapalli.plex.core.download.NetworkConditions
import com.thotapalli.plex.core.download.OfflineResolver
import com.thotapalli.plex.core.download.OfflineTimelineQueue
import com.thotapalli.plex.core.session.DeviceInfo
import com.thotapalli.plex.core.session.IdentityHeaderProvider
import com.thotapalli.plex.core.session.KeyValueStore
import com.thotapalli.plex.core.session.PlexSession
import com.thotapalli.plex.core.session.SecureStore
import com.thotapalli.plex.core.session.ServerDirectory
import com.thotapalli.plex.core.session.SignInController
import com.thotapalli.plex.core.session.TokenStore
import com.thotapalli.plex.core.session.UpdateChecker
import com.thotapalli.plex.core.session.UpdateTarget
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
    /**
     * True on Windows. The desktop video surface is a heavyweight window the Compose overlay
     * cannot draw over, so the player shows its controls in a bar beside the video rather
     * than floating on top of it.
     */
    val isDesktop: Boolean = false,
    private val nowMs: () -> Long,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /**
     * Downloads need a place to write, a way to fetch ranges and a network signal. Each
     * target supplies its own; a target that has not wired them yet gets a working
     * application with the Downloads screen honestly empty, rather than one that refuses
     * to start.
     */
    downloadFileSystem: DownloadFileSystem? = null,
    networkConditions: NetworkConditions? = null,
    /** Which artefact this build is, so the update check compares the right entry. */
    private val updateTarget: UpdateTarget? = null,
    private val currentVersionCode: Int = 0,
    private val updateManifestUrl: String? = null,
) {

    val http: PlexHttp = PlexHttp.create()

    val identity = IdentityHeaderProvider(keyValueStore, device)

    private val tvApi = PlexTvApi(http, identity)
    /** The server API, exposed so the player screen can build a PlaybackController. */
    val serverApi = PlexServerApi(http, identity)
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

    /**
     * Shares the player's state with the desktop's floating overlay window, which lives in a
     * different composition from the video. Unused on Android. See [PlayerBridge].
     */
    val playerBridge = com.thotapalli.plex.ui.shared.player.PlayerBridge()

    val downloadStore: DownloadStore? =
        if (downloadFileSystem != null) SqlDownloadStore(database) else null

    /**
     * Part and subtitle keys, recorded as items are queued.
     *
     * The transport is handed keys rather than deriving URLs, so it can never invent a
     * path for something that was never queued.
     */
    val downloadKeys = RecordedDownloadKeys()

    /**
     * The transport, bound to whichever server is active.
     *
     * The queue is built once at start up but the server is only known after the connection
     * probe, so the transport is a stable object whose target is set later rather than a
     * queue that has to be rebuilt on every server change.
     */
    private val transport = BoundTransport()

    fun bindDownloadServer(serverScope: ServerScope) {
        transport.bind(RangedDownloadSource(http, identity, serverScope, downloadKeys))
    }

    val downloadQueue: DownloadQueue? =
        if (downloadFileSystem != null && networkConditions != null) {
            DownloadQueue(
                store = SqlDownloadStore(database),
                transport = transport,
                files = downloadFileSystem,
                network = networkConditions,
                scope = scope,
                nowMs = nowMs,
            )
        } else {
            null
        }

    private val files = downloadFileSystem

    /** Where a sidecar subtitle lands. Delegated so only the platform knows the layout. */
    fun downloadSubtitlePath(ratingKey: String, streamId: String, language: String): String =
        files?.subtitlePathFor(ratingKey, streamId, language).orEmpty()

    val offlineResolver: OfflineResolver? = downloadFileSystem?.let {
        OfflineResolver(SqlDownloadStore(database), it)
    }

    /**
     * Progress recorded while the server was unreachable, replayed on reconnection.
     * See CLAUDE.md section 11.
     */
    val offlineTimeline = OfflineTimelineQueue(SqlPendingTimelineStore(database), nowMs)

    /**
     * The launch update check from CLAUDE.md section 17 point 4.
     *
     * Null when the target has not declared what artefact it is, in which case no check
     * happens at all rather than one that compares against the wrong entry.
     */
    val updateChecker: UpdateChecker? =
        if (updateTarget != null && updateManifestUrl != null) {
            UpdateChecker(
                store = keyValueStore,
                source = { url -> http.fetchText(url) },
                target = updateTarget,
                currentVersionCode = currentVersionCode,
                manifestUrl = updateManifestUrl,
                nowMs = nowMs,
            )
        } else {
            null
        }

    fun close() {
        http.close()
    }
}

/**
 * A transport whose server is set after construction.
 *
 * Throws rather than silently doing nothing when unbound: a download that quietly fetches
 * zero bytes would be marked failed with no explanation.
 */
private class BoundTransport : DownloadTransport {

    private var delegate: DownloadTransport? = null

    fun bind(source: DownloadTransport) {
        delegate = source
    }

    private fun require(): DownloadTransport =
        delegate ?: error("no server bound; downloads cannot start before a server is selected")

    override suspend fun fetchRange(partId: String, startByte: Long, endByte: Long): ByteArray =
        require().fetchRange(partId, startByte, endByte)

    override suspend fun fetchSubtitle(streamId: String): ByteArray =
        require().fetchSubtitle(streamId)
}

/** The active server, as the interface needs it: where to send requests and how to build URLs. */
data class ActiveServer(
    val name: String,
    val machineIdentifier: String,
    val scope: ServerScope,
    val urls: PlexUrls,
)
