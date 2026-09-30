package io.github.anthodingo.onyxfilter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Corps JSON échangés avec l'API mobile du serveur OnyxFilter (MobileApiEndpoints, sous /api).
// Voir la section "API" du README pour le contrat complet.

internal val ApiJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
internal data class LoginRequest(val username: String, val password: String)

@Serializable
internal data class RefreshRequest(val refreshToken: String)

/** Réponse de `/api/auth/login` et `/api/auth/refresh` (AccessTokenResponse d'ASP.NET Core Identity). */
@Serializable
data class TokenResponse(
    val tokenType: String = "Bearer",
    val accessToken: String,
    /** Durée de validité du jeton d'accès, en secondes. */
    val expiresIn: Long,
    val refreshToken: String,
)

/** État de la bascule "Protection" du tableau de bord OnyxFilter. */
@Serializable
data class ProtectionStateDto(
    val enabled: Boolean,
    /** Échéance de réactivation automatique (ISO 8601, UTC), `null` si active ou désactivée sans échéance. */
    val disabledUntilUtc: String? = null,
    /** Secondes restantes avant la réactivation automatique, calculées par le serveur. */
    val remainingSeconds: Long? = null,
)

/**
 * Corps de `PUT /api/protection`. `durationSeconds` absent avec `enabled = false` : désactivation
 * sans échéance.
 */
@Serializable
internal data class ProtectionUpdateRequest(
    val enabled: Boolean,
    val durationSeconds: Long? = null,
)

/** Corps d'erreur "application/problem+json" (RFC 9457) renvoyé par le serveur. */
@Serializable
internal data class ProblemDetails(
    val title: String? = null,
    val detail: String? = null,
)
