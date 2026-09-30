package io.github.anthodingo.onyxfilter.data

import java.io.IOException

/** Erreurs d'accès à l'instance OnyxFilter, traduites en message par l'interface. */
sealed class OnyxFilterException(message: String?, cause: Throwable? = null) : Exception(message, cause) {

    /** Jeton d'API manquant, invalide ou révoqué (HTTP 401). */
    class Unauthorized(val serverMessage: String?) : OnyxFilterException(serverMessage ?: "Jeton refusé")

    /** Pas (ou plus) de session : jeton refusé par le serveur, ou déconnexion. */
    class SessionEnded : OnyxFilterException("Session terminée")

    /** Aucun point d'accès /api/v1 sur ce serveur : adresse erronée ou instance pas à jour. */
    class ApiNotAvailable(val code: Int) : OnyxFilterException("API introuvable (HTTP $code)")

    /** Réponse HTTP inattendue. */
    class Http(val code: Int, val serverMessage: String?) : OnyxFilterException(serverMessage ?: "HTTP $code")

    /** Serveur injoignable, délai dépassé, certificat refusé... */
    class Network(cause: IOException) : OnyxFilterException(cause.message, cause)

    /** Le serveur a répondu, mais pas avec le JSON attendu (ce n'est probablement pas OnyxFilter). */
    class InvalidResponse(cause: Throwable?) : OnyxFilterException(cause?.message, cause)
}
