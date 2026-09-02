package com.cacompte.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Miroir de `Participant.swift`. `Participant.ID` côté Swift = `UUID` (via `Identifiable`) —
 * ici simplement `java.util.UUID`, pas de type enveloppe supplémentaire. */
@Serializable
data class Participant(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val displayName: String,
    val seatIndex: Int,
    /** Nul pour les jeux individuels ; une même valeur = même équipe (la valeur elle-même n'a
     * pas de sens au-delà de l'égalité). */
    val teamID: String? = null,
)
