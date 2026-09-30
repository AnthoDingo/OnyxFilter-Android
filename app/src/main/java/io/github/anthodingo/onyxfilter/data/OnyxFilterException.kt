package io.github.anthodingo.onyxfilter.data

import java.io.IOException

/** Erreurs d'accès à l'instance OnyxFilter, traduites en message par l'interface. */
sealed class OnyxFilterException(message: String?, cause: Throwable? = null) : Exception(message, cause) {

    /** Nom d'utilisateur ou mot de passe refusé (ou compte verrouillé) par le serveur. */
    class InvalidCredentials(val serverMessage: String?) : OnyxFilterException(serverMessage)

    /** Jeton d'accès refusé (HTTP 401) : il faut le rafraîchir. */
    class Unauthorized : OnyxFilterException("Jeton d'accès refusé")

    /** Jeton de rafraîchissement refusé : l'utilisateur doit se reconnecter. */
    class SessionExpired : OnyxFilterException("Session expirée")

    /** Aucun point d'accès /api sur ce serveur : adresse erronée ou instance pas à jour. */
    class ApiNotAvailable(val code: Int) : OnyxFilterException("API introuvable (HTTP $code)")

    /** Réponse HTTP inattendue. */
    class Http(val code: Int, val serverMessage: String?) : OnyxFilterException(serverMessage ?: "HTTP $code")

    /** Serveur injoignable, délai dépassé, certificat refusé... */
    class Network(cause: IOException) : OnyxFilterException(cause.message, cause)

    /** Le serveur a répondu, mais pas avec le JSON attendu (ce n'est probablement pas OnyxFilter). */
    class InvalidResponse(cause: Throwable?) : OnyxFilterException(cause?.message, cause)
}
