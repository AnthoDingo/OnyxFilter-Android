package io.github.anthodingo.onyxfilter.widget

import io.github.anthodingo.onyxfilter.domain.DisableDuration
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus

/** Couleur d'ensemble d'un widget. */
enum class WidgetTone { Enabled, Disabled, Neutral }

/** Contenu affiché par les widgets. */
data class WidgetModel(
    val loggedIn: Boolean = false,
    /** Dernier état connu, `null` s'il n'a encore jamais été lu. */
    val status: ProtectionStatus? = null,
    /** Action en cours depuis un widget. */
    val isUpdating: Boolean = false,
    /** Échec du dernier appel lancé depuis un widget : [status] n'est peut-être plus à jour. */
    val hasError: Boolean = false,
) {
    val tone: WidgetTone
        get() = when {
            !loggedIn || status == null -> WidgetTone.Neutral
            status.enabled -> WidgetTone.Enabled
            else -> WidgetTone.Disabled
        }

    /**
     * Action du widget "bascule" : l'inverse de l'état affiché, ou une actualisation si cet état est
     * inconnu ou douteux ; `null` pour ouvrir l'application (connexion requise).
     */
    val toggleCommand: WidgetCommand?
        get() = when {
            !loggedIn -> null
            status == null || hasError -> WidgetCommand.Refresh
            status.enabled -> WidgetCommand.Disable(DisableDuration.Indefinitely)
            else -> WidgetCommand.Enable
        }

    /** Action du bouton principal du widget "contrôle" (désactiver ou réactiver). */
    val primaryCommand: WidgetCommand?
        get() = when {
            !loggedIn || status == null -> null
            status.enabled -> WidgetCommand.Disable(DisableDuration.Indefinitely)
            else -> WidgetCommand.Enable
        }
}

/** Action déclenchée depuis un widget, transmise au [WidgetActionReceiver] sous forme de texte. */
sealed interface WidgetCommand {
    data object Refresh : WidgetCommand

    data object Enable : WidgetCommand

    data class Disable(val duration: DisableDuration) : WidgetCommand

    fun encode(): String = when (this) {
        Refresh -> REFRESH
        Enable -> ENABLE
        is Disable -> when (duration) {
            DisableDuration.Indefinitely -> "$DISABLE/$INDEFINITELY"
            DisableDuration.UntilTomorrow -> "$DISABLE/$TOMORROW"
            is DisableDuration.Fixed -> "$DISABLE/${duration.seconds}"
        }
    }

    companion object {
        private const val REFRESH = "refresh"
        private const val ENABLE = "enable"
        private const val DISABLE = "disable"
        private const val INDEFINITELY = "indefinitely"
        private const val TOMORROW = "tomorrow"

        /** Actions des boutons de durée du widget "contrôle". */
        val QuickDisable: List<Disable> = listOf(
            Disable(DisableDuration.Fixed(10 * 60)),
            Disable(DisableDuration.Fixed(60 * 60)),
            Disable(DisableDuration.UntilTomorrow),
        )

        /** @return la commande, ou `null` si [value] n'en décrit aucune. */
        fun decode(value: String?): WidgetCommand? {
            if (value == null) return null
            if (value == REFRESH) return Refresh
            if (value == ENABLE) return Enable
            if (!value.startsWith("$DISABLE/")) return null

            val duration = when (val argument = value.removePrefix("$DISABLE/")) {
                INDEFINITELY -> DisableDuration.Indefinitely
                TOMORROW -> DisableDuration.UntilTomorrow
                else -> argument.toLongOrNull()
                    ?.takeIf { it in 1..DisableDuration.MAX_SECONDS }
                    ?.let(DisableDuration::Fixed)
                    ?: return null
            }
            return Disable(duration)
        }
    }
}
