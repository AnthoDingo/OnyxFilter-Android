package io.github.anthodingo.onyxfilter

import android.app.Application
import io.github.anthodingo.onyxfilter.data.OnyxFilterApi
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.data.SecureSessionStore
import kotlinx.coroutines.MainScope
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
    }
}
