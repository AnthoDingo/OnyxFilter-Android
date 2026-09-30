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
 * Client de l'API HTTP d'une instance OnyxFilter (`/api/v1`), authentifié par un jeton créé depuis la
 * page « Accès API » de l'interface web.
 *
 * Sans état : l'adresse du serveur et le jeton sont fournis à chaque appel, la conservation de la
 * session revient à [OnyxFilterRepository].
 */
class OnyxFilterApi(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun getProtection(baseUrl: String, token: String): ProtectionStateDto =
        call(authorized(baseUrl, token, "api/v1/protection").get(), ProtectionStateDto.serializer())

    suspend fun enableProtection(baseUrl: String, token: String): ProtectionStateDto =
        call(authorized(baseUrl, token, "api/v1/protection/enable").post(EmptyBody), ProtectionStateDto.serializer())

    /** @param durationSeconds durée de la désactivation, `null` pour une désactivation sans échéance. */
    suspend fun disableProtection(baseUrl: String, token: String, durationSeconds: Long?): ProtectionStateDto {
        val body = ApiJson.encodeToString(DisableProtectionRequest.serializer(), DisableProtectionRequest(durationSeconds))
            .toRequestBody(JsonMediaType)
        return call(authorized(baseUrl, token, "api/v1/protection/disable").post(body), ProtectionStateDto.serializer())
    }

    suspend fun getStats(baseUrl: String, token: String): StatsDto =
        call(authorized(baseUrl, token, "api/v1/stats").get(), StatsDto.serializer())

    private fun authorized(baseUrl: String, token: String, path: String): Request.Builder =
        Request.Builder()
            .url(ServerUrl.endpoint(baseUrl, path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")

    private suspend fun <T> call(request: Request.Builder, serializer: KSerializer<T>): T {
        val response = try {
            httpClient.newCall(request.build()).await()
        } catch (e: IOException) {
            throw OnyxFilterException.Network(e)
        }
        return withContext(ioDispatcher) {
            try {
                response.use { handle(it, serializer) }
            } catch (e: IOException) {
                throw OnyxFilterException.Network(e)
            }
        }
    }

    private fun <T> handle(response: Response, serializer: KSerializer<T>): T = when {
        response.code == 200 -> decode(response, serializer)
        response.code == 401 -> throw OnyxFilterException.Unauthorized(apiError(response)?.message)
        // Pas de /api/v1 : 404 (en JSON pour une instance récente, en HTML sinon), voire 400 ou 405 en
        // HTML (OnyxFilter rejoue les erreurs sur sa page Blazor /not-found, qui refuse un POST). Les
        // autres erreurs de l'API sont toujours en JSON.
        response.code == 404 || (response.code in NoApiStatusCodes && !response.isJson()) ->
            throw OnyxFilterException.ApiNotAvailable(response.code)
        else -> throw OnyxFilterException.Http(response.code, apiError(response)?.message)
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

    private fun <T> decode(response: Response, serializer: KSerializer<T>): T {
        val body = response.body.string()
        return try {
            ApiJson.decodeFromString(serializer, body)
        } catch (e: IllegalArgumentException) {
            // SerializationException (JSON invalide ou champ manquant) hérite d'IllegalArgumentException.
            throw OnyxFilterException.InvalidResponse(e)
        }
    }

    private fun Response.isJson(): Boolean = body.contentType()?.subtype?.contains("json", ignoreCase = true) == true

    private fun apiError(response: Response): ApiError? {
        if (!response.isJson()) return null
        val body = response.body.string()
        if (body.isBlank()) return null
        return try {
            ApiJson.decodeFromString(ApiError.serializer(), body)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        val EmptyBody: RequestBody = ByteArray(0).toRequestBody(null)
        val NoApiStatusCodes = setOf(400, 405)
    }
}
