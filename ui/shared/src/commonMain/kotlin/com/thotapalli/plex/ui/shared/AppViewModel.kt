package com.thotapalli.plex.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thotapalli.plex.core.api.PlexUrls
import com.thotapalli.plex.core.api.SearchResults
import com.thotapalli.plex.core.api.ServerScope
import com.thotapalli.plex.core.model.Episode
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.model.Library
import com.thotapalli.plex.core.model.MediaCollection
import com.thotapalli.plex.core.model.MediaDetail
import com.thotapalli.plex.core.model.MediaItem
import com.thotapalli.plex.core.model.Season
import com.thotapalli.plex.core.model.Show
import com.thotapalli.plex.core.download.DownloadQueue
import com.thotapalli.plex.core.download.OfflineResolver
import com.thotapalli.plex.ui.shared.screens.DownloadEntry
import com.thotapalli.plex.ui.shared.screens.SettingsScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var searchJob: Job? = null

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
            connect()
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
        container.session.signOut()
        _state.value = AppState(phase = AppPhase.SIGNED_OUT)
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

        loadHome()
    }

    fun selectHomeUser(user: HomeUser, pin: String? = null) {
        viewModelScope.launch {
            runCatching { container.session.switchHomeUser(user, pin) }
                .onSuccess {
                    _state.update { it.copy(homeUser = user) }
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

        val active = ActiveServer(
            name = target.server.name,
            machineIdentifier = target.server.machineIdentifier,
            scope = ServerScope(target.baseUri, target.accessToken),
            urls = PlexUrls(target.baseUri, target.accessToken),
        )
        container.bindDownloadServer(active.scope)
        val servers = runCatching { container.session.servers() }.getOrDefault(emptyList())
        _state.update { it.copy(server = active, allServers = servers, phase = AppPhase.READY) }

        refreshHome(active)
        refreshDownloads()
        checkForUpdate()
    }

    fun refreshHome(server: ActiveServer = requireServer()) {
        viewModelScope.launch {
            val libraries = runCatching {
                container.repository.libraries(server.scope, server.machineIdentifier)
            }.getOrDefault(emptyList())

            val continueWatching = runCatching {
                container.repository.continueWatching(server.scope)
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(libraries = libraries, continueWatching = continueWatching)
            }
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
        loadLibraryContents(current.library, unwatchedOnly)
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

            if (item is Show) {
                val seasons = runCatching { container.repository.seasons(server.scope, item) }
                    .getOrDefault(emptyList())
                val episodes = runCatching { container.repository.episodes(server.scope, item) }
                    .getOrDefault(emptyList())
                val next = runCatching { container.repository.nextUnwatchedEpisode(server.scope, item) }
                    .getOrNull()

                _state.update {
                    it.copy(
                        detail = DetailState(
                            item = item,
                            detail = detail,
                            seasons = seasons,
                            episodes = episodes,
                            selectedSeason = seasons.firstOrNull { season ->
                                season.ratingKey == next?.seasonRatingKey
                            } ?: seasons.firstOrNull(),
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

    fun selectSeason(season: Season) {
        _state.update { it.copy(detail = it.detail?.copy(selectedSeason = season)) }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
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
            servers = _state.value.allServers,
            activeServerId = _state.value.server?.machineIdentifier,
            signedInAs = container.session.accountToken()?.let { _ -> _state.value.homeUser?.title },
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

    fun selectServer(server: com.thotapalli.plex.core.model.PlexServer) {
        container.session.selectServer(server)
        viewModelScope.launch { loadHome() }
    }

    private fun requireServer(): ActiveServer =
        checkNotNull(_state.value.server) { "no active server" }

    override fun onCleared() {
        container.close()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }
}

enum class AppPhase { STARTING, SIGNED_OUT, PICKING_HOME_USER, CONNECTING, READY, ERROR }

data class AppState(
    val phase: AppPhase = AppPhase.STARTING,
    val signIn: com.thotapalli.plex.core.session.SignInState? = null,
    val homeUsers: List<HomeUser> = emptyList(),
    val homeUser: HomeUser? = null,
    val server: ActiveServer? = null,
    val libraries: List<Library> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    val library: LibraryState? = null,
    val detail: DetailState? = null,
    val search: SearchState = SearchState(),
    val downloads: List<DownloadEntry> = emptyList(),
    val downloadBytesOnDisk: Long = 0,
    val allServers: List<com.thotapalli.plex.core.model.PlexServer> = emptyList(),
    /** Bumped so a settings change recomposes; the values themselves live in the store. */
    val settingsRevision: Int = 0,
    val availableUpdate: com.thotapalli.plex.core.session.AvailableUpdate? = null,
    val error: String? = null,
)

data class LibraryState(
    val library: Library,
    val items: List<MediaItem> = emptyList(),
    val collections: List<MediaCollection> = emptyList(),
    val openCollection: MediaCollection? = null,
    val unwatchedOnly: Boolean = false,
    val loading: Boolean = false,
)

data class DetailState(
    val item: MediaItem,
    val detail: MediaDetail? = null,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Season? = null,
    val nextUnwatched: Episode? = null,
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
