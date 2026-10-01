package io.github.anthodingo.onyxfilter.data

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Lien de connexion du QR code de la page « Accès API » :
 * `onyxfilter://pair?server=<adresse URL-encodée>&token=<jeton>`.
 * Le jeton est à usage unique et n'est valable qu'une minute environ.
 */
data class PairingLink(
    /** Adresse normalisée de l'instance (voir [ServerUrl.normalize]). */
    val serverUrl: String,
    val apiToken: String,
) {
    // Comme Session : le jeton ne doit pas se retrouver dans les journaux.
    override fun toString(): String = "PairingLink(serverUrl=$serverUrl)"

    companion object {
        const val SCHEME = "onyxfilter"
        const val HOST = "pair"

        /** @return le lien analysé, ou `null` s'il est incomplet ou invalide. */
        fun parse(link: String?): PairingLink? {
            val uri = try {
                URI(link ?: return null)
            } catch (e: URISyntaxException) {
                return null
            }
            if (!SCHEME.equals(uri.scheme, ignoreCase = true) || !HOST.equals(uri.host, ignoreCase = true)) return null

            val params = uri.rawQuery.orEmpty().split('&').mapNotNull { param ->
                val (name, value) = param.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                try {
                    name to URLDecoder.decode(value, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    null
                }
            }.toMap()

            val serverUrl = params["server"]?.let(ServerUrl::normalize) ?: return null
            val token = params["token"]?.trim()?.takeIf { it.startsWith(TOKEN_PREFIX) && it.length > TOKEN_PREFIX.length }
                ?: return null
            return PairingLink(serverUrl, token)
        }

        /** Préfixe des jetons créés par la page « Accès API » (ApiTokenService.TokenPrefix). */
        const val TOKEN_PREFIX = "onyx_"
    }
}
