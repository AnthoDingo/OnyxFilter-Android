package io.github.anthodingo.onyxfilter.domain

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Jour de la réactivation automatique, relativement à aujourd'hui. */
enum class RelativeDay { Today, Tomorrow, Later }

fun relativeDay(moment: ZonedDateTime, now: ZonedDateTime): RelativeDay {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), moment.withZoneSameInstant(now.zone).toLocalDate())
    return when {
        days <= 0 -> RelativeDay.Today
        days == 1L -> RelativeDay.Tomorrow
        else -> RelativeDay.Later
    }
}

/** Durée décomposée pour l'affichage d'un compte à rebours. */
data class DurationParts(val days: Long, val hours: Long, val minutes: Long, val seconds: Long) {
    companion object {
        fun of(totalSeconds: Long): DurationParts {
            val total = totalSeconds.coerceAtLeast(0)
            return DurationParts(
                days = total / 86_400,
                hours = total % 86_400 / 3_600,
                minutes = total % 3_600 / 60,
                seconds = total % 60,
            )
        }
    }
}
