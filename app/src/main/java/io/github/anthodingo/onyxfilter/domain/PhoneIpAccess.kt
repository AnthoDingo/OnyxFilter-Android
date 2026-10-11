package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.CallerAccessDto

/**
 * Adresse publique du téléphone vue par le serveur, et décision du DNS pour elle.
 *
 * @property rule règle en cause ("203.0.113.0/24"), `null` si aucune ne concerne l'adresse.
 */
data class PhoneIpAccess(val ip: String, val allowed: Boolean, val rule: String?) {
    companion object {
        fun fromDto(dto: CallerAccessDto): PhoneIpAccess = PhoneIpAccess(dto.ip, dto.allowed, dto.rule?.takeIf { it.isNotBlank() })
    }
}
