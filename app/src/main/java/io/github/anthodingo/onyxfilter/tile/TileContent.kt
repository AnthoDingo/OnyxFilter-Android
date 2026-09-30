package io.github.anthodingo.onyxfilter.tile

import io.github.anthodingo.onyxfilter.widget.WidgetModel
import io.github.anthodingo.onyxfilter.widget.WidgetTone
import java.time.Instant

/** Ligne d'état affichée sous le nom de la tuile (Android 10+). */
enum class TileSubtitle { LoggedOut, Loading, Updating, Offline, Enabled, DisabledIndefinitely, DisabledUntil }

/** Contenu de la tuile des réglages rapides. */
data class TileContent(
    /** Tuile allumée : filtrage actif. */
    val active: Boolean,
    val subtitle: TileSubtitle,
    /** Fin de la désactivation temporaire, pour [TileSubtitle.DisabledUntil]. */
    val disabledUntil: Instant? = null,
)

fun WidgetModel.toTileContent(): TileContent {
    val subtitle = when {
        !loggedIn -> TileSubtitle.LoggedOut
        isUpdating -> TileSubtitle.Updating
        hasError -> TileSubtitle.Offline
        status == null -> TileSubtitle.Loading
        status.enabled -> TileSubtitle.Enabled
        status.disabledUntil == null -> TileSubtitle.DisabledIndefinitely
        else -> TileSubtitle.DisabledUntil
    }
    return TileContent(
        active = tone == WidgetTone.Enabled,
        subtitle = subtitle,
        disabledUntil = status?.disabledUntil.takeIf { subtitle == TileSubtitle.DisabledUntil },
    )
}
