package io.github.anthodingo.onyxfilter.phoneip

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import io.github.anthodingo.onyxfilter.OnyxFilterApplication
import io.github.anthodingo.onyxfilter.data.AuthState
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Autorisation automatique de l'adresse publique du téléphone : quand elle change (autre réseau, nouvelle
 * adresse de l'opérateur), elle est ajoutée aux clients autorisés du DNS.
 */
class PhoneIpAutoAllow(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (value) {
                // ponytail: vérification périodique (15 min au mieux, plus espacée en veille) ; un changement
                // de réseau n'est vu qu'au passage suivant ou à l'ouverture de l'application.
                val job = JobInfo.Builder(JOB_ID, ComponentName(context, PhoneIpJobService::class.java))
                    .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(job)
            } else {
                scheduler.cancel(JOB_ID)
            }
        }

    private companion object {
        const val PREFERENCES_NAME = "phone_ip"
        const val KEY_ENABLED = "auto_allow"
        const val JOB_ID = 1001
    }
}

/**
 * Autorise l'adresse publique du téléphone si le DNS ne la sert pas encore.
 *
 * @throws OnyxFilterException
 */
suspend fun OnyxFilterRepository.allowPhoneIpIfNeeded() {
    val access = getPhoneIpAccess()
    if (!access.allowed) allowClient(access.ip)
}

/** Passage périodique de [PhoneIpAutoAllow]. */
class PhoneIpJobService : JobService() {

    private val scope = MainScope()

    override fun onStartJob(params: JobParameters): Boolean {
        val repository = (application as OnyxFilterApplication).repository
        scope.launch {
            try {
                // La session enregistrée est relue au démarrage du processus (OnyxFilterApplication).
                if (repository.authState.first { it != AuthState.Restoring } is AuthState.LoggedIn) {
                    repository.allowPhoneIpIfNeeded()
                }
            } catch (e: OnyxFilterException) {
                // Nouvel essai au passage suivant.
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
