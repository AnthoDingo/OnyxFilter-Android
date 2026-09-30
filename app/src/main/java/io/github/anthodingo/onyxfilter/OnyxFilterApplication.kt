package io.github.anthodingo.onyxfilter

import android.app.Application
import io.github.anthodingo.onyxfilter.data.OnyxFilterApi
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.data.SecureSessionStore
import io.github.anthodingo.onyxfilter.widget.StatsWidgetController
import io.github.anthodingo.onyxfilter.widget.WidgetController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OnyxFilterApplication : Application() {

    lateinit var repository: OnyxFilterRepository
        private set

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        repository = OnyxFilterRepository(OnyxFilterApi(httpClient), SecureSessionStore(this))
        applicationScope.launch { repository.restoreSession() }

        // Les widgets suivent la session et chaque état lu ou modifié depuis l'application.
        applicationScope.launch {
            combine(repository.authState, repository.protectionStatus, ::Pair).collect { (authState, status) ->
                WidgetController.onRepositoryState(this@OnyxFilterApplication, authState, status)
            }
        }
        applicationScope.launch {
            combine(repository.authState, repository.stats, ::Pair).collect { (authState, stats) ->
                StatsWidgetController.onRepositoryState(this@OnyxFilterApplication, authState, stats)
            }
        }
    }
}
