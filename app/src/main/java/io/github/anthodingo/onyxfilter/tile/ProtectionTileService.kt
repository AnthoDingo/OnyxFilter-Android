package io.github.anthodingo.onyxfilter.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.anthodingo.onyxfilter.MainActivity
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.ui.formatShortReEnableTime
import io.github.anthodingo.onyxfilter.widget.WidgetController
import io.github.anthodingo.onyxfilter.widget.WidgetModel
import io.github.anthodingo.onyxfilter.widget.WidgetTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Tuile des réglages rapides : active ou désactive le filtrage d'un toucher (désactivation sans limite
 * de durée). Un appui long ouvre l'application, pour une désactivation temporaire. Même état et mêmes
 * actions que les widgets ([WidgetController]).
 */
class ProtectionTileService : TileService() {

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        WidgetController.refresh(this)
    }

    // Volet des réglages rapides ouvert : la tuile suit l'état et le relit s'il date un peu.
    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            WidgetController.state(this@ProtectionTileService).filterNotNull().collect(::render)
        }
        WidgetController.refreshIfStale(this)
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val command = WidgetController.currentModel(this).toggleCommand
        if (command == null) {
            openApp()
            return
        }

        // Pas de coupure du filtrage depuis l'écran de verrouillage : l'appareil doit d'abord être déverrouillé.
        if (isLocked) {
            unlockAndRun { WidgetController.execute(this, command) }
        } else {
            WidgetController.execute(this, command)
        }
    }

    private fun render(model: WidgetModel) {
        val tile = qsTile ?: return
        val content = model.toTileContent()

        tile.state = if (content.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        val icon = if (model.tone == WidgetTone.Disabled) R.drawable.ic_tile_shield_off else R.drawable.ic_tile_shield
        tile.icon = Icon.createWithResource(this, icon)

        val subtitle = subtitle(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = subtitle
        }
        tile.updateTile()
    }

    private fun subtitle(content: TileContent): String {
        val until = content.disabledUntil
        return when (content.subtitle) {
            TileSubtitle.LoggedOut -> getString(R.string.tile_logged_out)
            TileSubtitle.Loading -> getString(R.string.widget_loading)
            TileSubtitle.Updating -> getString(R.string.tile_updating)
            TileSubtitle.Offline -> getString(R.string.widget_state_offline)
            TileSubtitle.Enabled -> getString(R.string.widget_state_enabled)
            TileSubtitle.DisabledIndefinitely -> getString(R.string.widget_state_disabled)
            TileSubtitle.DisabledUntil ->
                if (until != null) {
                    getString(R.string.tile_disabled_until, formatShortReEnableTime(this, until, Instant.now()))
                } else {
                    getString(R.string.widget_state_disabled)
                }
        }
    }

    private fun openApp() {
        val intent = Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            // Seule variante disponible avant Android 14 (celle à PendingIntent n'existe pas encore).
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
