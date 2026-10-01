package io.github.anthodingo.onyxfilter.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.data.AuthState
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.data.PairingLink
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Étape de la connexion par QR code (lien `onyxfilter://pair`). */
sealed interface PairingState {
    data object Idle : PairingState

    /** Une session est déjà ouverte : l'utilisateur doit accepter de la remplacer. */
    data class ConfirmReplace(val link: PairingLink, val currentServerUrl: String) : PairingState

    data class Connecting(val link: PairingLink) : PairingState

    /** Lien illisible, jeton expiré ou déjà utilisé, serveur injoignable... : rescanner un code. */
    data class Failed(val error: UiText) : PairingState
}

class PairingViewModel(private val repository: OnyxFilterRepository) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    /** Lien reçu par l'activité (`data` d'une intention VIEW). */
    fun onLink(link: String?) {
        val pairing = PairingLink.parse(link)
        if (pairing == null) {
            _state.value = PairingState.Failed(UiText.Resource(R.string.pairing_invalid_link))
            return
        }
        viewModelScope.launch {
            // Au démarrage à froid, la session enregistrée n'est peut-être pas encore relue.
            val authState = repository.authState.first { it != AuthState.Restoring }
            if (authState is AuthState.LoggedIn) {
                _state.value = PairingState.ConfirmReplace(pairing, authState.session.serverUrl)
            } else {
                connect(pairing)
            }
        }
    }

    fun confirmReplace() {
        (_state.value as? PairingState.ConfirmReplace)?.let { connect(it.link) }
    }

    fun dismiss() {
        if (_state.value !is PairingState.Connecting) _state.value = PairingState.Idle
    }

    private fun connect(link: PairingLink) {
        _state.value = PairingState.Connecting(link)
        viewModelScope.launch {
            // En cas d'échec, la session en cours (s'il y en a une) reste ouverte.
            _state.value = try {
                repository.login(link.serverUrl, link.apiToken)
                PairingState.Idle
            } catch (e: OnyxFilterException.Unauthorized) {
                PairingState.Failed(UiText.Resource(R.string.pairing_token_expired))
            } catch (e: OnyxFilterException) {
                PairingState.Failed(e.toUiText())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PairingViewModel((this[APPLICATION_KEY] as OnyxFilterApplication).repository)
            }
        }
    }
}
