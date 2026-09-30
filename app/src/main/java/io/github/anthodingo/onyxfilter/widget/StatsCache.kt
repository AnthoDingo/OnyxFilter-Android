package io.github.anthodingo.onyxfilter.widget

import android.content.Context
import androidx.core.content.edit
import io.github.anthodingo.onyxfilter.data.ApiJson
import io.github.anthodingo.onyxfilter.domain.DnsStats
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Dernières statistiques affichées par les widgets, conservées entre deux démarrages de l'application
 * (même rôle que [WidgetStatusCache] pour l'état de la protection).
 */
internal class StatsCache(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** @return les statistiques et le moment de leur lecture, ou `null`. */
    fun load(): Pair<DnsStats, Instant>? {
        val json = preferences.getString(KEY_STATS, null) ?: return null
        val cached = try {
            ApiJson.decodeFromString(Cached.serializer(), json)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val stats = DnsStats(
            totalQueries = cached.totalQueries,
            blockedQueries = cached.blockedQueries,
            blockedRatio = cached.blockedRatio,
            averageProcessingTimeMs = cached.averageProcessingTimeMs,
            topBlockedDomain = cached.topBlockedName?.let { DnsStats.RankedItem(it, cached.topBlockedCount) },
            hourlyQueries = cached.hourlyQueries,
            lastHourStart = cached.lastHourStartMillis?.let(Instant::ofEpochMilli),
        )
        return stats to Instant.ofEpochMilli(cached.fetchedAtMillis)
    }

    fun save(stats: DnsStats?, fetchedAt: Instant?) {
        preferences.edit {
            if (stats == null || fetchedAt == null) {
                clear()
            } else {
                val cached = Cached(
                    totalQueries = stats.totalQueries,
                    blockedQueries = stats.blockedQueries,
                    blockedRatio = stats.blockedRatio,
                    averageProcessingTimeMs = stats.averageProcessingTimeMs,
                    topBlockedName = stats.topBlockedDomain?.name,
                    topBlockedCount = stats.topBlockedDomain?.count ?: 0,
                    hourlyQueries = stats.hourlyQueries,
                    lastHourStartMillis = stats.lastHourStart?.toEpochMilli(),
                    fetchedAtMillis = fetchedAt.toEpochMilli(),
                )
                putString(KEY_STATS, ApiJson.encodeToString(Cached.serializer(), cached))
            }
        }
    }

    @Serializable
    private data class Cached(
        val totalQueries: Long,
        val blockedQueries: Long,
        val blockedRatio: Double,
        val averageProcessingTimeMs: Int,
        val topBlockedName: String? = null,
        val topBlockedCount: Long = 0,
        val hourlyQueries: List<Long> = emptyList(),
        val lastHourStartMillis: Long? = null,
        val fetchedAtMillis: Long,
    )

    private companion object {
        const val PREFERENCES_NAME = "stats_widgets"
        const val KEY_STATS = "stats"
    }
}
