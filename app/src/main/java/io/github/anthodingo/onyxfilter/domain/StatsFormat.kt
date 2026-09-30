package io.github.anthodingo.onyxfilter.domain

import java.text.NumberFormat
import java.util.Locale

/** Mise en forme des statistiques, dans la langue du téléphone. */
object StatsFormat {

    /** "12 345" en français, "12,345" en anglais. */
    fun count(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** Part entre 0 et 1, avec une décimale : "4,5 %" en français, "4.5%" en anglais. */
    fun percent(ratio: Double, locale: Locale): String {
        val format = NumberFormat.getPercentInstance(locale)
        format.minimumFractionDigits = 1
        format.maximumFractionDigits = 1
        return format.format(ratio.coerceIn(0.0, 1.0))
    }
}

/** Colonne d'un histogramme, en pixels (origine en haut à gauche). */
data class Column(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Géométrie d'un histogramme en colonnes (une série) : colonnes de largeur égale, séparées par
 * [gap], au plus [maxWidth] de large, partant toutes de la ligne de base (bas de la zone). La plus
 * haute valeur occupe toute la hauteur ; une valeur non nulle garde au moins [minHeight], pour rester
 * visible à côté d'un pic. Une valeur nulle ne produit pas de colonne.
 */
object ColumnChart {

    fun layout(
        values: List<Long>,
        width: Float,
        height: Float,
        gap: Float,
        maxWidth: Float,
        minHeight: Float,
    ): List<Column> {
        val max = values.maxOrNull() ?: return emptyList()
        if (max <= 0 || width <= 0f || height <= 0f) return emptyList()

        val slot = width / values.size
        val columnWidth = (slot - gap).coerceIn(1f, maxWidth)
        return values.mapIndexedNotNull { index, value ->
            if (value <= 0) return@mapIndexedNotNull null
            val columnHeight = (height * value / max).coerceIn(minHeight.coerceAtMost(height), height)
            val center = slot * index + slot / 2
            Column(
                left = center - columnWidth / 2,
                top = height - columnHeight,
                right = center + columnWidth / 2,
                bottom = height,
            )
        }
    }

    /** Position de la plus haute valeur (la plus récente en cas d'égalité), `null` si toutes sont nulles. */
    fun peakIndex(values: List<Long>): Int? {
        val max = values.maxOrNull() ?: return null
        if (max <= 0) return null
        return values.lastIndexOf(max)
    }
}
