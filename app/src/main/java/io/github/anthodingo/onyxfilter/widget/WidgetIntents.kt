package io.github.anthodingo.onyxfilter.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.anthodingo.onyxfilter.MainActivity

/** Action d'un widget, reçue par [WidgetActionReceiver]. */
internal fun widgetCommandIntent(context: Context, command: WidgetCommand): PendingIntent {
    val intent = Intent(context, WidgetActionReceiver::class.java)
        .setAction(WidgetActionReceiver.ACTION_COMMAND)
        // Une donnée distincte par commande : sans elle, tous les boutons partageraient le même
        // PendingIntent (les extras ne comptent pas dans leur comparaison).
        .setData(Uri.parse("onyxfilter-widget:${command.encode()}"))
        .putExtra(WidgetActionReceiver.EXTRA_COMMAND, command.encode())
    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Ouvre l'application, comme l'icône du lanceur (la ramène au premier plan si elle est ouverte). */
internal fun openAppIntent(context: Context): PendingIntent {
    val intent = Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
