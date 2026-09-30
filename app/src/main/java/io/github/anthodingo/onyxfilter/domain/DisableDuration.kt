package io.github.anthodingo.onyxfilter.domain

import java.time.Duration
import java.time.ZonedDateTime

/** Durée d'une désactivation de la protection. */
sealed interface DisableDuration {

    /**
     * Secondes à transmettre au serveur, ou `null` pour une désactivation sans échéance.
     *
     * @param now heure locale courante, qui détermine la fin de [UntilTomorrow].
     */
    fun toSeconds(now: ZonedDateTime): Long?

    /** Jusqu'à réactivation manuelle (ou redémarrage du serveur, qui réactive toujours la protection). */
    data object Indefinitely : DisableDuration {
        override fun toSeconds(now: ZonedDateTime): Long? = null
    }

    data class Fixed(val seconds: Long) : DisableDuration {
        init {
            require(seconds in 1..MAX_SECONDS) { "Durée hors limites : $seconds s" }
        }

        override fun toSeconds(now: ZonedDateTime): Long = seconds
    }

    /** Jusqu'à minuit (heure du téléphone), comme l'option "Jusqu'à demain" du tableau de bord. */
    data object UntilTomorrow : DisableDuration {
        override fun toSeconds(now: ZonedDateTime): Long {
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            return Duration.between(now, midnight).seconds.coerceAtLeast(1)
        }
    }

    companion object {
        /** Durée maximale acceptée par le serveur (30 jours). */
        const val MAX_SECONDS: Long = 30L * 24 * 60 * 60

        /** Options du menu "Désactiver la protection" du tableau de bord web. */
        val Presets: List<DisableDuration> = listOf(
            Fixed(30),
            Fixed(60),
            Fixed(10 * 60),
            Fixed(60 * 60),
            UntilTomorrow,
        )

        /**
         * Durée personnalisée saisie en heures et minutes, ou `null` si elle est nulle ou dépasse
         * [MAX_SECONDS].
         */
        fun custom(hours: Long, minutes: Long): Fixed? {
            if (hours < 0 || minutes < 0) return null
            val seconds = hours * 3600 + minutes * 60
            return if (seconds in 1..MAX_SECONDS) Fixed(seconds) else null
        }
    }
}
