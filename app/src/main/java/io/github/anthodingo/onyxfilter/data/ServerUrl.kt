package io.github.anthodingo.onyxfilter.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Adresse de l'instance OnyxFilter saisie par l'utilisateur.
 */
object ServerUrl {

    /**
     * Normalise la saisie de l'utilisateur : `https://` est ajouté quand aucun schéma n'est précisé,
     * la requête, le fragment et les "/" finaux sont retirés. Un chemin est conservé, pour les
     * instances servies derrière un proxy inverse (`https://maison.example/onyxfilter`).
     *
     * @return l'adresse normalisée, sans "/" final, ou `null` si la saisie n'est pas une adresse
     * HTTP(S) valide.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null

        return url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    }

    /** `true` si l'adresse (normalisée) utilise HTTP en clair. */
    fun isCleartext(normalizedUrl: String): Boolean = normalizedUrl.startsWith("http://", ignoreCase = true)

    /** Adresse d'un point d'accès de l'API, `path` étant relatif à la racine de l'instance (`api/...`). */
    internal fun endpoint(baseUrl: String, path: String): HttpUrl =
        requireNotNull("$baseUrl/$path".toHttpUrlOrNull()) { "Adresse de serveur invalide : $baseUrl" }

    /** Nom d'hôte (et port éventuel) à afficher dans l'interface. */
    fun displayName(normalizedUrl: String): String {
        val url = normalizedUrl.toHttpUrlOrNull() ?: return normalizedUrl
        val defaultPort = HttpUrl.defaultPort(url.scheme)
        val hostAndPort = if (url.port == defaultPort) url.host else "${url.host}:${url.port}"
        val path = url.encodedPath.trimEnd('/')
        return hostAndPort + path
    }
}
