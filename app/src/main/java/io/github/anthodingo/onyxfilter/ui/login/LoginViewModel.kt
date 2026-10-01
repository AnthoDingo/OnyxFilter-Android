package io.github.anthodingo.onyxfilter.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.anthodingo.onyxfilter.data.PairingLink
import io.github.anthodingo.onyxfilter.data.ServerUrl
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.toUiText
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val apiToken: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
) {
    /** L'adresse saisie désigne une connexion HTTP non chiffrée. */
    val isCleartext: Boolean
        get() = ServerUrl.normalize(serverUrl)?.let(ServerUrl::isCleartext) == true

    /** Le jeton saisi n'a pas la forme de ceux d'OnyxFilter (souvent une erreur de copier-coller). */
    val tokenLooksWrong: Boolean
        get() = apiToken.isNotBlank() && !apiToken.trim().startsWith(TOKEN_PREFIX)

    val canSubmit: Boolean
        get() = !isLoading && serverUrl.isNotBlank() && apiToken.isNotBlank()

    companion object {
        const val TOKEN_PREFIX = PairingLink.TOKEN_PREFIX
    }
}

class LoginViewModel(private val repository: OnyxFilterRepository) : ViewModel() {

    // État Compose plutôt que StateFlow : recommandé pour les champs de saisie (pas de décalage du
    // curseur entre la frappe et la mise à jour de la valeur).
    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        viewModelScope.launch {
            val hint = repository.loginHint() ?: return@launch
            uiState = uiState.copy(serverUrl = uiState.serverUrl.ifEmpty { hint.serverUrl })
        }
    }

    fun onServerUrlChange(value: String) {
        uiState = uiState.copy(serverUrl = value, error = null)
    }

    fun onApiTokenChange(value: String) {
        uiState = uiState.copy(apiToken = value, error = null)
    }

    fun login() {
        val state = uiState
        if (!state.canSubmit) return

        val serverUrl = ServerUrl.normalize(state.serverUrl)
        if (serverUrl == null) {
            uiState = state.copy(error = UiText.Resource(R.string.error_invalid_server_url))
            return
        }

        uiState = state.copy(serverUrl = serverUrl, isLoading = true, error = null)
        viewModelScope.launch {
            uiState = try {
                // Un jeton collé traîne souvent une espace ou un retour à la ligne.
                repository.login(serverUrl, state.apiToken.trim())
                // L'écran de la protection remplace celui-ci ; le formulaire est prêt pour une
                // prochaine connexion (après une déconnexion), sans le jeton.
                uiState.copy(apiToken = "", isLoading = false)
            } catch (e: OnyxFilterException) {
                uiState.copy(isLoading = false, error = e.toUiText())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel((this[APPLICATION_KEY] as OnyxFilterApplication).repository)
            }
        }
    }
}
