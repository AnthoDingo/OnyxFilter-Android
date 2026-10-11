package io.github.anthodingo.onyxfilter.ui.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.domain.ClientAccess
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedClientsUiState(
    /** Dernières listes lues, `null` tant que la première lecture n'a pas abouti. */
    val access: ClientAccess? = null,
    /** Clients en cours de déblocage, déjà retirés de la liste affichée. */
    val unblocking: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    /** Échec de la dernière lecture : la liste affichée n'est peut-être plus à jour. */
    val error: UiText? = null,
) {
    val blockedClients: List<String> get() = access?.blockedClients.orEmpty().filterNot { it in unblocking }
}

/** Clients refusés par le DNS, débloqués d'un glissement. */
class BlockedClientsViewModel(private val repository: OnyxFilterRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedClientsUiState())
    val uiState: StateFlow<BlockedClientsUiState> = _uiState.asStateFlow()

    // Résultats et erreurs des déblocages, affichés une seule fois (Snackbar).
    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    // Relue à chaque affichage : un client a pu être bloqué entre-temps (page Protection, interface web).
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val access = repository.getClientAccess()
                _uiState.update { it.copy(access = access, isRefreshing = false, error = null) }
            } catch (e: OnyxFilterException) {
                // Session expirée : le dépôt ramène déjà l'application à l'écran de connexion.
                val error = if (e is OnyxFilterException.SessionEnded) null else e.toUiText()
                _uiState.update { it.copy(isRefreshing = false, error = error) }
            }
        }
    }

    fun unblock(client: String) {
        if (client in _uiState.value.unblocking) return
        _uiState.update { it.copy(unblocking = it.unblocking + client) }
        viewModelScope.launch {
            val message = try {
                val access = repository.unblockClient(client)
                _uiState.update { it.copy(access = access, unblocking = it.unblocking - client) }
                UiText.Resource(R.string.client_unblocked, listOf(client))
            } catch (e: OnyxFilterException) {
                // Le client revient dans la liste.
                _uiState.update { it.copy(unblocking = it.unblocking - client) }
                if (e is OnyxFilterException.SessionEnded) return@launch
                e.toUiText()
            }
            _messages.send(message)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BlockedClientsViewModel((this[APPLICATION_KEY] as OnyxFilterApplication).repository)
            }
        }
    }
}
