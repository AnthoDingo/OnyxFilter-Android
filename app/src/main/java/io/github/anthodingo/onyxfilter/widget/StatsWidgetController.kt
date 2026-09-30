package io.github.anthodingo.onyxfilter.widget

import android.content.Context
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.data.AuthState
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.domain.DnsStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * État des widgets de statistiques et lecture des statistiques depuis ces widgets. Comme
 * [WidgetController], tout se passe sur le thread principal.
 */
internal object StatsWidgetController {

    // Même limite que les actions des autres widgets (BroadcastReceiver.goAsync).
    private const val TIMEOUT_MILLIS = 9_000L

    private val scope = MainScope()
    private var model: StatsWidgetModel? = null
    private var refreshJob: Job? = null

    fun currentModel(context: Context): StatsWidgetModel =
        model ?: StatsCache(context).load()
            .let { cached -> StatsWidgetModel(loggedIn = cached != null, stats = cached?.first, fetchedAt = cached?.second) }
            .also { model = it }

    /** Changement de session ou statistiques lues par l'application. */
    fun onRepositoryState(context: Context, authState: AuthState, stats: DnsStats?) {
        when (authState) {
            AuthState.Restoring -> Unit
            is AuthState.LoggedOut -> show(context, StatsWidgetModel())
            is AuthState.LoggedIn -> if (stats != null) {
                show(context, currentModel(context).copy(loggedIn = true, stats = stats, fetchedAt = Instant.now(), hasError = false))
            }
        }
    }

    /** Relit les statistiques ; le [Job] se termine une fois le résultat affiché. */
    fun refresh(context: Context, showProgress: Boolean): Job {
        refreshJob?.takeIf { it.isActive }?.let { return it }
        val application = context.applicationContext as OnyxFilterApplication
        return scope.launch { run(application, showProgress) }.also { refreshJob = it }
    }

    /** Widget redimensionné : l'histogramme est redessiné à sa nouvelle taille. */
    fun onSizeChanged(context: Context) {
        StatsWidgets.render(context, currentModel(context))
    }

    private suspend fun run(application: OnyxFilterApplication, showProgress: Boolean) {
        val repository = application.repository
        repository.restoreSession()
        if (repository.authState.value !is AuthState.LoggedIn) {
            show(application, StatsWidgetModel())
            return
        }

        // Nouveau widget : il affiche tout de suite les dernières statistiques connues.
        show(application, currentModel(application).copy(loggedIn = true, isUpdating = showProgress))

        val stats = try {
            withTimeoutOrNull(TIMEOUT_MILLIS) { repository.getStats() }
        } catch (e: OnyxFilterException.SessionEnded) {
            show(application, StatsWidgetModel())
            return
        } catch (e: OnyxFilterException) {
            null
        }

        val current = currentModel(application)
        show(
            application,
            if (stats != null) {
                current.copy(loggedIn = true, stats = stats, fetchedAt = Instant.now(), isUpdating = false, hasError = false)
            } else {
                current.copy(isUpdating = false, hasError = true)
            },
        )
    }

    private fun show(context: Context, newModel: StatsWidgetModel) {
        model = newModel
        StatsCache(context).save(newModel.stats.takeIf { newModel.loggedIn }, newModel.fetchedAt)
        StatsWidgets.render(context, newModel)
    }
}
