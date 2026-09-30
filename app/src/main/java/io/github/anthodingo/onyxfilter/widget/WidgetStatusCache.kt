package io.github.anthodingo.onyxfilter.widget

import android.content.Context
import androidx.core.content.edit
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import java.time.Instant

/**
 * Dernier état affiché par les widgets, conservé entre deux démarrages de l'application : sans lui, un
 * rafraîchissement périodique qui échoue (serveur injoignable) n'aurait plus rien à afficher.
 */
internal class WidgetStatusCache(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ProtectionStatus? {
        if (!preferences.contains(KEY_ENABLED)) return null
        val until = preferences.getLong(KEY_DISABLED_UNTIL, NO_END)
        return ProtectionStatus(
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            disabledUntil = if (until == NO_END) null else Instant.ofEpochMilli(until),
        )
    }

    fun save(status: ProtectionStatus?) {
        preferences.edit {
            if (status == null) {
                clear()
            } else {
                putBoolean(KEY_ENABLED, status.enabled)
                putLong(KEY_DISABLED_UNTIL, status.disabledUntil?.toEpochMilli() ?: NO_END)
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "widgets"
        const val KEY_ENABLED = "enabled"
        const val KEY_DISABLED_UNTIL = "disabled_until"
        const val NO_END = -1L
    }
}
