package io.github.anthodingo.onyxfilter.widget

import io.github.anthodingo.onyxfilter.domain.DnsStats
import java.time.Instant

/** Contenu des widgets de statistiques. */
data class StatsWidgetModel(
    val loggedIn: Boolean = false,
    /** Dernières statistiques lues, `null` si elles ne l'ont encore jamais été. */
    val stats: DnsStats? = null,
    /** Moment de la lecture de [stats], affiché pour qu'un chiffre ancien se reconnaisse. */
    val fetchedAt: Instant? = null,
    val isUpdating: Boolean = false,
    /** Échec de la dernière lecture : [stats] n'est peut-être plus à jour. */
    val hasError: Boolean = false,
)
