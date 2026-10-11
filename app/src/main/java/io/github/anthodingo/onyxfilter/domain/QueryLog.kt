package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.QueryLogDto
import io.github.anthodingo.onyxfilter.data.QueryLogItemDto
import java.time.Instant

/** Une page du journal des requêtes DNS des clients. */
data class QueryLogPage(
    /** Des plus récentes aux plus anciennes. */
    val entries: List<QueryLogEntry>,
    /** D'autres requêtes, plus anciennes, suivent cette page. */
    val hasMore: Boolean,
) {
    companion object {
        fun fromDto(dto: QueryLogDto): QueryLogPage = QueryLogPage(dto.items.map(QueryLogEntry::fromDto), dto.hasMore)
    }
}

/** Requête DNS d'un client, et ce que le serveur en a fait. */
data class QueryLogEntry(
    /** `null` si la date du serveur est illisible. */
    val time: Instant?,
    val domain: String,
    /** Type d'enregistrement demandé : "A", "AAAA", "HTTPS"... */
    val type: String,
    /** Adresse IP (ou nom) du client. */
    val client: String,
    /** Pays et fournisseur du client ("FR · Orange"), s'ils sont connus. */
    val clientDetail: String?,
    val reason: QueryLogReason,
    val blocked: Boolean,
    /** Règle, liste ou service en cause, pour une requête bloquée ou réécrite. */
    val reasonDetail: String?,
    /** Serveur en amont qui a répondu, pour une requête résolue. */
    val upstream: String?,
    val processingTimeMs: Long,
) {
    companion object {
        fun fromDto(dto: QueryLogItemDto): QueryLogEntry = QueryLogEntry(
            time = parseApiInstant(dto.time),
            domain = dto.domain,
            type = dto.type,
            client = dto.client,
            clientDetail = dto.clientLocation?.describe(),
            reason = QueryLogReason.parse(dto.reason),
            blocked = dto.blocked,
            reasonDetail = dto.reasonDetail?.takeIf { it.isNotBlank() },
            upstream = dto.upstream?.takeIf { it.isNotBlank() },
            processingTimeMs = dto.processingTimeMs.coerceAtLeast(0),
        )
    }
}

/** Sort d'une requête (QueryLogReason côté serveur). */
enum class QueryLogReason {
    Resolved,
    Cached,
    Rewritten,
    SafeSearch,
    CustomRule,
    Filtered,
    BlockedService,
    SecurityThreat,
    ParentalControl,

    /** Valeur ajoutée par une version plus récente du serveur. */
    Unknown,
    ;

    companion object {
        fun parse(value: String): QueryLogReason = entries.firstOrNull { it != Unknown && it.name.equals(value, ignoreCase = true) } ?: Unknown
    }
}
