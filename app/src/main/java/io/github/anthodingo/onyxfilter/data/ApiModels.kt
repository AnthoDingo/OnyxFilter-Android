package io.github.anthodingo.onyxfilter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Corps JSON de l'API HTTP d'OnyxFilter (/api/v1, Services/Api/ApiModels.cs côté serveur). Dates au
// format ISO 8601 avec décalage ("2026-09-30T12:00:00+00:00").

internal val ApiJson = Json {
    ignoreUnknownKeys = true
}

/** État de la bascule "Protection" (GET /api/v1/protection et réponses de enable/disable). */
@Serializable
data class ProtectionStateDto(
    val enabled: Boolean,
    /** Échéance de réactivation automatique, `null` si active ou désactivée sans échéance. */
    val disabledUntil: String? = null,
    /** Secondes restantes avant la réactivation automatique, calculées par le serveur. */
    val remainingSeconds: Long? = null,
)

/** Corps de POST /api/v1/protection/disable ; sans durée, désactivation jusqu'à réactivation. */
@Serializable
internal data class DisableProtectionRequest(val durationSeconds: Long? = null)

/** Statistiques des dernières 24 heures (GET /api/v1/stats). */
@Serializable
data class StatsDto(
    val generatedAt: String? = null,
    val totalQueries: Long = 0,
    val blockedQueries: Long = 0,
    val blockedRatio: Double = 0.0,
    val averageProcessingTimeMs: Int = 0,
    /** Classements des dernières 24 heures, 10 entrées au plus chacun. */
    val topQueriedDomains: List<RankedItemDto> = emptyList(),
    val topBlockedDomains: List<RankedItemDto> = emptyList(),
    val topClients: List<RankedClientDto> = emptyList(),
    /** Une entrée par heure, de la plus ancienne à la plus récente. */
    val hourly: List<HourlyStatsDto> = emptyList(),
)

@Serializable
data class RankedItemDto(val name: String, val count: Long)

/** Client du classement : adresse IP (ou nom), et son pays et fournisseur s'ils sont connus. */
@Serializable
data class RankedClientDto(val name: String, val count: Long, val location: ClientLocationDto? = null)

/**
 * Pays (code ISO 3166-1 alpha-2) et fournisseur d'accès d'un client ; absent pour les adresses privées ou
 * si l'option est désactivée sur le serveur.
 */
@Serializable
data class ClientLocationDto(val country: String? = null, val asn: Int = 0, val provider: String = "")

/** Une page du journal des requêtes (GET /api/v1/querylog), de la plus récente à la plus ancienne. */
@Serializable
data class QueryLogDto(
    val items: List<QueryLogItemDto> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 0,
    /** D'autres requêtes, plus anciennes, suivent cette page. */
    val hasMore: Boolean = false,
)

@Serializable
data class QueryLogItemDto(
    val time: String,
    val domain: String,
    val type: String = "",
    val client: String = "",
    val clientLocation: ClientLocationDto? = null,
    /** Valeur de QueryLogReason côté serveur : "Resolved", "Cached", "Filtered"... */
    val reason: String = "",
    val blocked: Boolean = false,
    val reasonDetail: String? = null,
    val upstream: String? = null,
    val processingTimeMs: Long = 0,
)

@Serializable
data class HourlyStatsDto(val hourStart: String, val totalQueries: Long = 0, val blockedQueries: Long = 0)

/** Corps d'erreur de l'API : { "error": "unauthorized", "message": "..." }. */
@Serializable
internal data class ApiError(
    val error: String? = null,
    val message: String? = null,
)
