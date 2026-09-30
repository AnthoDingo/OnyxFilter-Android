package io.github.anthodingo.onyxfilter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Widget 1 × 1 : bascule de la protection. */
class ProtectionToggleWidget : ProtectionWidgetProvider()

/** Widget 4 × 2 : état de la protection et désactivation temporaire. */
class ProtectionControlWidget : ProtectionWidgetProvider()

/** Les deux widgets partagent le même état ([WidgetController]) : seule leur mise en page diffère. */
abstract class ProtectionWidgetProvider : AppWidgetProvider() {

    // Ajout d'un widget, actualisation périodique (updatePeriodMillis) ou redémarrage du téléphone.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        finishWhenDone(WidgetController.refresh(context))
    }

    override fun onDisabled(context: Context) {
        WidgetController.onWidgetsRemoved(context)
    }
}

/** Reçoit les actions des widgets ([WidgetCommand]). */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMMAND) return
        val command = WidgetCommand.decode(intent.getStringExtra(EXTRA_COMMAND)) ?: return
        finishWhenDone(WidgetController.execute(context, command))
    }

    companion object {
        const val ACTION_COMMAND = "io.github.anthodingo.onyxfilter.widget.COMMAND"
        const val EXTRA_COMMAND = "command"
    }
}
