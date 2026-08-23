package com.thotapalli.plex.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thotapalli.plex.core.api.LibraryContentType
import com.thotapalli.plex.core.api.LibraryFilter
import com.thotapalli.plex.core.api.LibrarySort
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.SearchResults
import com.thotapalli.plex.core.api.ServerActivity
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.LibraryKind
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Movie
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.ServerUpdate
import com.thotapalli.plex.core.model.SharedUser
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.model.watched
import com.thotapalli.plex.core.download.DownloadQueue
import com.thotapalli.plex.core.download.OfflineResolver
import com.thotapalli.plex.ui.design.ThemeMode
import com.thotapalli.plex.ui.shared.screens.DownloadEntry
import com.thotapalli.plex.ui.shared.screens.SettingsScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for every screen below the player.
 *
 * One view model rather than one per screen: the screens all read the same library data
 * through the same repository, and splitting them would mean each refetching what the
 * previous one already has.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AppState(themeMode = container.settings.themeMode))
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** The live scan-progress poll. Cancelled and replaced on server change and sign-out. */
    private var scanJob: Job? = null

    /** Set by the platform so the update notice can open the artefact in a browser. */
    var onOpenUrl: ((String) -> Unit)? = null

    /**
     * Present only once the platform supplies a file system, a transport and a network
     * signal. Null on a target that has not wired them, so the Downloads screen shows an
     * honest empty list rather than the application refusing to start.
     */
    private val downloads: DownloadQueue? get() = container.downloadQueue
    private val offline: OfflineResolver? get() = container.offlineResolver

    fun start() {
        viewModelScope.launch {
            if (!container.session.isSignedIn) {
                _state.update { it.copy(phase = AppPhase.SIGNED_OUT) }
                return@launch
            }
            // Show the signed-in account name from the moment we know we're signed in, so the
            // profile chip never reads a bare "Account" during the optimistic start (connect()
            // later upgrades it to the active Home user's name where there is one).
            _state.update { it.copy(accountName = container.session.signedInUsername()) }
            // Optimistic start: if a previous session left a known-good target, render Home from
            // it immediately and reconcile with plex.tv in the background. This turns the common
            // relaunch from a serial plex.tv (home users + resources) + probe sequence into an
            // instant local start — the single biggest win for perceived startup speed.
            val cached = container.session.cachedTarget()
            if (cached != null) {
                applyTarget(cached)
                launch { reconcile(cached) }
            } else {
                connect()
            }
        }
    }

    // --- sign in ---------------------------------------------------------------------------

    fun signIn() {
        viewModelScope.launch {
            container.signIn.signIn().collect { signInState ->
                _state.update { it.copy(signIn = signInState) }
                if (signInState is com.thotapalli.plex.core.session.SignInState.SignedIn) {
                    connect()
                }
            }
        }
    }

    fun signOut() {
        scanJob?.cancel()
        container.session.signOut()
        _state.value = AppState(phase = AppPhase.SIGNED_OUT, themeMode = container.settings.themeMode)
    }

    /**
     * More than one Plex Home user shows a picker. Exactly one skips it silently.
     * See CLAUDE.md section 2.
     */
    private suspend fun connect() {
        _state.update { it.copy(phase = AppPhase.CONNECTING, error = null) }

        val homeUsers = runCatching { container.session.homeUsers() }.getOrDefault(emptyList())
        if (homeUsers.size > 1 && _state.value.homeUser == null) {
            _state.update { it.copy(phase = AppPhase.PICKING_HOME_USER, homeUsers = homeUsers) }
            return
        }

        // Exactly one Home user skips the picker silently — but we still record that user so the
        // profile chip and top bar can show their name. Without this, the single-user case left
        // homeUser null and the name never appeared.
        if (_state.value.homeUser == null && homeUsers.isNotEmpty()) {
            _state.update { it.copy(homeUser = homeUsers.first()) }
        }

        // The display name: the active Home user if there is one, otherwise the signed-in account
        // name (a single non-Home account never returns any Home users, so homeUser stays null and
        // the chip would otherwise read "Account").
        _state.update {
            it.copy(accountName = it.homeUser?.title ?: container.session.signedInUsername())
        }

        loadHome()
    }

    fun selectHomeUser(user: HomeUser, pin: String? = null) {
        viewModelScope.launch {
            runCatching { container.session.switchHomeUser(user, pin) }
                .onSuccess {
                    _state.update { it.copy(homeUser = user, accountName = user.title) }
                    loadHome()
                }
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
        }
    }

    // --- home ------------------------------------------------------------------------------

    private suspend fun loadHome() {
        val target = runCatching { container.session.activeTarget() }.getOrNull()
        if (target == null) {
            _state.update {
                it.copy(
                    phase = AppPhase.ERROR,
                    error = "No server answered. Check the server is running and reachable.",
                )
            }
            return
        }
        applyTarget(target)
        // Now that the server answered, flush any progress recorded while offline (§11). Runs here
        // so it also fires after a reconnect (onNetworkChanged -> loadHome).
        replayOfflineTimeline()
    }

    /**
     * Replay offline-recorded progress to the server on reconnection (§11). The queue keeps only the
     * newest row per item; each accepted row is deleted, a stale one (server newer) is dropped, and a
     * still-failing one stays for next time.
     */
    private fun replayOfflineTimeline() {
        val server = _state.value.server ?: return
        viewModelScope.launch {
            val sid = container.identity.newSessionIdentifier()
            runCatching {
                container.offlineTimeline.replay { row ->
                    runCatching {
                        container.serverApi.timeline(
                            scope = server.scope,
                            ratingKey = row.ratingKey,
                            state = com.thotapalli.plex.core.api.TimelineState.STOPPED,
                            positionMs = row.positionMs,
                            durationMs = row.durationMs,
                            sessionIdentifier = sid,
                        )
                    }.fold(
                        onSuccess = { com.thotapalli.plex.core.download.ReplayOutcome.ACCEPTED },
                        onFailure = { com.thotapalli.plex.core.download.ReplayOutcome.FAILED },
                    )
                }
            }
        }
    }

    /**
     * Bind [target] as the active server and fill Home. Used both by the full [connect] path and
     * by the optimistic start, which hands in a target rebuilt from cache with no network. The
     * full server list (only Settings' picker needs it) loads off the hot path so it never gates
     * Home appearing.
     */
    private fun applyTarget(target: com.thotapalli.plex.core.session.ServerTarget) {
        val active = ActiveServer(
            name = target.server.name,
            machineIdentifier = target.server.machineIdentifier,
            scope = ServerScope(target.baseUri, target.accessToken),
            urls = PlexUrls(target.baseUri, target.accessToken),
        )
        container.bindDownloadServer(active.scope)
        _state.update { it.copy(server = active, phase = AppPhase.READY) }

        refreshHome(active)
        refreshDownloads()
        checkForUpdate()
        refreshServerUpdate(active)
        startScanPolling(active)

        viewModelScope.launch {
            val servers = runCatching { container.session.servers() }.getOrDefault(emptyList())
            if (servers.isNotEmpty()) _state.update { it.copy(allServers = servers) }
        }
    }

    /**
     * Background reconciliation after an optimistic start: confirm — or re-probe — the real target
     * and rebind only if the connection or token actually changed since it was cached. Silent; the
     * user is already on Home. If plex.tv is unreachable but the cached connection still answers,
     * nothing changes and the app keeps working.
     */
    private suspend fun reconcile(cached: com.thotapalli.plex.core.session.ServerTarget) {
        val fresh = runCatching { container.session.activeTarget() }.getOrNull() ?: return
        if (fresh.baseUri != cached.baseUri || fresh.accessToken != cached.accessToken) {
            applyTarget(fresh)
        }
    }

    // --- live scan progress ----------------------------------------------------------------

    /**
     * Poll the server's running library scans while a server is active, so the interface can
     * show live progress and pick up newly scanned items on its own.
     *
     * When a library's scan drops out of the list after having been present, that scan has
     * finished: its contents are refreshed from the network and Home is rebuilt, so new items
     * appear without the viewer reaching for a refresh. Lightweight and cancellable — a single
     * job on [viewModelScope], replaced on every server change and stopped on sign-out.
     */
    private fun startScanPolling(server: ActiveServer) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            var scanning = emptySet<String>()
            while (isActive) {
                val activities = container.serverApi.activities(server.scope)
                publishScanActivities(activities)

                val current = activities.mapNotNull { it.librarySectionId }.toSet()
                val finished = scanning - current
                if (finished.isNotEmpty()) {
                    finished.forEach { sectionId ->
                        _state.value.libraries.firstOrNull { it.key == sectionId }?.let { library ->
                            runCatching {
                                container.repository.refreshLibraryContents(server.scope, library)
                            }
                        }
                    }
                    // If the viewer is looking at a library that just finished, reload it too.
                    _state.value.library?.let { open ->
                        if (open.library.key in finished) {
                            loadLibraryContents(open.library, open.unwatchedOnly)
                        }
                    }
                    refreshHome(server)
                }
                scanning = current
                delay(SCAN_POLL_INTERVAL_MS)
            }
        }
    }

    fun refreshHome(server: ActiveServer = requireServer()) {
        viewModelScope.launch {
            val libraries = runCatching {
                container.repository.libraries(server.scope, server.machineIdentifier)
            }.getOrDefault(emptyList()).let(::orderedLibraries)

            val continueWatching = runCatching {
                container.repository.continueWatching(server.scope)
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(libraries = libraries, continueWatching = continueWatching)
            }

            // A preview rail of each library's titles, so Home browses like a shelf of
            // content rather than a list of folders. Loaded in parallel so the rails fill
            // together rather than one after another.
            val previews = coroutineScope {
                libraries.map { library ->
                    async {
                        library.key to runCatching {
                            container.repository.libraryContents(server.scope, library, false)
                                .filter { it !is MediaCollection }
                                .take(HOME_RAIL_LIMIT)
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().toMap()
            }
            _state.update { it.copy(libraryPreviews = previews) }
        }
    }

    // --- library ---------------------------------------------------------------------------

    fun openLibrary(library: Library) {
        _state.update { it.copy(library = LibraryState(library = library, loading = true)) }
        loadLibraryContents(library, _state.value.library?.unwatchedOnly ?: false)
    }

    fun setUnwatchedOnly(unwatchedOnly: Boolean) {
        val current = _state.value.library ?: return
        _state.update { it.copy(library = current.copy(unwatchedOnly = unwatchedOnly, loading = true)) }
        refreshLibraryView()
    }

    /** Change the library sort (#16). Overrides the fixed-sort brief §14.3 per the owner's request. */
    fun setSort(sort: String) {
        val current = _state.value.library ?: return
        _state.update { it.copy(library = current.copy(sort = sort, loading = true)) }
        refreshLibraryView()
    }

    /** Filter the library by genre (#16); null clears. */
    fun setGenre(genre: String?) {
        val current = _state.value.library ?: return
        _state.update { it.copy(library = current.copy(selectedGenre = genre, loading = true)) }
        refreshLibraryView()
    }

    /**
     * Re-query the open library for the current sort + filters. The default view (title A–Z, no
     * genre) uses the cache-first repository; any other sort or a genre goes straight to the server,
     * because the local cache holds only the default order.
     */
    private fun refreshLibraryView() {
        val lib = _state.value.library ?: return
        if (lib.sort == "titleSort:asc" && lib.selectedGenre == null) {
            loadLibraryContents(lib.library, lib.unwatchedOnly)
            return
        }
        val server = requireServer()
        val contentType = when (lib.library.kind) {
            LibraryKind.MOVIE -> LibraryContentType.MOVIE
            LibraryKind.SHOW -> LibraryContentType.SHOW
            else -> null
        }
        viewModelScope.launch {
            val items = if (contentType == null) {
                emptyList()
            } else {
                runCatching {
                    container.serverApi.libraryContents(
                        server.scope,
                        lib.library.key,
                        contentType,
                        LibrarySort.entries.firstOrNull { it.wire == lib.sort } ?: LibrarySort.TITLE_ASC,
                        LibraryFilter(genre = lib.selectedGenre, unwatchedOnly = lib.unwatchedOnly),
                    )
                }.getOrDefault(emptyList())
            }
            _state.update { s ->
                s.copy(
                    library = s.library?.copy(
                        items = items.filterNot { it is MediaCollection },
                        loading = false,
                    ),
                )
            }
        }
    }

    private fun loadLibraryContents(library: Library, unwatchedOnly: Boolean) {
        val server = requireServer()
        viewModelScope.launch {
            val contents = runCatching {
                container.repository.libraryContents(server.scope, library, unwatchedOnly)
            }.getOrDefault(emptyList())

            _state.update { current ->
                current.copy(
                    library = current.library?.copy(
                        items = contents.filter { it !is MediaCollection },
                        collections = contents.filterIsInstance<MediaCollection>(),
                        loading = false,
                    ),
                )
            }
        }
    }

    fun openCollection(collection: MediaCollection) {
        val server = requireServer()
        viewModelScope.launch {
            val children = runCatching {
                container.repository.collectionChildren(server.scope, collection)
            }.getOrDefault(emptyList())

            _state.update { current ->
                current.copy(library = current.library?.copy(openCollection = collection, items = children))
            }
        }
    }

    fun closeCollection() {
        val current = _state.value.library ?: return
        _state.update { it.copy(library = current.copy(openCollection = null)) }
        loadLibraryContents(current.library, current.unwatchedOnly)
    }

    // --- detail ----------------------------------------------------------------------------

    fun openDetail(item: MediaItem) {
        val server = requireServer()
        _state.update { it.copy(detail = DetailState(item = item, loading = true)) }

        viewModelScope.launch {
            val detail = runCatching { container.repository.detail(server.scope, item.ratingKey) }.getOrNull()

            // Episodic content — a Show, or an Episode opened directly (e.g. from the Home
            // "Continue Watching" hero) — loads the parent show's full season and episode set so
            // the detail screen can offer season/episode navigation regardless of entry point.
            // A movie has neither, so it takes the plain branch. See CLAUDE.md section 14 item 5.
            val show: Show? = when (item) {
                is Show -> item
                is Episode -> resolveShow(item)
                else -> null
            }

            if (show != null) {
                val seasons = runCatching { container.repository.seasons(server.scope, show) }
                    .getOrDefault(emptyList())
                val episodes = runCatching { container.repository.episodes(server.scope, show) }
                    .getOrDefault(emptyList())
                val next = runCatching { container.repository.nextUnwatchedEpisode(server.scope, show) }
                    .getOrNull()

                // The season shown first: when opened on an episode, that episode's own season;
                // otherwise the season holding the next unwatched episode.
                val targetSeasonKey = (item as? Episode)?.seasonRatingKey ?: next?.seasonRatingKey
                val selectedSeason = seasons.firstOrNull { it.ratingKey == targetSeasonKey }
                    ?: seasons.firstOrNull()
                // When opened on an episode, that episode is the one highlighted in the list and
                // resumed by the primary action; prefer the freshly loaded copy for its watch state.
                val selectedEpisode = (item as? Episode)?.let { opened ->
                    episodes.firstOrNull { it.ratingKey == opened.ratingKey } ?: opened
                }

                _state.update {
                    it.copy(
                        detail = DetailState(
                            item = item,
                            detail = detail,
                            seasons = seasons,
                            episodes = episodes,
                            selectedSeason = selectedSeason,
                            selectedEpisode = selectedEpisode,
                            nextUnwatched = next,
                            loading = false,
                        ),
                    )
                }
            } else {
                _state.update { it.copy(detail = DetailState(item = item, detail = detail, loading = false)) }
            }
        }
    }

    /**
     * The parent show for an episode-opened detail, so the season/episode navigation works from
     * any entry point. Prefers the cached show, which carries the real library key; falls back to
     * a synthetic show built from the episode's own fields when the show is not cached — the
     * season and episode reads key on the show rating key, so navigation still works either way.
     */
    private fun resolveShow(episode: Episode): Show =
        (container.repository.cachedItem(episode.showRatingKey) as? Show)
            ?: Show(
                ratingKey = episode.showRatingKey,
                title = episode.showTitle,
                year = null,
                summary = "",
                thumbPath = null,
                artPath = null,
                durationMs = 0L,
                viewOffsetMs = 0L,
                viewCount = 0,
                titleSort = episode.showTitle,
                libraryKey = "",
                childCount = 0,
                leafCount = 0,
                viewedLeafCount = 0,
            )

    fun selectSeason(season: Season) {
        _state.update { it.copy(detail = it.detail?.copy(selectedSeason = season, selectedEpisode = null)) }
    }

    /** Tapping an episode row inspects it (shows its summary) without playing. */
    fun selectEpisode(episode: Episode) {
        _state.update { it.copy(detail = it.detail?.copy(selectedEpisode = episode)) }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
    }

    fun closeLibrary() {
        _state.update { it.copy(library = null) }
    }

    /**
     * One place that answers a back gesture, so hardware Back, the on-screen arrow and the
     * desktop Escape key all pop the same stack in the same order. Returns false only at a
     * top-level destination, which is the platform's cue to let Back do its default thing
     * (leave the app on the home screen, nothing below it). See CLAUDE.md section 13.
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.playback != null -> { closePlayer(); true }
            s.detail != null -> { closeDetail(); true }
            s.library?.openCollection != null -> { closeCollection(); true }
            s.library != null -> { closeLibrary(); true }
            else -> false
        }
    }

    /** True when there is somewhere to go back to, so the interface can show the arrow. */
    val canGoBack: Boolean
        get() = _state.value.let {
            it.playback != null || it.detail != null ||
                it.library?.openCollection != null || it.library != null
        }

    // --- watched state ---------------------------------------------------------------------

    /**
     * Mark as watched or unwatched, driven straight off the server so the change shows on
     * every device. The interface updates optimistically and reconciles from Home; a failed
     * call simply leaves the server as it was. See CLAUDE.md section 5.
     */
    fun toggleWatched(item: MediaItem) {
        setWatched(item, !item.watched)
    }

    /** Explicitly set watched or unwatched — the item menu offers both directly. */
    fun setWatched(item: MediaItem, watched: Boolean) {
        val server = _state.value.server ?: return
        // Reflect the change everywhere it is visible right now — the open detail, the open library
        // grid, the Home rails and Continue Watching — not just the detail. Marking a poster watched
        // from its long-press menu must flip that poster's badge immediately (#1), not only after a
        // later reload. The server call reconciles the rest.
        _state.update { it.reflectWatched(item.ratingKey, watched) }
        viewModelScope.launch {
            runCatching {
                if (watched) container.serverApi.scrobble(server.scope, item.ratingKey)
                else container.serverApi.unscrobble(server.scope, item.ratingKey)
            }
            // Deliberately do NOT reload the open library here: the repository read is cache-first
            // and the cache still holds the pre-scrobble watch state for a moment, so a reload would
            // immediately revert the optimistic badge we just set. The optimistic [reflectWatched]
            // above is the source of truth for the UI until the next natural navigation refreshes it
            // from the server. Home still refreshes (its rails re-fetch). See §5 (#1).
            refreshHome(server)
        }
    }

    /**
     * Applies a watched/unwatched change to every copy of [ratingKey] currently held in UI state,
     * so the change shows instantly wherever that item appears. See [setWatched] (#1).
     */
    private fun AppState.reflectWatched(ratingKey: String, watched: Boolean): AppState {
        fun MediaItem.maybe(): MediaItem = if (this.ratingKey == ratingKey) markedWatched(watched) else this
        return copy(
            // A newly watched item leaves Continue Watching; otherwise just update it in place.
            continueWatching = if (watched) {
                continueWatching.filterNot { it.ratingKey == ratingKey }
            } else {
                continueWatching.map { it.maybe() }
            },
            library = library?.let { lib -> lib.copy(items = lib.items.map { it.maybe() }) },
            detail = detail?.let { d ->
                d.copy(
                    item = d.item.maybe(),
                    episodes = d.episodes.map { it.maybe() as Episode },
                    selectedEpisode = d.selectedEpisode?.let { it.maybe() as Episode },
                )
            },
        )
    }

    /**
     * Marks an entire container — a whole show or a single season — watched or unwatched. Plex's
     * scrobble endpoint applied to a container key marks every child in one call, so this covers
     * the series without walking the episodes. The open detail is then reloaded so its season and
     * episode rows reflect the new state. See CLAUDE.md section 5 (§12 high-value parity).
     */
    fun setContainerWatched(containerRatingKey: String, watched: Boolean) {
        val server = _state.value.server ?: return
        viewModelScope.launch {
            runCatching {
                if (watched) container.serverApi.scrobble(server.scope, containerRatingKey)
                else container.serverApi.unscrobble(server.scope, containerRatingKey)
            }
            refreshHome(server)
            // Reload the open detail so every episode row shows the new watch state.
            _state.value.detail?.item?.let { openDetail(it) }
        }
    }

    // --- server administration -------------------------------------------------------------
    //
    // The owner runs the server and asked for the management actions the official app exposes.
    // Each is a single server call; the interface updates optimistically and reconciles from a
    // Home refresh. Destructive ones (delete) are confirmed in the interface before they land here.

    /** Remove an item from the Continue Watching row without changing its watched state. */
    fun removeFromContinueWatching(item: MediaItem) {
        val server = _state.value.server ?: return
        _state.update {
            it.copy(continueWatching = it.continueWatching.filterNot { m -> m.ratingKey == item.ratingKey })
        }
        viewModelScope.launch {
            runCatching { container.serverApi.removeFromContinueWatching(server.scope, item.ratingKey) }
                .onFailure { notify("Couldn't remove from Continue Watching") }
            refreshHome(server)
        }
    }

    /** Refresh one item's metadata from its agents. */
    fun refreshItemMetadata(item: MediaItem) =
        serverAction("Refreshing “${item.title}”…", "Couldn't refresh metadata") { s ->
            container.serverApi.refreshMetadata(s.scope, item.ratingKey)
        }

    /** Analyze one item's media (bitrate, duration, resolution). */
    fun analyzeItem(item: MediaItem) =
        serverAction("Analyzing “${item.title}”…", "Couldn't analyze") { s ->
            container.serverApi.analyze(s.scope, item.ratingKey)
        }

    /** Scan one library for newly added files. */
    fun scanLibrary(library: Library) {
        // Show progress the instant the scan is requested, for ANY library. The server only reports
        // an activity while a scan is in flight, and a small library (few files) can finish between
        // 1.5s polls — so a poll-only indicator only ever caught the long TV-show scans. An
        // optimistic entry bridges that: it shows immediately and until either the real server
        // activity takes over or a short grace elapses (a scan too fast to be polled). See §5, #3.
        optimisticScanCycles[library.key] = OPTIMISTIC_SCAN_CYCLES
        _state.update {
            it.copy(
                scanActivities = it.scanActivities.filterNot { a -> a.librarySectionId == library.key } +
                    syntheticScan(library.key),
            )
        }
        serverAction("Scanning “${library.title}”…", "Couldn't start the scan") { s ->
            container.serverApi.scanLibrary(s.scope, library.key)
        }
    }

    /**
     * Optimistic scan entries, keyed by library section id, each with the number of remaining poll
     * cycles before it is assumed finished. Bridges the gap between tapping Scan and the server
     * reporting the activity, and covers a scan too fast to ever be polled. See [scanLibrary], §5.
     */
    private val optimisticScanCycles = mutableMapOf<String, Int>()

    private fun syntheticScan(key: String): ServerActivity {
        val title = _state.value.libraries.firstOrNull { it.key == key }?.title ?: "Library"
        return ServerActivity(
            type = "library.optimistic",
            title = "Scanning “$title”",
            subtitle = "Starting…",
            progress = 0f,
            librarySectionId = key,
        )
    }

    /**
     * Merges the server's real scan activities with the optimistic ones and publishes the union as
     * the single scan-indicator source. A real activity always wins over its optimistic placeholder;
     * an optimistic entry drops when its grace runs out (the scan finished too fast to be polled).
     */
    private fun publishScanActivities(real: List<ServerActivity>) {
        val realKeys = real.mapNotNull { it.librarySectionId }.toSet()
        val expired = mutableListOf<String>()
        val next = optimisticScanCycles.mapNotNull { (key, cycles) ->
            when {
                key in realKeys -> null   // the real activity is showing now; drop the placeholder
                cycles <= 1 -> { expired += key; null }  // grace elapsed: a too-fast scan finished
                else -> key to (cycles - 1)
            }
        }.toMap()
        optimisticScanCycles.clear()
        optimisticScanCycles.putAll(next)
        // A scan we showed optimistically that the server never reported a lasting activity for has
        // finished near-instantly — a small library with nothing new to index. Tell the viewer it
        // completed, so a non-TV scan gives closure instead of a bar that just disappears (#2).
        expired.forEach { key ->
            val title = _state.value.libraries.firstOrNull { it.key == key }?.title ?: "Library"
            notify("Scan complete — “$title”")
        }
        val synthetic = optimisticScanCycles.keys.map { syntheticScan(it) }
        _state.update { it.copy(scanActivities = real + synthetic) }
    }

    /** Permanently delete an item's media from the server. Irreversible; confirmed in the UI first. */
    fun deleteItem(item: MediaItem) {
        val server = _state.value.server ?: return
        _state.update {
            it.copy(
                continueWatching = it.continueWatching.filterNot { m -> m.ratingKey == item.ratingKey },
                detail = if (it.detail?.item?.ratingKey == item.ratingKey) null else it.detail,
            )
        }
        viewModelScope.launch {
            runCatching { container.serverApi.deleteItem(server.scope, item.ratingKey) }
                .onSuccess { notify("Deleted “${item.title}”") }
                .onFailure { notify("Couldn't delete “${item.title}”") }
            refreshHome(server)
        }
    }

    private fun serverAction(pending: String, failure: String, block: suspend (ActiveServer) -> Unit) {
        val server = _state.value.server ?: return
        notify(pending)
        viewModelScope.launch {
            runCatching { block(server) }.onFailure { notify(failure) }
        }
    }

    /** A short transient message for an action's result, shown as a snackbar. */
    private fun notify(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // --- server updates --------------------------------------------------------------------
    //
    // The server product updating itself, surfaced the way the official app does. Distinct
    // from the client's own update notice (checkForUpdate), which compares this build against
    // the release manifest in CLAUDE.md section 17.

    /**
     * Poll the server's own update state once, so an available update can be surfaced. Folded
     * into the connect path via [applyTarget]. Silent on failure — an unreachable or older
     * server simply reports nothing.
     */
    private fun refreshServerUpdate(server: ActiveServer = requireServer()) {
        viewModelScope.launch {
            val update = container.serverApi.serverUpdateStatus(server.scope)
            // A reachable status also means a server that was restarting has come back.
            _state.update { it.copy(serverUpdate = update, serverUpdateApplying = false) }
        }
    }

    /** Ask the server to check for, and download, an available update, then re-poll status. */
    fun checkServerUpdate() {
        val server = _state.value.server ?: return
        notify("Checking for a server update…")
        viewModelScope.launch {
            runCatching { container.serverApi.checkServerUpdate(server.scope) }
                .onFailure { notify("Couldn't check for a server update") }
            val update = container.serverApi.serverUpdateStatus(server.scope)
            _state.update { it.copy(serverUpdate = update) }
            if (update?.available == true) {
                notify("Server update available: ${update.version ?: "new version"}")
            } else {
                notify("The server is up to date")
            }
        }
    }

    /**
     * Install the available server update and restart the server. Applying drops the
     * connection, so this is fire-and-forget: the flag stays set and the interface tells the
     * viewer the update is under way rather than waiting for a response that may never come.
     */
    fun applyServerUpdate() {
        val server = _state.value.server ?: return
        _state.update { it.copy(serverUpdateApplying = true) }
        notify("Applying the server update. The server will restart…")
        viewModelScope.launch {
            container.serverApi.applyServerUpdate(server.scope)
        }
    }

    // --- library sharing -------------------------------------------------------------------
    //
    // The account model is separate accounts with a shared library (CLAUDE.md section 2). The
    // owner grants another account access to selected libraries; sharing goes through plex.tv
    // with the account token, never the server token.

    /**
     * Grant an account access to selected libraries of the active server by email. Surfaces
     * the result as a notice and refreshes the shared-users list on success.
     */
    fun grantLibraryAccess(email: String, libraryKeys: List<String>) {
        val server = _state.value.server ?: return
        val accountToken = container.session.accountToken()
        if (accountToken == null) {
            notify("Sign in again to share libraries")
            return
        }
        notify("Sharing libraries with $email…")
        viewModelScope.launch {
            val ok = container.tvApi.shareLibraries(
                accountToken = accountToken,
                machineIdentifier = server.machineIdentifier,
                invitedEmail = email,
                librarySectionIds = libraryKeys,
            )
            if (ok) {
                notify("Shared with $email")
                refreshSharedUsers()
            } else {
                notify("Couldn't share with $email")
            }
        }
    }

    /** The accounts the active server is already shared with. Best-effort. */
    fun refreshSharedUsers() {
        val server = _state.value.server ?: return
        val accountToken = container.session.accountToken() ?: return
        viewModelScope.launch {
            val users = container.tvApi.sharedUsers(accountToken, server.machineIdentifier)
            _state.update { it.copy(sharedUsers = users) }
        }
    }

    // --- library order ---------------------------------------------------------------------
    //
    // Plex has no reliable client reorder API, so the order is kept locally and applied on
    // every load. See [SettingsStore.libraryOrder].

    /**
     * Reorder the library cards, persisting the new order so it survives a restart. Reorders
     * the current in-memory list and writes the resulting key order to settings.
     */
    fun moveLibrary(fromIndex: Int, toIndex: Int) {
        val current = _state.value.libraries
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val reordered = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        container.settings.libraryOrder = reordered.map { it.key }
        _state.update { it.copy(libraries = reordered) }
    }

    /**
     * Sort [libraries] by the saved order. Keys present in the saved order lead, in that
     * order; anything unknown (a newly added library) falls to the end in the server's own
     * order rather than disappearing. See [SettingsStore.libraryOrder].
     */
    private fun orderedLibraries(libraries: List<Library>): List<Library> {
        val order = container.settings.libraryOrder
        if (order.isEmpty()) return libraries
        val rank = order.withIndex().associate { (index, key) -> key to index }
        return libraries.sortedBy { rank[it.key] ?: (order.size + libraries.indexOf(it)) }
    }

    // --- player ----------------------------------------------------------------------------

    /**
     * Launch playback of [item] from [startAtMs]. Zero starts from the beginning, which is
     * what the detail screen's "Play from start" uses; a resume position resumes with no
     * prompt. See CLAUDE.md section 2.
     */
    fun play(item: MediaItem, startAtMs: Long) {
        val server = _state.value.server ?: return
        _state.update {
            it.copy(playback = PlaybackRequest(item, server.scope, server.urls, startAtMs))
        }
    }

    /** Leave the player and reconcile Home, so a resume position set while watching shows. */
    fun closePlayer() {
        val wasPlaying = _state.value.playback != null
        _state.update { it.copy(playback = null, isFullScreen = false) }
        if (wasPlaying) _state.value.server?.let { refreshHome(it) }
    }

    /** Windows only: the player's full-screen toggle. The desktop window reads this. */
    fun toggleFullScreen() {
        _state.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    // --- search ----------------------------------------------------------------------------

    /**
     * Requests after 300 ms of no typing, minimum two characters.
     * See CLAUDE.md section 14.
     */
    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query)) }

        searchJob?.cancel()
        if (query.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(search = it.search.copy(results = null, searching = false)) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(search = it.search.copy(searching = true)) }

            val server = _state.value.server ?: return@launch
            val results = runCatching { container.repository.search(server.scope, query) }.getOrNull()

            _state.update { it.copy(search = it.search.copy(results = results, searching = false)) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = SearchState()) }
    }

    // --- downloads --------------------------------------------------------------------

    fun refreshDownloads() {
        val queue = downloads ?: return
        viewModelScope.launch {
            // Rows whose files have gone are dropped first, so the screen never lists an
            // item that will not play.
            offline?.reconcileWithDisk()

            val rows = container.downloadStore?.all().orEmpty()
            val entries = rows.map { row ->
                DownloadEntry(
                    row = row,
                    title = container.repository.cachedItem(row.ratingKey)?.title ?: row.ratingKey,
                )
            }
            _state.update {
                it.copy(
                    downloads = entries,
                    downloadBytesOnDisk = container.downloadStore?.totalBytesOnDisk() ?: 0L,
                )
            }
            queue.start()
        }
    }

    fun download(item: MediaItem) {
        val queue = downloads ?: return
        val server = _state.value.server ?: return
        viewModelScope.launch {
            val detail = runCatching { container.repository.detail(server.scope, item.ratingKey) }.getOrNull()
            val part = detail?.primaryPart ?: return@launch

            // Record the keys before queueing, so the transport never has to invent a path.
            container.downloadKeys.record(part)

            queue.enqueue(
                com.thotapalli.plex.core.download.DownloadRequest(
                    ratingKey = item.ratingKey,
                    partId = part.partId,
                    container = part.container.ifBlank { "mkv" },
                    sizeBytes = part.sizeBytes,
                    // External subtitles download separately; embedded tracks need no action.
                    subtitles = part.subtitleStreams
                        .filter { it.external && it.key != null }
                        .map { stream ->
                            com.thotapalli.plex.core.download.SubtitleRequest(
                                streamId = stream.id,
                                language = stream.languageCode ?: "und",
                                localPath = container.downloadSubtitlePath(
                                    item.ratingKey,
                                    stream.id,
                                    stream.languageCode ?: "und",
                                ),
                            )
                        },
                ),
            )
            refreshDownloads()
        }
    }

    fun pauseDownload(ratingKey: String) {
        viewModelScope.launch { downloads?.pause(ratingKey); refreshDownloads() }
    }

    fun resumeDownload(ratingKey: String) {
        viewModelScope.launch { downloads?.resume(ratingKey); refreshDownloads() }
    }

    fun deleteDownload(ratingKey: String) {
        viewModelScope.launch { downloads?.delete(ratingKey); refreshDownloads() }
    }

    /** Resolve a completed download to its [MediaItem] so the Downloads screen can play it. The
     *  player then plays the local file offline via the resolver (§11, #1). */
    fun playableDownloadItem(entry: DownloadEntry): MediaItem? =
        container.repository.cachedItem(entry.row.ratingKey)

    /**
     * The launch update check. At most once per 24 hours, and it never blocks anything:
     * a failure says nothing at all. See CLAUDE.md section 17 point 4.
     */
    private fun checkForUpdate() {
        val checker = container.updateChecker ?: return
        viewModelScope.launch {
            val result = checker.check()
            if (result is com.thotapalli.plex.core.session.UpdateCheckResult.Available) {
                _state.update { it.copy(availableUpdate = result.update) }
            }
        }
    }

    // --- settings ---------------------------------------------------------------------

    fun settingsState(): SettingsScreenState {
        val settings = container.settings
        return SettingsScreenState(
            matchDisplayRate = settings.matchDisplayRate,
            unmeteredOnly = settings.unmeteredDownloadsOnly,
            audioLanguage = settings.preferredAudioLanguage,
            subtitleLanguage = settings.preferredSubtitleLanguage,
            subtitlesOn = settings.subtitlesOnByDefault,
            streamingMaxBitrateKbps = settings.streamingMaxBitrateKbps,
            subtitleScalePercent = settings.subtitleScalePercent,
            subtitleForegroundArgb = settings.subtitleForegroundArgb,
            subtitleBackgroundOpacityPercent = settings.subtitleBackgroundOpacityPercent,
            servers = _state.value.allServers,
            activeServerId = _state.value.server?.machineIdentifier,
            signedInAs = container.session.accountToken()
                ?.let { _ -> _state.value.accountName ?: _state.value.homeUser?.title },
            updateAvailable = _state.value.availableUpdate?.versionName,
            onDownloadUpdate = { _state.value.availableUpdate?.let { onOpenUrl?.invoke(it.downloadUrl) } },
        )
    }

    fun setMatchDisplayRate(value: Boolean) {
        container.settings.matchDisplayRate = value
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setUnmeteredOnly(value: Boolean) {
        container.settings.unmeteredDownloadsOnly = value
        downloads?.onNetworkChanged()
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setAudioLanguage(code: String) {
        container.settings.preferredAudioLanguage = code
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setSubtitleLanguage(code: String) {
        container.settings.preferredSubtitleLanguage = code
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setSubtitlesOn(value: Boolean) {
        container.settings.subtitlesOnByDefault = value
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setStreamingBitrate(value: Int?) {
        container.settings.streamingMaxBitrateKbps = value
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setSubtitleScale(value: Int) {
        container.settings.subtitleScalePercent = value
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setSubtitleForeground(value: Long) {
        container.settings.subtitleForegroundArgb = value
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun setSubtitleBackgroundOpacity(value: Int) {
        container.settings.subtitleBackgroundOpacityPercent = value
        _state.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    /**
     * Light, dark or follow the system. Persisted to the store and mirrored into state so the
     * setting recomposes; PlexApp reads [AppState.themeMode] to apply the theme.
     */
    fun setThemeMode(mode: ThemeMode) {
        container.settings.themeMode = mode
        _state.update { it.copy(themeMode = mode, settingsRevision = it.settingsRevision + 1) }
    }

    fun selectServer(server: com.thotapalli.plex.core.model.PlexServer) {
        container.session.selectServer(server)
        viewModelScope.launch { loadHome() }
    }

    /**
     * A real OS network transition (Wi-Fi/cellular change, connect/disconnect). Clears the cached
     * connection so the next request re-probes, and — once past sign-in — re-resolves the active
     * server and refreshes Home. Debounced by the platform callers. See CLAUDE.md section 5 point 4.
     */
    fun onNetworkChanged() {
        viewModelScope.launch {
            runCatching { container.session.onNetworkChanged() }
            if (_state.value.phase == AppPhase.READY || _state.value.phase == AppPhase.ERROR) {
                // Re-probe done; now repopulate whatever the viewer is looking at so a dropped-then-
                // restored connection recovers on its own, with no app restart (per request). Home
                // always reloads; the open library and detail reload too so their content returns.
                loadHome()
                _state.value.library?.let { loadLibraryContents(it.library, it.unwatchedOnly) }
                _state.value.detail?.item?.let { openDetail(it) }
            }
            // Signal any active player to retry from its last position if it had stalled/failed.
            _networkRegained.tryEmit(Unit)
        }
    }

    /**
     * Fires when connectivity is (re)gained, so an active player showing a "connection lost" state
     * can retry from where it stopped instead of the viewer restarting the app (per request). A
     * replayless, conflated event — a late collector does not re-trigger an old reconnect.
     */
    private val _networkRegained = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkRegained: SharedFlow<Unit> = _networkRegained.asSharedFlow()

    private fun requireServer(): ActiveServer =
        checkNotNull(_state.value.server) { "no active server" }

    /** Returns a copy of the item with its watched state flipped, for the optimistic update. */
    private fun MediaItem.markedWatched(watched: Boolean): MediaItem {
        val count = if (watched) maxOf(viewCount, 1) else 0
        val offset = if (watched) 0L else viewOffsetMs
        return when (this) {
            is Movie -> copy(viewCount = count, viewOffsetMs = offset)
            is Show -> copy(viewCount = count)
            is Season -> copy(viewCount = count)
            is Episode -> copy(viewCount = count, viewOffsetMs = offset)
            is MediaCollection -> copy(viewCount = count)
        }
    }

    override fun onCleared() {
        container.close()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
        const val HOME_RAIL_LIMIT = 20

        /** How often the live scan-progress poll asks the server for its running jobs. */
        const val SCAN_POLL_INTERVAL_MS = 1500L

        /** Poll cycles an optimistic scan entry survives without a real activity before it is
         *  assumed finished (§5, #3). 8 × 1.5s ≈ 12s of "Scanning…" feedback for a fast scan. */
        const val OPTIMISTIC_SCAN_CYCLES = 8
    }
}

enum class AppPhase { STARTING, SIGNED_OUT, PICKING_HOME_USER, CONNECTING, READY, ERROR }

data class AppState(
    val phase: AppPhase = AppPhase.STARTING,
    val signIn: com.thotapalli.plex.core.session.SignInState? = null,
    val homeUsers: List<HomeUser> = emptyList(),
    val homeUser: HomeUser? = null,
    /** Display name for the profile chip / top bar: the active Home user, or the signed-in account
     *  name when there is no Home user. Null only before sign-in. */
    val accountName: String? = null,
    val server: ActiveServer? = null,
    val libraries: List<Library> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    /** libraryKey -> a short preview of that library's titles, for the Home rails. */
    val libraryPreviews: Map<String, List<MediaItem>> = emptyMap(),
    val library: LibraryState? = null,
    val detail: DetailState? = null,
    /** Non-null while the full-screen player is up. See [AppViewModel.play]. */
    val playback: PlaybackRequest? = null,
    /** Windows only: whether the player is filling the screen. */
    val isFullScreen: Boolean = false,
    val search: SearchState = SearchState(),
    val downloads: List<DownloadEntry> = emptyList(),
    val downloadBytesOnDisk: Long = 0,
    val allServers: List<com.thotapalli.plex.core.model.PlexServer> = emptyList(),
    /** Bumped so a settings change recomposes; the values themselves live in the store. */
    val settingsRevision: Int = 0,
    val availableUpdate: com.thotapalli.plex.core.session.AvailableUpdate? = null,
    /** The server's own pending update, polled once on connect. Null when none or unknown. */
    val serverUpdate: ServerUpdate? = null,
    /** True from the moment [AppViewModel.applyServerUpdate] fires; the server then restarts. */
    val serverUpdateApplying: Boolean = false,
    /** Accounts the active server is shared with, when the sharing screen has loaded them. */
    val sharedUsers: List<SharedUser> = emptyList(),
    /** A short transient message for a server action's result, shown as a snackbar. */
    val notice: String? = null,
    /** Light, dark or follow the system. Applied by PlexApp, not here. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Running library scans on the server, refreshed by the live poll for progress display. */
    val scanActivities: List<ServerActivity> = emptyList(),
    val error: String? = null,
)

data class LibraryState(
    val library: Library,
    val items: List<MediaItem> = emptyList(),
    val collections: List<MediaCollection> = emptyList(),
    val openCollection: MediaCollection? = null,
    val unwatchedOnly: Boolean = false,
    /** Plex sort string, e.g. "titleSort:asc" (#16). */
    val sort: String = "titleSort:asc",
    /** Genres offered in the filter sheet; empty hides the genre chooser. */
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val loading: Boolean = false,
)

data class DetailState(
    val item: MediaItem,
    val detail: MediaDetail? = null,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Season? = null,
    val nextUnwatched: Episode? = null,
    /** The episode the viewer tapped to inspect; its summary is shown, and Play plays it. */
    val selectedEpisode: Episode? = null,
    val loading: Boolean = false,
) {
    val episodesInSelectedSeason: List<Episode>
        get() = selectedSeason?.let { season ->
            episodes.filter { it.seasonRatingKey == season.ratingKey }
        } ?: episodes
}

data class SearchState(
    val query: String = "",
    val results: SearchResults? = null,
    val searching: Boolean = false,
)

/** A request to play one item from a position, carrying the server it belongs to. */
data class PlaybackRequest(
    val item: MediaItem,
    val serverScope: ServerScope,
    val urls: PlexUrls,
    val startAtMs: Long,
)
