package io.github.anthodingo.onyxfilter.data

import io.github.anthodingo.onyxfilter.domain.DisableDuration
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

    data class LoggedOut(val sessionExpired: Boolean = false) : AuthState

    data class LoggedIn(val session: Session) : AuthState
}

/**
 * Point d'entrée unique de l'interface vers l'instance OnyxFilter : connexion, conservation de la
 * session et rafraîchissement transparent du jeton d'accès.
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

    // Évite plusieurs rafraîchissements simultanés du même jeton (appel réseau).
    private val refreshMutex = Mutex()

    // Garde la cohérence entre la session enregistrée et [authState] (connexion, rafraîchissement et
    // déconnexion concurrents). Jamais tenu pendant un appel réseau.
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
     * @param serverUrl adresse déjà normalisée par [ServerUrl.normalize].
     * @throws OnyxFilterException
     */
    suspend fun login(serverUrl: String, username: String, password: String): Session {
        val tokens = api.login(serverUrl, username, password)
        val session = Session.create(serverUrl, username, tokens, clock.millis())
        sessionMutex.withLock {
            withContext(ioDispatcher) { sessionStore.save(session) }
            _protectionStatus.value = null
            _authState.value = AuthState.LoggedIn(session)
        }
        return session
    }

    suspend fun logout() {
        endSession(sessionExpired = false)
    }

    /** @throws OnyxFilterException */
    suspend fun getProtection(): ProtectionStatus =
        authenticated { session -> api.getProtection(session.serverUrl, session.accessToken) }

    /** @throws OnyxFilterException */
    suspend fun enableProtection(): ProtectionStatus =
        authenticated { session -> api.setProtection(session.serverUrl, session.accessToken, enabled = true, durationSeconds = null) }

    /** @throws OnyxFilterException */
    suspend fun disableProtection(duration: DisableDuration): ProtectionStatus {
        val seconds = duration.toSeconds(ZonedDateTime.now(clock))
        return authenticated { session ->
            api.setProtection(session.serverUrl, session.accessToken, enabled = false, durationSeconds = seconds)
        }
    }

    // Exécute un appel authentifié : le jeton d'accès est rafraîchi avant l'appel s'il a expiré, ou
    // après un refus du serveur (jeton révoqué, serveur redémarré avec de nouvelles clés...), puis
    // l'appel est rejoué une fois. Si le rafraîchissement échoue lui aussi, la session est fermée.
    private suspend fun authenticated(call: suspend (Session) -> ProtectionStateDto): ProtectionStatus {
        var session = currentSession ?: throw OnyxFilterException.SessionExpired()
        if (session.isAccessTokenExpired(clock.millis())) {
            session = refreshTokens(session)
        }

        val dto = try {
            call(session)
        } catch (e: OnyxFilterException.Unauthorized) {
            val refreshed = refreshTokens(session)
            try {
                call(refreshed)
            } catch (retryFailure: OnyxFilterException.Unauthorized) {
                // Jeton tout juste renouvelé et pourtant refusé : inutile d'insister.
                endSession(sessionExpired = true, onlyIfCurrent = refreshed)
                throw OnyxFilterException.SessionExpired()
            }
        }
        val status = ProtectionStatus.fromDto(dto, clock.instant())
        // Réponse arrivée après une déconnexion (ou une autre connexion) : elle ne vaut plus rien.
        if (currentSession?.serverUrl == session.serverUrl) {
            _protectionStatus.value = status
        }
        return status
    }

    private suspend fun refreshTokens(stale: Session): Session = refreshMutex.withLock {
        val current = currentSession ?: throw OnyxFilterException.SessionExpired()

        // Un autre appel a déjà rafraîchi les jetons pendant l'attente du verrou.
        if (current.accessToken != stale.accessToken) return current

        val tokens = try {
            api.refresh(current.serverUrl, current.refreshToken)
        } catch (e: OnyxFilterException.SessionExpired) {
            endSession(sessionExpired = true, onlyIfCurrent = current)
            throw e
        }

        val refreshed = current.withTokens(tokens, clock.millis())
        sessionMutex.withLock {
            // Déconnexion pendant le rafraîchissement : la session ne doit pas être rouverte.
            if (currentSession != current) throw OnyxFilterException.SessionExpired()
            withContext(ioDispatcher) { sessionStore.save(refreshed) }
            _authState.value = AuthState.LoggedIn(refreshed)
        }
        refreshed
    }

    // onlyIfCurrent : ne ferme la session que si elle n'a pas changé entre-temps (déconnexion ou
    // nouvelle connexion de l'utilisateur pendant un appel réseau).
    private suspend fun endSession(sessionExpired: Boolean, onlyIfCurrent: Session? = null) {
        sessionMutex.withLock {
            if (onlyIfCurrent != null && currentSession != onlyIfCurrent) return
            withContext(ioDispatcher) { sessionStore.clear() }
            _protectionStatus.value = null
            _authState.value = AuthState.LoggedOut(sessionExpired)
        }
    }
}
