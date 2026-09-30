package io.github.anthodingo.onyxfilter.ui

import android.content.Context
import android.text.format.DateFormat
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.RelativeDay
import io.github.anthodingo.onyxfilter.domain.relativeDay
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/** Heure seule, au format 12 h/24 h choisi sur le téléphone. */
fun formatTime(context: Context, instant: Instant): String = DateFormat.getTimeFormat(context).format(Date.from(instant))

/** Jour abrégé ("mer. 7 oct."), dans la langue du téléphone. */
fun formatShortDay(context: Context, instant: Instant): String {
    val locale = context.resources.configuration.locales[0]
    return DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), Date.from(instant)).toString()
}

/** Moment de la réactivation automatique : "à 14:32", "demain à 00:00" ou "le mer. 7 oct. à 09:15". */
fun formatReEnableTime(context: Context, until: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val time = formatTime(context, until)
    return when (relativeDay(until.atZone(zone), now.atZone(zone))) {
        RelativeDay.Today -> context.getString(R.string.protection_re_enable_today, time)
        RelativeDay.Tomorrow -> context.getString(R.string.protection_re_enable_tomorrow, time)
        RelativeDay.Later -> context.getString(R.string.protection_re_enable_later, formatShortDay(context, until), time)
    }
}

/** Version courte, pour les widgets et la tuile : "14:32", "demain" ou "mer. 7 oct.". */
fun formatShortReEnableTime(context: Context, until: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    return when (relativeDay(until.atZone(zone), now.atZone(zone))) {
        RelativeDay.Today -> formatTime(context, until)
        RelativeDay.Tomorrow -> context.getString(R.string.widget_tomorrow)
        RelativeDay.Later -> formatShortDay(context, until)
    }
}
