package io.github.anthodingo.onyxfilter.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * Client HTTP de l'API mobile d'une instance OnyxFilter (`/api/auth/...`, `/api/protection`).
 *
 * Sans état : l'adresse du serveur et le jeton d'accès sont fournis à chaque appel, la gestion de la
 * session (stockage, rafraîchissement des jetons) revient à [OnyxFilterRepository].
 */
class OnyxFilterApi(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun login(baseUrl: String, username: String, password: String): TokenResponse {
        val request = Request.Builder()
            .url(ServerUrl.endpoint(baseUrl, "api/auth/login"))
            .post(jsonBody(LoginRequest.serializer(), LoginRequest(username, password)))
            .build()

        return execute(request) { response ->
            when {
                response.code == 200 -> decode(response, TokenResponse.serializer())
                // 400 : champ vide, 401 : identifiants refusés ou compte verrouillé. Toujours en JSON :
                // un 400 en HTML vient d'une instance sans API mobile (voir unexpected()).
                response.code in 400..401 && response.isJson() ->
                    throw OnyxFilterException.InvalidCredentials(problemDetail(response))
                else -> throw unexpected(response)
            }
        }
    }

    suspend fun refresh(baseUrl: String, refreshToken: String): TokenResponse {
        val request = Request.Builder()
            .url(ServerUrl.endpoint(baseUrl, "api/auth/refresh"))
            .post(jsonBody(RefreshRequest.serializer(), RefreshRequest(refreshToken)))
            .build()

        return execute(request) { response ->
            when (response.code) {
                200 -> decode(response, TokenResponse.serializer())
                401 -> throw OnyxFilterException.SessionExpired()
                else -> throw unexpected(response)
            }
        }
    }

    suspend fun getProtection(baseUrl: String, accessToken: String): ProtectionStateDto {
        val request = Request.Builder()
            .url(ServerUrl.endpoint(baseUrl, "api/protection"))
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return execute(request, ::decodeProtectionState)
    }

    /**
     * @param durationSeconds durée de la désactivation (ignorée si [enabled]) ; `null` pour une
     * désactivation sans échéance.
     */
    suspend fun setProtection(
        baseUrl: String,
        accessToken: String,
        enabled: Boolean,
        durationSeconds: Long?,
    ): ProtectionStateDto {
        val body = ProtectionUpdateRequest(enabled, if (enabled) null else durationSeconds)
        val request = Request.Builder()
            .url(ServerUrl.endpoint(baseUrl, "api/protection"))
            .header("Authorization", "Bearer $accessToken")
            .put(jsonBody(ProtectionUpdateRequest.serializer(), body))
            .build()

        return execute(request, ::decodeProtectionState)
    }

    private fun decodeProtectionState(response: Response): ProtectionStateDto =
        when (response.code) {
            200 -> decode(response, ProtectionStateDto.serializer())
            401 -> throw OnyxFilterException.Unauthorized()
            else -> throw unexpected(response)
        }

    private suspend fun <T> execute(request: Request, handle: (Response) -> T): T {
        val response = try {
            httpClient.newCall(request).await()
        } catch (e: IOException) {
            throw OnyxFilterException.Network(e)
        }
        return withContext(ioDispatcher) {
            try {
                response.use(handle)
            } catch (e: IOException) {
                throw OnyxFilterException.Network(e)
            }
        }
    }

    // Appel asynchrone annulé avec la coroutine (écran quitté, délai d'un widget dépassé...) :
    // Call.execute() bloquerait jusqu'à la réponse ou au délai d'OkHttp.
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            },
        )
    }

    private fun <T> jsonBody(serializer: KSerializer<T>, value: T): RequestBody =
        ApiJson.encodeToString(serializer, value).toRequestBody(JsonMediaType)

    private fun <T> decode(response: Response, serializer: KSerializer<T>): T {
        val body = response.body?.string().orEmpty()
        return try {
            ApiJson.decodeFromString(serializer, body)
        } catch (e: IllegalArgumentException) {
            // SerializationException (JSON invalide ou champ manquant) hérite d'IllegalArgumentException.
            throw OnyxFilterException.InvalidResponse(e)
        }
    }

    // Pas de route /api (instance sans l'API mobile, ou adresse qui ne désigne pas OnyxFilter) : 404 ou
    // 405 selon la méthode, voire 400 pour un POST (OnyxFilter rejoue le 404 sur sa page Blazor
    // /not-found, qui refuse un POST sans formulaire). Les erreurs de l'API, elles, sont en JSON.
    private fun unexpected(response: Response): OnyxFilterException =
        if (response.code in NoApiStatusCodes && !response.isJson()) {
            OnyxFilterException.ApiNotAvailable(response.code)
        } else {
            OnyxFilterException.Http(response.code, problemDetail(response))
        }

    private fun Response.isJson(): Boolean = body?.contentType()?.subtype?.contains("json", ignoreCase = true) == true

    // Message d'erreur du serveur ("detail" du corps problem+json), s'il y en a un.
    private fun problemDetail(response: Response): String? {
        val body = response.body?.string()
        if (body.isNullOrBlank()) return null
        return try {
            val problem = ApiJson.decodeFromString(ProblemDetails.serializer(), body)
            problem.detail ?: problem.title
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        val NoApiStatusCodes = setOf(400, 404, 405)
    }
}
