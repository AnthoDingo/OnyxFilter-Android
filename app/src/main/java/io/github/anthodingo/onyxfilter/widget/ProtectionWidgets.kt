package io.github.anthodingo.onyxfilter.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.ui.formatReEnableTime
import io.github.anthodingo.onyxfilter.ui.formatShortReEnableTime
import java.time.Instant

/** Affichage des widgets de l'écran d'accueil à partir d'un [WidgetModel]. */
internal object ProtectionWidgets {

    // Laisse au serveur le temps de réactiver la protection avant de relire son état.
    private const val RE_ENABLE_GRACE_MILLIS = 2_000L

    fun render(context: Context, model: WidgetModel) {
        val manager = AppWidgetManager.getInstance(context)
        val now = Instant.now()

        val toggleIds = manager.getAppWidgetIds(ComponentName(context, ProtectionToggleWidget::class.java))
        if (toggleIds.isNotEmpty()) {
            manager.updateAppWidget(toggleIds, toggleViews(context, model, now))
        }

        val controlIds = manager.getAppWidgetIds(ComponentName(context, ProtectionControlWidget::class.java))
        if (controlIds.isNotEmpty()) {
            manager.updateAppWidget(controlIds, controlViews(context, model, now))
        }

        scheduleReEnableRefresh(context, model, now, hasWidgets = toggleIds.isNotEmpty() || controlIds.isNotEmpty())
    }

    // Widget 1 × 1 : l'état en un coup d'œil, un toucher pour basculer.
    private fun toggleViews(context: Context, model: WidgetModel, now: Instant): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_toggle)
        applyTone(views, model)

        val status = model.status
        val label = when {
            !model.loggedIn -> R.string.widget_state_logged_out
            status == null -> R.string.app_name
            model.hasError -> R.string.widget_state_offline
            status.enabled -> R.string.widget_state_enabled
            else -> R.string.widget_state_disabled
        }
        views.setTextViewText(R.id.widget_label, context.getString(label))

        val until = status?.disabledUntil
        val sublabel = if (model.loggedIn && !model.hasError && until != null) formatShortReEnableTime(context, until, now) else null
        views.setTextViewText(R.id.widget_sublabel, sublabel.orEmpty())
        views.setViewVisibility(R.id.widget_sublabel, if (sublabel != null) View.VISIBLE else View.GONE)

        val command = model.toggleCommand
        views.setOnClickPendingIntent(android.R.id.background, command?.let { widgetCommandIntent(context, it) } ?: openAppIntent(context))
        views.setContentDescription(android.R.id.background, context.getString(toggleDescription(command)))
        return views
    }

    // Widget 4 × 2 : état détaillé, désactivation temporaire et bouton principal.
    private fun controlViews(context: Context, model: WidgetModel, now: Instant): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_control)
        applyTone(views, model)

        val status = model.status
        val title = when {
            !model.loggedIn || status == null -> R.string.app_name
            status.enabled -> R.string.protection_enabled
            else -> R.string.protection_disabled
        }
        views.setTextViewText(R.id.widget_title, context.getString(title))

        val until = status?.disabledUntil
        val subtitle = when {
            !model.loggedIn -> context.getString(R.string.widget_logged_out)
            model.hasError -> context.getString(R.string.widget_error)
            status == null -> context.getString(R.string.widget_loading)
            status.enabled -> context.getString(R.string.protection_enabled_description)
            until == null -> context.getString(R.string.disable_indefinitely_description)
            else -> context.getString(R.string.widget_re_enable, formatReEnableTime(context, until, now))
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle)

        // En-tête : réessaie si l'état est inconnu ou douteux, ouvre l'application sinon.
        val headerCommand = if (model.loggedIn && (status == null || model.hasError)) WidgetCommand.Refresh else null
        views.setOnClickPendingIntent(R.id.widget_header, headerCommand?.let { widgetCommandIntent(context, it) } ?: openAppIntent(context))

        val primary = model.primaryCommand
        views.setViewVisibility(R.id.widget_actions, if (primary != null) View.VISIBLE else View.GONE)
        if (primary != null) {
            QuickDisableButtons.zip(WidgetCommand.QuickDisable).forEach { (viewId, command) ->
                views.setOnClickPendingIntent(viewId, widgetCommandIntent(context, command))
            }
            val disables = primary is WidgetCommand.Disable
            views.setTextViewText(R.id.widget_action_primary, context.getString(if (disables) R.string.widget_disable else R.string.widget_enable))
            views.setInt(
                R.id.widget_action_primary,
                "setBackgroundResource",
                if (disables) R.drawable.widget_button_disable else R.drawable.widget_button_enable,
            )
            views.setOnClickPendingIntent(R.id.widget_action_primary, widgetCommandIntent(context, primary))
        }
        return views
    }

    // Dans le même ordre que WidgetCommand.QuickDisable.
    private val QuickDisableButtons = listOf(
        R.id.widget_action_10_min,
        R.id.widget_action_1_h,
        R.id.widget_action_tomorrow,
    )

    private fun applyTone(views: RemoteViews, model: WidgetModel) {
        val (background, icon) = when (model.tone) {
            WidgetTone.Enabled -> R.drawable.widget_background_enabled to R.drawable.ic_shield_check
            WidgetTone.Disabled -> R.drawable.widget_background_disabled to R.drawable.ic_shield_off
            WidgetTone.Neutral -> R.drawable.widget_background_neutral to R.drawable.ic_shield_outline
        }
        views.setInt(android.R.id.background, "setBackgroundResource", background)
        views.setImageViewResource(R.id.widget_icon, icon)
        views.setViewVisibility(R.id.widget_icon, if (model.isUpdating) View.INVISIBLE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_progress, if (model.isUpdating) View.VISIBLE else View.GONE)
    }

    @StringRes
    private fun toggleDescription(command: WidgetCommand.Protection?): Int = when (command) {
        null -> R.string.widget_cd_open
        WidgetCommand.Refresh -> R.string.widget_cd_refresh
        WidgetCommand.Enable -> R.string.widget_cd_toggle_enable
        is WidgetCommand.Disable -> R.string.widget_cd_toggle_disable
    }

    // Relit l'état juste après la fin d'une désactivation temporaire, pour que les widgets n'affichent
    // pas "désactivée" alors que le serveur a déjà réactivé la protection.
    private fun scheduleReEnableRefresh(context: Context, model: WidgetModel, now: Instant, hasWidgets: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val refresh = widgetCommandIntent(context, WidgetCommand.Refresh)
        val until = model.status?.disabledUntil

        // Échéance déjà passée (état en cache, serveur injoignable...) : pas d'alarme, sinon elle se
        // déclencherait aussitôt, en boucle.
        if (!hasWidgets || !model.loggedIn || until == null || !until.isAfter(now)) {
            alarmManager.cancel(refresh)
            return
        }

        // Alarme inexacte, sans permission particulière : l'actualisation peut être un peu différée.
        alarmManager.set(AlarmManager.RTC, until.toEpochMilli() + RE_ENABLE_GRACE_MILLIS, refresh)
    }
}
