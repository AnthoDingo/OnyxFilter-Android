package io.github.anthodingo.onyxfilter.data

/** Connexion à une instance OnyxFilter : son adresse et un jeton de son API (page « Accès API »). */
data class Session(
    /** Adresse normalisée de l'instance (voir [ServerUrl.normalize]). */
    val serverUrl: String,
    val apiToken: String,
) {
    // Pas de toString() par défaut : le jeton ne doit pas se retrouver dans les journaux.
    override fun toString(): String = "Session(serverUrl=$serverUrl)"
}

/** Dernière adresse de serveur utilisée, pour préremplir l'écran de connexion. */
data class LoginHint(val serverUrl: String)

/** Stockage persistant de la session. Appelé hors du thread principal. */
interface SessionStore {
    fun load(): Session?

    fun save(session: Session)

    /** Supprime le jeton, en conservant l'adresse du serveur ([loginHint]). */
    fun clear()

    fun loginHint(): LoginHint?
}
