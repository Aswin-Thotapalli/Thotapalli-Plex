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
        _state.update { it.copy(server = active, phase = AppPhase.READY) }

        refreshHome(active)
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
