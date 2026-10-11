package io.github.anthodingo.onyxfilter.ui.querylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.domain.QueryLogEntry
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class QueryLogUiState(
    /** Texte de la recherche (domaine ou client), tel que saisi. */
    val search: String = "",
    /** Requêtes chargées, des plus récentes aux plus anciennes. */
    val entries: List<QueryLogEntry> = emptyList(),
    /** D'autres requêtes, plus anciennes, restent à charger. */
    val hasMore: Boolean = false,
    /** La première page de la recherche en cours a été lue au moins une fois. */
    val isLoaded: Boolean = false,
    /** Lecture de la première page en cours (premier affichage, actualisation ou recherche). */
    val isRefreshing: Boolean = false,
    /** Lecture de la page suivante en cours. */
    val isLoadingMore: Boolean = false,
    /** Échec de la lecture de la première page. */
    val error: UiText? = null,
    /** Échec de la lecture de la page suivante. */
    val loadMoreError: UiText? = null,
)

/** Journal des requêtes DNS des clients, lu page par page à mesure du défilement. */
class QueryLogViewModel(
    private val repository: OnyxFilterRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueryLogUiState())
    val uiState: StateFlow<QueryLogUiState> = _uiState.asStateFlow()

    // Lecture en cours (première page ou suivante) : une nouvelle recherche ou une actualisation
    // l'annule, sa réponse ne correspondrait plus à la liste affichée.
    private var loadJob: Job? = null
    private var lastRefreshMillis: Long? = null

    // Recherche dont les résultats sont affichés.
    private var loadedSearch: String? = null

    /** Page affichée : recharge le journal s'il n'a jamais été lu ou date de plus de [STALE_AFTER_MILLIS]. */
    fun onShown() {
        val last = lastRefreshMillis
        if (last == null || clock.millis() - last >= STALE_AFTER_MILLIS) refresh()
    }

    fun refresh() {
        loadFirstPage(debounce = false)
    }

    fun onSearchChange(search: String) {
        if (search == _uiState.value.search) return
        _uiState.update { it.copy(search = search) }
        loadFirstPage(debounce = true)
    }

    /** Charge la page suivante, si elle existe et qu'aucune lecture n'est en cours. */
    fun loadMore() {
        val state = _uiState.value
        if (!state.isLoaded || !state.hasMore || loadJob?.isActive == true) return
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

        loadJob = viewModelScope.launch {
            try {
                val page = repository.getQueryLog(offset = state.entries.size, limit = PAGE_SIZE, search = state.search.searchOrNull())
                _uiState.update { it.copy(entries = it.entries + page.entries, hasMore = page.hasMore, isLoadingMore = false) }
            } catch (e: OnyxFilterException) {
                // Session expirée : le dépôt ramène déjà l'application à l'écran de connexion.
                val error = if (e is OnyxFilterException.SessionEnded) null else e.toUiText()
                _uiState.update { it.copy(isLoadingMore = false, loadMoreError = error) }
            }
        }
    }

    private fun loadFirstPage(debounce: Boolean) {
        loadJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, loadMoreError = null) }

        loadJob = viewModelScope.launch {
            // Une seule requête une fois la saisie terminée, plutôt qu'une par caractère.
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            val search = _uiState.value.search
            try {
                val page = repository.getQueryLog(offset = 0, limit = PAGE_SIZE, search = search.searchOrNull())
                lastRefreshMillis = clock.millis()
                loadedSearch = search
                _uiState.update {
                    it.copy(entries = page.entries, hasMore = page.hasMore, isLoaded = true, isRefreshing = false, error = null)
                }
            } catch (e: OnyxFilterException) {
                val error = if (e is OnyxFilterException.SessionEnded) null else e.toUiText()
                // Les requêtes affichées ne correspondent plus à la recherche saisie : elles sont retirées.
                val stale = search != loadedSearch
                _uiState.update {
                    if (stale) {
                        it.copy(entries = emptyList(), hasMore = false, isLoaded = false, isRefreshing = false, error = error)
                    } else {
                        it.copy(isRefreshing = false, error = error)
                    }
                }
            }
        }
    }

    private fun String.searchOrNull(): String? = trim().ifEmpty { null }

    companion object {
        const val PAGE_SIZE = 50
        private const val SEARCH_DEBOUNCE_MILLIS = 400L
        private const val STALE_AFTER_MILLIS = 30_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                QueryLogViewModel((this[APPLICATION_KEY] as OnyxFilterApplication).repository)
            }
        }
    }
}
