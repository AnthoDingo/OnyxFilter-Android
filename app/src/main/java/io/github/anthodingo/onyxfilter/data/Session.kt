package io.github.anthodingo.onyxfilter.data

/** Connexion à une instance OnyxFilter. */
data class Session(
    /** Adresse normalisée de l'instance (voir [ServerUrl.normalize]). */
    val serverUrl: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    /** Instant (epoch, ms) à partir duquel le jeton d'accès est considéré comme expiré. */
    val accessTokenExpiresAtMillis: Long,
) {
    fun isAccessTokenExpired(nowMillis: Long): Boolean = nowMillis >= accessTokenExpiresAtMillis

    fun withTokens(tokens: TokenResponse, nowMillis: Long): Session = copy(
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        accessTokenExpiresAtMillis = expiresAt(tokens, nowMillis),
    )

    // Pas de toString() par défaut : les jetons ne doivent pas se retrouver dans les journaux.
    override fun toString(): String = "Session(serverUrl=$serverUrl, username=$username)"

    companion object {
        // Marge de sécurité : le jeton est rafraîchi un peu avant son expiration réelle.
        private const val EXPIRY_MARGIN_MILLIS = 60_000L

        fun create(serverUrl: String, username: String, tokens: TokenResponse, nowMillis: Long): Session = Session(
            serverUrl = serverUrl,
            username = username,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresAtMillis = expiresAt(tokens, nowMillis),
        )

        private fun expiresAt(tokens: TokenResponse, nowMillis: Long): Long =
            nowMillis + (tokens.expiresIn * 1000 - EXPIRY_MARGIN_MILLIS).coerceAtLeast(0)
    }
}

/** Dernière adresse de serveur et dernier nom d'utilisateur saisis, pour préremplir la connexion. */
data class LoginHint(val serverUrl: String, val username: String)

/** Stockage persistant de la session. Appelé hors du thread principal. */
interface SessionStore {
    fun load(): Session?

    fun save(session: Session)

    /** Supprime les jetons, en conservant l'adresse du serveur et le nom d'utilisateur ([loginHint]). */
    fun clear()

    fun loginHint(): LoginHint?
}
