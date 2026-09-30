package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.ProtectionStateDto
import java.time.Instant
import java.time.format.DateTimeParseException

/** État de la protection (filtrage DNS) de l'instance OnyxFilter. */
data class ProtectionStatus(
    val enabled: Boolean,
    /**
     * Fin de la désactivation temporaire, exprimée selon l'horloge du téléphone ; `null` si la
     * protection est active ou désactivée sans échéance.
     */
    val disabledUntil: Instant?,
) {
    val isDisabledIndefinitely: Boolean get() = !enabled && disabledUntil == null

    val isDisabledTemporarily: Boolean get() = !enabled && disabledUntil != null

    /** Secondes restantes avant la réactivation automatique (0 si aucune n'est programmée). */
    fun remainingSeconds(now: Instant): Long {
        val until = disabledUntil ?: return 0
        val millis = until.toEpochMilli() - now.toEpochMilli()
        return if (millis <= 0) 0 else (millis + 999) / 1000
    }

    companion object {
        /**
         * @param now instant de réception de la réponse : la fin de la désactivation est calculée à
         * partir de la durée restante fournie par le serveur, pour ne pas dépendre d'un éventuel
         * décalage entre l'horloge du serveur et celle du téléphone.
         */
        fun fromDto(dto: ProtectionStateDto, now: Instant): ProtectionStatus {
            if (dto.enabled) return ProtectionStatus(enabled = true, disabledUntil = null)

            val until = when {
                dto.remainingSeconds != null -> now.plusSeconds(dto.remainingSeconds)
                dto.disabledUntilUtc != null -> parseInstant(dto.disabledUntilUtc)
                else -> null
            }
            return ProtectionStatus(enabled = false, disabledUntil = until)
        }

        private fun parseInstant(value: String): Instant? = try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
