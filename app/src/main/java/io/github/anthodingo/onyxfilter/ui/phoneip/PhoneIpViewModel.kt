package io.github.anthodingo.onyxfilter.ui.phoneip

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
import io.github.anthodingo.onyxfilter.domain.PhoneIpAccess
import io.github.anthodingo.onyxfilter.phoneip.PhoneIpAutoAllow
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

data class PhoneIpUiState(
    /** Dernière adresse lue, `null` tant que la première lecture n'a pas abouti. */
    val access: PhoneIpAccess? = null,
    val autoAllow: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Ajout de l'adresse aux clients autorisés en cours. */
    val isAdding: Boolean = false,
    /** Échec de la dernière lecture : l'adresse affichée n'est peut-être plus à jour. */
    val error: UiText? = null,
)

/** Adresse publique du téléphone et son autorisation sur le DNS. */
class PhoneIpViewModel(
    private val repository: OnyxFilterRepository,
    private val autoAllow: PhoneIpAutoAllow,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneIpUiState(autoAllow = autoAllow.isEnabled))
    val uiState: StateFlow<PhoneIpUiState> = _uiState.asStateFlow()

    // Résultats et erreurs des ajouts, affichés une seule fois (Snackbar).
    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    // Relue à chaque affichage : l'adresse change avec le réseau.
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { load() }
    }

    fun setAutoAllow(enabled: Boolean) {
        autoAllow.isEnabled = enabled
        _uiState.update { it.copy(autoAllow = enabled) }
        if (enabled && _uiState.value.access?.allowed == false) add()
    }

    /** Ajoute l'adresse affichée aux clients autorisés, même si le DNS la sert déjà. */
    fun add() {
        val ip = _uiState.value.access?.ip ?: return
        if (_uiState.value.isAdding) return
        _uiState.update { it.copy(isAdding = true) }
        viewModelScope.launch {
            val message = try {
                val changed = repository.allowClient(ip)
                UiText.Resource(if (changed) R.string.phone_ip_added else R.string.phone_ip_already_allowed, listOf(ip))
            } catch (e: OnyxFilterException) {
                if (e is OnyxFilterException.SessionEnded) null else e.toUiText()
            }
            _uiState.update { it.copy(isAdding = false) }
            message?.let { _messages.send(it) }
            load()
        }
    }

    private suspend fun load() {
        try {
            val access = repository.getPhoneIpAccess()
            _uiState.update { it.copy(access = access, isRefreshing = false, error = null) }
        } catch (e: OnyxFilterException) {
            // Session expirée : le dépôt ramène déjà l'application à l'écran de connexion.
            val error = if (e is OnyxFilterException.SessionEnded) null else e.toUiText()
            _uiState.update { it.copy(isRefreshing = false, error = error) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as OnyxFilterApplication
                PhoneIpViewModel(application.repository, application.phoneIpAutoAllow)
            }
        }
    }
}
