package io.github.anthodingo.onyxfilter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.Column
import io.github.anthodingo.onyxfilter.domain.ColumnChart
import io.github.anthodingo.onyxfilter.domain.DnsStats
import io.github.anthodingo.onyxfilter.domain.StatsFormat
import io.github.anthodingo.onyxfilter.ui.formatTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Affichage des widgets de statistiques à partir d'un [StatsWidgetModel]. */
internal object StatsWidgets {

    // Géométrie de l'histogramme (en dp) : colonnes de 24 au plus, séparées de 2, extrémité arrondie de
    // 4 et base carrée, hauteur minimale de 2 pour qu'une petite valeur reste visible.
    private const val CHART_HEIGHT_DP = 64
    private const val COLUMN_GAP_DP = 2f
    private const val COLUMN_MAX_WIDTH_DP = 24f
    private const val COLUMN_MIN_HEIGHT_DP = 2f
    private const val COLUMN_RADIUS_DP = 4f

    // Marges horizontales du widget « Activité » (padding de widget_stats_activity.xml).
    private const val ACTIVITY_HORIZONTAL_PADDING_DP = 24
    private const val DEFAULT_ACTIVITY_WIDTH_DP = 250

    fun render(context: Context, model: StatsWidgetModel) {
        val manager = AppWidgetManager.getInstance(context)

        val summaryIds = manager.getAppWidgetIds(ComponentName(context, StatsSummaryWidget::class.java))
        if (summaryIds.isNotEmpty()) {
            manager.updateAppWidget(summaryIds, summaryViews(context, model))
        }

        // Une mise en page par widget : l'histogramme est dessiné à la largeur de chacun.
        for (id in manager.getAppWidgetIds(ComponentName(context, StatsActivityWidget::class.java))) {
            val widthDp = manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_ACTIVITY_WIDTH_DP)
                .takeIf { it > 0 } ?: DEFAULT_ACTIVITY_WIDTH_DP
            manager.updateAppWidget(id, activityViews(context, model, widthDp))
        }
    }

    // Widget 2 × 2 : le nombre de requêtes des dernières 24 heures, et la part bloquée.
    private fun summaryViews(context: Context, model: StatsWidgetModel): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_stats_summary)
        bindCommon(context, views, model)

        val stats = model.stats
        views.setViewVisibility(R.id.stats_values, if (stats != null) View.VISIBLE else View.GONE)
        if (stats != null) {
            val locale = locale(context)
            views.setTextViewText(R.id.stats_total, StatsFormat.count(stats.totalQueries, locale))
            views.setTextViewText(
                R.id.stats_blocked,
                context.resources.getQuantityString(
                    R.plurals.stats_blocked_line,
                    stats.blockedQueries.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    StatsFormat.count(stats.blockedQueries, locale),
                    StatsFormat.percent(stats.blockedRatio, locale),
                ),
            )
        }
        return views
    }

    // Widget 4 × 2 : chiffres clés et requêtes heure par heure.
    private fun activityViews(context: Context, model: StatsWidgetModel, widthDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_stats_activity)
        bindCommon(context, views, model)

        val stats = model.stats
        views.setViewVisibility(R.id.stats_values, if (stats != null) View.VISIBLE else View.GONE)
        if (stats == null) return views

        val locale = locale(context)
        views.setTextViewText(R.id.stats_total, StatsFormat.count(stats.totalQueries, locale))
        views.setTextViewText(R.id.stats_blocked, StatsFormat.count(stats.blockedQueries, locale))
        views.setTextViewText(R.id.stats_ratio, StatsFormat.percent(stats.blockedRatio, locale))

        val hasTraffic = ColumnChart.peakIndex(stats.hourlyQueries) != null
        val chartVisibility = if (hasTraffic) View.VISIBLE else View.INVISIBLE
        views.setViewVisibility(R.id.stats_chart, chartVisibility)
        views.setViewVisibility(R.id.stats_chart_blocked, chartVisibility)
        views.setViewVisibility(R.id.stats_chart_empty, if (hasTraffic) View.GONE else View.VISIBLE)
        if (hasTraffic) {
            val (allowed, blocked) = chartBitmaps(context, stats, widthDp - ACTIVITY_HORIZONTAL_PADDING_DP)
            views.setImageViewBitmap(R.id.stats_chart, allowed)
            views.setImageViewBitmap(R.id.stats_chart_blocked, blocked)
            tintChart(context, views, R.id.stats_chart, R.color.widget_chart_allowed)
            tintChart(context, views, R.id.stats_chart_blocked, R.color.widget_chart_blocked)
            views.setContentDescription(R.id.stats_chart, chartDescription(context, stats))
        }
        return views
    }

    // En-tête, bouton d'actualisation, ligne d'état et toucher : communs aux deux widgets.
    private fun bindCommon(context: Context, views: RemoteViews, model: StatsWidgetModel) {
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))

        views.setViewVisibility(R.id.stats_refresh, if (model.loggedIn && !model.isUpdating) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.stats_progress, if (model.isUpdating) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.stats_refresh, widgetCommandIntent(context, WidgetCommand.RefreshStats))

        val fetchedAt = model.fetchedAt
        val status = when {
            !model.loggedIn -> context.getString(R.string.widget_logged_out)
            model.stats == null && model.hasError -> context.getString(R.string.stats_unreachable)
            model.stats == null -> context.getString(R.string.widget_loading)
            fetchedAt == null -> ""
            model.hasError -> context.getString(R.string.stats_offline_since, formatTime(context, fetchedAt))
            else -> context.getString(R.string.stats_updated_at, formatTime(context, fetchedAt))
        }
        views.setTextViewText(R.id.stats_status, status)
    }

    // Deux masques blancs superposés, teintés par le widget : colonnes entières (autorisées) et leur
    // part bloquée, en bas. Les couleurs suivent le thème du lanceur sans redessiner l'image (Android 12+).
    private fun chartBitmaps(context: Context, stats: DnsStats, widthDp: Int): Pair<Bitmap, Bitmap> {
        val density = context.resources.displayMetrics.density
        val width = (widthDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val height = (CHART_HEIGHT_DP * density).roundToInt()
        val allowed = createBitmap(width, height)
        val blocked = createBitmap(width, height)
        val allowedCanvas = Canvas(allowed)
        val blockedCanvas = Canvas(blocked)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val radius = COLUMN_RADIUS_DP * density

        ColumnChart.layout(
            values = stats.hourlyQueries,
            blocked = stats.hourlyBlocked,
            width = width.toFloat(),
            height = height.toFloat(),
            gap = COLUMN_GAP_DP * density,
            maxWidth = COLUMN_MAX_WIDTH_DP * density,
            minHeight = COLUMN_MIN_HEIGHT_DP * density,
        ).forEach { column ->
            drawColumn(allowedCanvas, column, radius, paint)
            // Même forme, limitée à la part bloquée : elle garde l'extrémité arrondie si tout est bloqué.
            if (column.blockedTop < column.bottom) {
                blockedCanvas.withClip(column.left, column.blockedTop, column.right, column.bottom) {
                    drawColumn(this, column, radius, paint)
                }
            }
        }
        return allowed to blocked
    }

    // Extrémité arrondie, base carrée : rectangle arrondi, puis sa moitié basse redessinée à angles droits.
    private fun drawColumn(canvas: Canvas, column: Column, radius: Float, paint: Paint) {
        val r = minOf(radius, (column.right - column.left) / 2, column.bottom - column.top)
        canvas.drawRoundRect(column.left, column.top, column.right, column.bottom, r, r, paint)
        canvas.drawRect(column.left, maxOf(column.top, column.bottom - r), column.right, column.bottom, paint)
    }

    private fun tintChart(context: Context, views: RemoteViews, @IdRes viewId: Int, @ColorRes color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorInt(
                viewId,
                "setColorFilter",
                themedColor(context, color, night = false),
                themedColor(context, color, night = true),
            )
        } else {
            views.setInt(viewId, "setColorFilter", context.getColor(color))
        }
    }

    private fun themedColor(context: Context, @ColorRes color: Int, night: Boolean): Int {
        val configuration = Configuration(context.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return context.createConfigurationContext(configuration).getColor(color)
    }

    // Lecteur d'écran : l'image ne dit rien d'elle-même, le pic d'activité la résume.
    private fun chartDescription(context: Context, stats: DnsStats): String {
        val peak = ColumnChart.peakIndex(stats.hourlyQueries) ?: return context.getString(R.string.stats_chart_empty)
        val lastHour = stats.lastHourStart
        val count = StatsFormat.count(stats.hourlyQueries[peak], locale(context))
        if (lastHour == null) return context.getString(R.string.stats_chart_description, count)
        val peakHour: Instant = lastHour.minus((stats.hourlyQueries.lastIndex - peak).toLong(), ChronoUnit.HOURS)
        return context.getString(R.string.stats_chart_description_at, count, formatTime(context, peakHour))
    }

    private fun locale(context: Context) = context.resources.configuration.locales[0]
}
