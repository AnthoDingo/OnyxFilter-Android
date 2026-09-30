package io.github.anthodingo.onyxfilter.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.os.SystemClock
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.data.AuthState
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * État partagé des raccourcis hors de l'application (widgets de l'écran d'accueil et tuile des
 * réglages rapides) et exécution de leurs actions. Tout se passe sur le thread principal : les appels
 * au serveur sont suspendus, pas bloquants.
 */
internal object WidgetController {

    // Un BroadcastReceiver (goAsync) doit se terminer en quelques secondes : au-delà, les raccourcis
    // affichent "serveur injoignable" et l'appel HTTP est annulé.
    private const val TIMEOUT_MILLIS = 9_000L

    // Ouvrir le volet des réglages rapides relit l'état, au plus une fois par intervalle.
    private const val REFRESH_THROTTLE_MILLIS = 30_000L

    private val scope = MainScope()
    private val _state = MutableStateFlow<WidgetModel?>(null)
    private var refreshJob: Job? = null
    private var lastRefreshElapsed: Long? = null

    /** État courant (dernier état connu au démarrage, conservé par [WidgetStatusCache]). */
    fun state(context: Context): StateFlow<WidgetModel?> {
        currentModel(context)
        return _state.asStateFlow()
    }

    fun currentModel(context: Context): WidgetModel =
        _state.value ?: WidgetStatusCache(context).load()
            .let { cached -> WidgetModel(loggedIn = cached != null, status = cached) }
            .also { _state.value = it }

    /** Changement de session ou nouvel état lu par l'application (écran de la protection). */
    fun onRepositoryState(context: Context, authState: AuthState, status: ProtectionStatus?) {
        when (authState) {
            AuthState.Restoring -> Unit
            is AuthState.LoggedOut -> show(context, WidgetModel())
            is AuthState.LoggedIn -> if (status != null) {
                show(context, currentModel(context).copy(loggedIn = true, status = status, hasError = false))
            }
        }
    }

    /** Actualisation périodique ou ajout d'un widget : sans indicateur de progression. */
    fun refresh(context: Context): Job = execute(context, WidgetCommand.Refresh, showProgress = false)

    /** Comme [refresh], sauf si l'état a été lu il y a moins de [REFRESH_THROTTLE_MILLIS]. */
    fun refreshIfStale(context: Context) {
        val last = lastRefreshElapsed
        if (last != null && SystemClock.elapsedRealtime() - last < REFRESH_THROTTLE_MILLIS) return
        refresh(context)
    }

    /** Action demandée depuis un widget ou la tuile ; le [Job] se termine une fois le résultat affiché. */
    fun execute(context: Context, command: WidgetCommand, showProgress: Boolean = true): Job {
        val application = context.applicationContext as OnyxFilterApplication

        // Widgets et tuile sont actualisés en même temps : une seule lecture suffit.
        if (command == WidgetCommand.Refresh) {
            refreshJob?.takeIf { it.isActive }?.let { return it }
        }

        val job = scope.launch { run(application, command, showProgress) }
        if (command == WidgetCommand.Refresh) refreshJob = job
        return job
    }

    /** Le dernier widget d'un type a été retiré : l'alarme de réactivation n'est peut-être plus utile. */
    fun onWidgetsRemoved(context: Context) {
        ProtectionWidgets.render(context, currentModel(context))
    }

    private suspend fun run(application: OnyxFilterApplication, command: WidgetCommand, showProgress: Boolean) {
        val repository = application.repository
        repository.restoreSession()
        if (repository.authState.value !is AuthState.LoggedIn) {
            show(application, WidgetModel())
            return
        }

        val before = currentModel(application).copy(loggedIn = true)
        // Nouveau widget : il affiche tout de suite le dernier état connu plutôt que sa mise en page par défaut.
        show(application, before.copy(isUpdating = showProgress))

        val status = try {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                when (command) {
                    WidgetCommand.Refresh -> repository.getProtection()
                    WidgetCommand.Enable -> repository.enableProtection()
                    is WidgetCommand.Disable -> repository.disableProtection(command.duration)
                }
            }
        } catch (e: OnyxFilterException.SessionExpired) {
            show(application, WidgetModel())
            return
        } catch (e: OnyxFilterException) {
            null
        }

        val current = currentModel(application)
        if (status != null) {
            lastRefreshElapsed = SystemClock.elapsedRealtime()
            show(application, current.copy(loggedIn = true, status = status, isUpdating = false, hasError = false))
        } else {
            show(application, current.copy(isUpdating = false, hasError = true))
        }
    }

    private fun show(context: Context, newModel: WidgetModel) {
        _state.value = newModel
        WidgetStatusCache(context).save(newModel.status.takeIf { newModel.loggedIn })
        ProtectionWidgets.render(context, newModel)
    }
}

/** Garde le processus en vie (goAsync) jusqu'à la fin de [job]. */
internal fun BroadcastReceiver.finishWhenDone(job: Job) {
    val pendingResult = goAsync()
    job.invokeOnCompletion { pendingResult.finish() }
}
