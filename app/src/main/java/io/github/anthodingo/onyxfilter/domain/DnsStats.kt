package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.ClientLocationDto
import io.github.anthodingo.onyxfilter.data.StatsDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Activité DNS des dernières 24 heures. */
data class DnsStats(
    val totalQueries: Long,
    val blockedQueries: Long,
    /** Part des requêtes bloquées, entre 0 et 1. */
    val blockedRatio: Double,
    val averageProcessingTimeMs: Int,
    /** Domaine le plus bloqué et son nombre de requêtes, s'il y en a un. */
    val topBlockedDomain: RankedItem?,
    /** Domaines les plus demandés, du premier au dixième au plus. */
    val topQueriedDomains: List<RankedItem> = emptyList(),
    /** Domaines les plus bloqués, du premier au dixième au plus. */
    val topBlockedDomains: List<RankedItem> = emptyList(),
    /** Clients les plus actifs, du premier au dixième au plus. */
    val topClients: List<RankedItem> = emptyList(),
    /** Requêtes par heure, de la plus ancienne à la plus récente (24 valeurs en général). */
    val hourlyQueries: List<Long>,
    /** Requêtes bloquées par heure, alignées sur [hourlyQueries] (vide si inconnues). */
    val hourlyBlocked: List<Long> = emptyList(),
    /** Début de la dernière heure de [hourlyQueries]. */
    val lastHourStart: Instant?,
) {
    /** @property detail précision à afficher sous le nom (pays et fournisseur d'un client). */
    data class RankedItem(val name: String, val count: Long, val detail: String? = null)

    companion object {
        fun fromDto(dto: StatsDto): DnsStats = DnsStats(
            totalQueries = dto.totalQueries.coerceAtLeast(0),
            blockedQueries = dto.blockedQueries.coerceAtLeast(0),
            blockedRatio = dto.blockedRatio.coerceIn(0.0, 1.0),
            averageProcessingTimeMs = dto.averageProcessingTimeMs.coerceAtLeast(0),
            topBlockedDomain = dto.topBlockedDomains.firstOrNull()?.let { RankedItem(it.name, it.count) },
            topQueriedDomains = dto.topQueriedDomains.ranking { RankedItem(it.name, it.count.coerceAtLeast(0)) },
            topBlockedDomains = dto.topBlockedDomains.ranking { RankedItem(it.name, it.count.coerceAtLeast(0)) },
            topClients = dto.topClients.ranking { RankedItem(it.name, it.count.coerceAtLeast(0), it.location?.describe()) },
            hourlyQueries = dto.hourly.map { it.totalQueries.coerceAtLeast(0) },
            hourlyBlocked = dto.hourly.map { it.blockedQueries.coerceIn(0, it.totalQueries.coerceAtLeast(0)) },
            lastHourStart = dto.hourly.lastOrNull()?.hourStart?.let(::parseApiInstant),
        )

        /** Taille des classements, celle de l'interface web d'OnyxFilter. */
        const val TOP_SIZE = 10

        private inline fun <T> List<T>.ranking(transform: (T) -> RankedItem): List<RankedItem> = take(TOP_SIZE).map(transform)
    }
}

/** "FR · Orange", "FR" ou "Orange" ; `null` si rien n'est connu. */
internal fun ClientLocationDto.describe(): String? =
    listOfNotNull(country?.takeIf { it.isNotBlank() }, provider.takeIf { it.isNotBlank() })
        .joinToString(" · ")
        .ifEmpty { null }

/**
 * Date de l'API ("2026-09-30T12:00:00+00:00", ou "…Z"), ou `null` si illisible. OffsetDateTime plutôt
 * qu'Instant.parse, qui refuse les décalages "+00:00" sur les versions d'Android les plus anciennes.
 */
internal fun parseApiInstant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (e: DateTimeParseException) {
    null
}
