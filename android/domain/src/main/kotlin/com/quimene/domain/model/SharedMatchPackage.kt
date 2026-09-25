package com.quimene.domain.model

import com.quimene.domain.engine.StampedEvent
import kotlinx.serialization.Serializable
import java.util.UUID

/** Doc 16, phase E — miroir de `SharedMatchPackage.swift` : une partie terminée, **complète**
 * (journal d'événements et fiches de ses joueurs), telle que déposée dans la boîte aux lettres
 * d'un ami lié qui y a joué (`MailboxCrypto`, :sync). Remplace [SharedMatchSummaryPayload]. */
@Serializable
data class SharedMatchPackage(
    @Serializable(with = UUIDSerializer::class) val matchID: UUID,
    val participants: List<Participant>,
    /** Le journal complet, prêt pour `MatchEngine.replay` : la partie se relit à l'identique. */
    val events: List<StampedEvent>,
) {
    @Serializable
    data class Participant(
        @Serializable(with = UUIDSerializer::class) val participantID: UUID,
        /** `null` pour un joueur sans profil lié chez l'expéditeur. */
        @Serializable(with = UUIDSerializer::class) val sharedProfileID: UUID? = null,
        val nickname: String,
        val avatarKind: String,
        val avatarValue: String,
        val paletteID: String,
    )
}
