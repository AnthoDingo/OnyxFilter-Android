package io.github.anthodingo.onyxfilter.widget

import android.content.BroadcastReceiver
import android.content.Context
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.data.AuthState
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * État des widgets et exécution de leurs actions. Tout se passe sur le thread principal : les appels
 * au serveur sont suspendus, pas bloquants.
 */
internal object WidgetController {

    // Un BroadcastReceiver (goAsync) doit se terminer en quelques secondes : au-delà, le widget
    // affiche "serveur injoignable" et l'appel HTTP est annulé.
    private const val TIMEOUT_MILLIS = 9_000L

    private val scope = MainScope()
    private var model: WidgetModel? = null
    private var refreshJob: Job? = null

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
    fun refresh(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        execute(context, WidgetCommand.Refresh, pendingResult, showProgress = false)
    }

    /** Action demandée depuis un widget. [pendingResult] est terminé une fois le résultat affiché. */
    fun execute(
        context: Context,
        command: WidgetCommand,
        pendingResult: BroadcastReceiver.PendingResult,
        showProgress: Boolean = true,
    ) {
        val application = context.applicationContext as OnyxFilterApplication

        // Les deux types de widget sont actualisés en même temps : une seule lecture suffit.
        if (command == WidgetCommand.Refresh && refreshJob?.isActive == true) {
            pendingResult.finish()
            return
        }

        val job = scope.launch {
            try {
                run(application, command, showProgress)
            } finally {
                pendingResult.finish()
            }
        }
        if (command == WidgetCommand.Refresh) refreshJob = job
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
        show(
            application,
            if (status != null) {
                current.copy(loggedIn = true, status = status, isUpdating = false, hasError = false)
            } else {
                current.copy(isUpdating = false, hasError = true)
            },
        )
    }

    private fun currentModel(context: Context): WidgetModel =
        model ?: WidgetStatusCache(context).load().let { cached -> WidgetModel(loggedIn = cached != null, status = cached) }

    private fun show(context: Context, newModel: WidgetModel) {
        model = newModel
        WidgetStatusCache(context).save(newModel.status.takeIf { newModel.loggedIn })
        ProtectionWidgets.render(context, newModel)
    }
}
