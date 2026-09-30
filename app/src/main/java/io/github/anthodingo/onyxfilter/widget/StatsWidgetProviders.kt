package io.github.anthodingo.onyxfilter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Widget 2 × 2 : requêtes des dernières 24 heures et part bloquée. */
class StatsSummaryWidget : StatsWidgetProvider()

/** Widget 4 × 2 : chiffres clés et requêtes heure par heure sur 24 heures. */
class StatsActivityWidget : StatsWidgetProvider()

/** Les deux widgets partagent le même état ([StatsWidgetController]). */
abstract class StatsWidgetProvider : AppWidgetProvider() {

    // Ajout d'un widget, actualisation périodique (updatePeriodMillis) ou redémarrage du téléphone.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        finishWhenDone(StatsWidgetController.refresh(context, showProgress = false))
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        StatsWidgetController.onSizeChanged(context)
    }
}
