package io.github.anthodingo.onyxfilter.data

import io.github.anthodingo.onyxfilter.domain.DisableDuration
import io.github.anthodingo.onyxfilter.domain.DnsStats
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.ZonedDateTime

/** État de connexion de l'application. */
sealed interface AuthState {
    /** Lecture de la session enregistrée en cours (démarrage de l'application). */
    data object Restoring : AuthState

    /** @property tokenRejected la session a pris fin parce que le serveur a refusé le jeton (révoqué ?). */
    data class LoggedOut(val tokenRejected: Boolean = false) : AuthState

    data class LoggedIn(val session: Session) : AuthState
}

/**
 * Point d'entrée unique de l'interface, des widgets et de la tuile vers l'instance OnyxFilter :
 * connexion, conservation de la session et derniers états lus.
 */
class OnyxFilterRepository(
    private val api: OnyxFilterApi,
    private val sessionStore: SessionStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _protectionStatus = MutableStateFlow<ProtectionStatus?>(null)

    /**
     * Dernier état de la protection obtenu du serveur pendant cette session (quel que soit l'écran ou
     * le widget à l'origine de l'appel), `null` avant le premier appel et après une déconnexion.
     */
    val protectionStatus: StateFlow<ProtectionStatus?> = _protectionStatus.asStateFlow()

    private val _stats = MutableStateFlow<DnsStats?>(null)

    /** Dernières statistiques obtenues pendant cette session, comme [protectionStatus]. */
    val stats: StateFlow<DnsStats?> = _stats.asStateFlow()

    // Garde la cohérence entre la session enregistrée et [authState] (connexion et déconnexion
    // concurrentes). Jamais tenu pendant un appel réseau.
    private val sessionMutex = Mutex()

    private val currentSession: Session?
        get() = (_authState.value as? AuthState.LoggedIn)?.session

    /** Recharge la session enregistrée ; sans effet si elle a déjà été chargée. */
    suspend fun restoreSession() {
        if (_authState.value != AuthState.Restoring) return
        val stored = withContext(ioDispatcher) { sessionStore.load() }
        _authState.compareAndSet(
            AuthState.Restoring,
            if (stored != null) AuthState.LoggedIn(stored) else AuthState.LoggedOut(),
        )
    }

    suspend fun loginHint(): LoginHint? = withContext(ioDispatcher) { sessionStore.loginHint() }

    /**
     * Vérifie le jeton (lecture de l'état de la protection) puis ouvre la session.
     *
     * @param serverUrl adresse déjà normalisée par [ServerUrl.normalize].
     * @throws OnyxFilterException
     */
    suspend fun login(serverUrl: String, apiToken: String): Session {
        val dto = api.getProtection(serverUrl, apiToken)
        val session = Session(serverUrl, apiToken)
        sessionMutex.withLock {
            withContext(ioDispatcher) { sessionStore.save(session) }
            _stats.value = null
            _protectionStatus.value = ProtectionStatus.fromDto(dto, clock.instant())
            _authState.value = AuthState.LoggedIn(session)
        }
        return session
    }

    suspend fun logout() {
        endSession(tokenRejected = false)
    }

    /** @throws OnyxFilterException */
    suspend fun getProtection(): ProtectionStatus =
        protectionCall { session -> api.getProtection(session.serverUrl, session.apiToken) }

    /** @throws OnyxFilterException */
    suspend fun enableProtection(): ProtectionStatus =
        protectionCall { session -> api.enableProtection(session.serverUrl, session.apiToken) }

    /** @throws OnyxFilterException */
    suspend fun disableProtection(duration: DisableDuration): ProtectionStatus {
        val seconds = duration.toSeconds(ZonedDateTime.now(clock))
        return protectionCall { session -> api.disableProtection(session.serverUrl, session.apiToken, seconds) }
    }

    /** @throws OnyxFilterException */
    suspend fun getStats(): DnsStats {
        val (session, dto) = authenticated { session -> api.getStats(session.serverUrl, session.apiToken) }
        val stats = DnsStats.fromDto(dto)
        publishIfCurrent(session) { _stats.value = stats }
        return stats
    }

    private suspend fun protectionCall(call: suspend (Session) -> ProtectionStateDto): ProtectionStatus {
        val (session, dto) = authenticated(call)
        val status = ProtectionStatus.fromDto(dto, clock.instant())
        publishIfCurrent(session) { _protectionStatus.value = status }
        return status
    }

    // Jeton refusé (révoqué depuis la page « Accès API ») : la session est fermée et l'application
    // revient à l'écran de connexion.
    private suspend fun <T> authenticated(call: suspend (Session) -> T): Pair<Session, T> {
        val session = currentSession ?: throw OnyxFilterException.SessionEnded()
        return try {
            session to call(session)
        } catch (e: OnyxFilterException.Unauthorized) {
            endSession(tokenRejected = true, onlyIfCurrent = session)
            throw OnyxFilterException.SessionEnded()
        }
    }

    // Réponse arrivée après une déconnexion (ou une autre connexion) : elle ne vaut plus rien.
    private inline fun publishIfCurrent(session: Session, publish: () -> Unit) {
        if (currentSession == session) publish()
    }

    // onlyIfCurrent : ne ferme la session que si elle n'a pas changé entre-temps (déconnexion ou
    // nouvelle connexion de l'utilisateur pendant un appel réseau).
    private suspend fun endSession(tokenRejected: Boolean, onlyIfCurrent: Session? = null) {
        sessionMutex.withLock {
            if (onlyIfCurrent != null && currentSession != onlyIfCurrent) return
            withContext(ioDispatcher) { sessionStore.clear() }
            _protectionStatus.value = null
            _stats.value = null
            _authState.value = AuthState.LoggedOut(tokenRejected)
        }
    }
}
