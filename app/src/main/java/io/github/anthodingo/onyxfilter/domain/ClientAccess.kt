package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.AccessListsDto
import io.github.anthodingo.onyxfilter.data.ClientAccessChangeDto

/** Accès des clients au DNS : clients refusés (adresses IP ou sous-réseaux) et mode en vigueur. */
data class ClientAccess(
    val blockedClients: List<String>,
    /** Seuls les clients autorisés sont servis : les clients refusés sont alors ignorés par le serveur. */
    val isAllowlistMode: Boolean,
) {
    companion object {
        fun fromDto(dto: AccessListsDto): ClientAccess = of(dto.mode, dto.blockedClients)

        fun fromDto(dto: ClientAccessChangeDto): ClientAccess = of(dto.mode, dto.blockedClients)

        private fun of(mode: String, blocked: List<String>) = ClientAccess(blocked, mode.equals("Allowlist", ignoreCase = true))
    }
}
