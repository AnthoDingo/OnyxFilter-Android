package io.github.anthodingo.onyxfilter.ui.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.domain.DisableDuration
import io.github.anthodingo.onyxfilter.domain.DnsStats
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock

data class ProtectionUiState(
    /** Dernier état connu, `null` tant que le premier chargement n'a pas abouti. */
    val status: ProtectionStatus? = null,
    /** Actualisation demandée par l'utilisateur (ou premier chargement) en cours. */
    val isRefreshing: Boolean = true,
    /** Activation ou désactivation en cours. */
    val isUpdating: Boolean = false,
    /** Échec de la dernière actualisation : le [status] affiché n'est peut-être plus à jour. */
    val error: UiText? = null,
)

class ProtectionViewModel(
    private val repository: OnyxFilterRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProtectionUiState())
    val uiState: StateFlow<ProtectionUiState> = _uiState.asStateFlow()

    // Erreurs des actions de l'utilisateur, affichées une seule fois (Snackbar).
    private val _actionErrors = Channel<UiText>(Channel.BUFFERED)
    val actionErrors: Flow<UiText> = _actionErrors.receiveAsFlow()

    /** Statistiques des dernières 24 heures (partagées avec les widgets par le dépôt). */
    val stats: StateFlow<DnsStats?> = repository.stats

    private var pollingJob: Job? = null
    private var lastStatsLoadMillis: Long? = null

    /**
     * Actualise l'état régulièrement tant que l'écran est visible, pour refléter les changements faits
     * ailleurs (interface web) et la réactivation automatique en fin de désactivation temporaire.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                load()
                delay(nextPollDelayMillis())
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { load(forceStats = true) }
    }

    fun enable() = runAction { repository.enableProtection() }

    fun disable(duration: DisableDuration) = runAction { repository.disableProtection(duration) }

    fun logout() {
        stopPolling()
        viewModelScope.launch { repository.logout() }
    }

    private suspend fun load(forceStats: Boolean = false) {
        try {
            val status = repository.getProtection()
            _uiState.update { it.copy(status = status, isRefreshing = false, error = null) }
        } catch (e: OnyxFilterException) {
            // Session expirée : le dépôt ramène déjà l'application à l'écran de connexion.
            val error = if (e is OnyxFilterException.SessionEnded) null else e.toUiText()
            _uiState.update { it.copy(isRefreshing = false, error = error ?: it.error) }
            return
        }
        loadStatsIfStale(forceStats)
    }

    // Les statistiques évoluent lentement : relues au plus toutes les STATS_INTERVAL_MILLIS. Un échec
    // reste silencieux, l'état de la protection signale déjà les problèmes de connexion.
    private suspend fun loadStatsIfStale(force: Boolean) {
        val now = clock.millis()
        val last = lastStatsLoadMillis
        if (!force && last != null && now - last < STATS_INTERVAL_MILLIS) return
        try {
            repository.getStats()
            lastStatsLoadMillis = now
        } catch (e: OnyxFilterException) {
            // Voir ci-dessus.
        }
    }

    private fun runAction(action: suspend () -> ProtectionStatus) {
        if (_uiState.value.isUpdating) return
        _uiState.update { it.copy(isUpdating = true) }

        viewModelScope.launch {
            try {
                val status = action()
                _uiState.update { it.copy(status = status, isUpdating = false, error = null) }
            } catch (e: OnyxFilterException) {
                _uiState.update { it.copy(isUpdating = false) }
                if (e !is OnyxFilterException.SessionEnded) {
                    _actionErrors.send(e.toUiText())
                }
            }
        }
    }

    // Toutes les POLL_INTERVAL_MILLIS, ou juste après la fin prévue d'une désactivation temporaire.
    private fun nextPollDelayMillis(): Long {
        val status = _uiState.value.status
        if (status == null || !status.isDisabledTemporarily) return POLL_INTERVAL_MILLIS

        val untilReEnabled = status.remainingSeconds(clock.instant()) * 1000 + RE_ENABLE_GRACE_MILLIS
        return untilReEnabled.coerceIn(RE_ENABLE_GRACE_MILLIS, POLL_INTERVAL_MILLIS)
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 15_000L
        private const val STATS_INTERVAL_MILLIS = 60_000L

        // Laisse au serveur le temps de réactiver la protection avant de relire son état.
        private const val RE_ENABLE_GRACE_MILLIS = 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProtectionViewModel((this[APPLICATION_KEY] as OnyxFilterApplication).repository)
            }
        }
    }
}
