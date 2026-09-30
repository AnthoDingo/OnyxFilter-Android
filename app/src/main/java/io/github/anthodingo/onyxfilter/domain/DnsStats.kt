package io.github.anthodingo.onyxfilter.domain

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
    /** Requêtes par heure, de la plus ancienne à la plus récente (24 valeurs en général). */
    val hourlyQueries: List<Long>,
    /** Début de la dernière heure de [hourlyQueries]. */
    val lastHourStart: Instant?,
) {
    data class RankedItem(val name: String, val count: Long)

    companion object {
        fun fromDto(dto: StatsDto): DnsStats = DnsStats(
            totalQueries = dto.totalQueries.coerceAtLeast(0),
            blockedQueries = dto.blockedQueries.coerceAtLeast(0),
            blockedRatio = dto.blockedRatio.coerceIn(0.0, 1.0),
            averageProcessingTimeMs = dto.averageProcessingTimeMs.coerceAtLeast(0),
            topBlockedDomain = dto.topBlockedDomains.firstOrNull()?.let { RankedItem(it.name, it.count) },
            hourlyQueries = dto.hourly.map { it.totalQueries.coerceAtLeast(0) },
            lastHourStart = dto.hourly.lastOrNull()?.hourStart?.let(::parseApiInstant),
        )
    }
}

/**
 * Date de l'API ("2026-09-30T12:00:00+00:00", ou "…Z"), ou `null` si illisible. OffsetDateTime plutôt
 * qu'Instant.parse, qui refuse les décalages "+00:00" sur les versions d'Android les plus anciennes.
 */
internal fun parseApiInstant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (e: DateTimeParseException) {
    null
}
