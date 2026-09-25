package com.quimene.sync

import com.quimene.domain.model.InstantSerializer
import com.quimene.domain.model.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// Doc 16, phase D — miroir de `SessionIdentity.swift` : « Qui es-tu ? ». Qui est qui dans une
// session en ligne, publié dans le même journal chiffré que les parties ([OnlineSession]), sous une
// enveloppe distincte (`{"identity": …}`) : une version de l'app qui ne la connaît pas la saute,
// comme tout événement illisible, sans casser le rejeu des parties. Format commun vérifié par
// `spec/session/identity-events.json`, produit par le code Swift.
//
// Une place se désigne par son siège et son pseudo ([SeatRef]), pas par l'identifiant du
// participant : celui-ci change à chaque partie de la session, le siège et le pseudo restent.

/** Ce qu'un appareil dit de son profil : de quoi le reconnaître ([id], l'identifiant partageable
 * du doc 14) et de quoi lui créer une fiche chez les autres. Jamais de photo. */
@Serializable
data class ProfileCard(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    val avatarKind: String,
    val avatarValue: String,
    val paletteID: String,
)

/** Une place autour de la table, stable d'une partie de la session à la suivante. */
@Serializable
data class SeatRef(
    val seatIndex: Int,
    val displayName: String,
)

/** Une place que la fiche du créateur relie déjà à un profil (un ami lié, ou lui-même). */
@Serializable
data class LinkedSeat(
    val seat: SeatRef,
    @Serializable(with = UUIDSerializer::class) val profileID: UUID,
)

/** Un événement d'identité. Structure plate, même JSON que Swift. */
@Serializable
data class SessionIdentityEvent(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val deviceID: String,
    @Serializable(with = InstantSerializer::class) val occurredAt: Instant = Instant.now(),
    val kind: Kind,
    val seat: SeatRef? = null,
    val profile: ProfileCard? = null,
    @Serializable(with = UUIDSerializer::class) val revokedClaimID: UUID? = null,
    val linkedSeats: List<LinkedSeat>? = null,
) {
    @Serializable
    enum class Kind {
        /** « C'est moi » : [seat] + [profile]. */
        @SerialName("claim")
        Claim,

        /** Annule la revendication [revokedClaimID] — par le créateur, ou par son auteur. */
        @SerialName("revoke")
        Revoke,

        /** Publié par le créateur : son profil ([profile]) et les places que ses fiches relient
         * déjà à un profil ([linkedSeats]). Seul le plus récent compte. */
        @SerialName("roster")
        Roster,
    }

    companion object {
        fun claim(
            seat: SeatRef,
            profile: ProfileCard,
            deviceID: String,
        ) = SessionIdentityEvent(deviceID = deviceID, kind = Kind.Claim, seat = seat, profile = profile)

        fun revoke(
            claimID: UUID,
            deviceID: String,
        ) = SessionIdentityEvent(deviceID = deviceID, kind = Kind.Revoke, revokedClaimID = claimID)

        fun roster(
            owner: ProfileCard?,
            linkedSeats: List<LinkedSeat>,
            deviceID: String,
        ) = SessionIdentityEvent(deviceID = deviceID, kind = Kind.Roster, profile = owner, linkedSeats = linkedSeats)
    }
}

/** L'enveloppe stockée : la clé `identity` la distingue d'un `StampedEvent`. */
@Serializable
internal data class SessionIdentityEnvelope(
    val identity: SessionIdentityEvent,
)

/** Un événement d'identité lisible du journal de la session. */
data class SessionIdentityRecord(
    val seq: Long,
    val event: SessionIdentityEvent,
)

/** Une revendication retenue. */
data class ActiveClaim(
    val claimID: UUID,
    val seat: SeatRef,
    val profile: ProfileCard,
    val deviceID: String,
    val seq: Long,
)

/**
 * Qui occupe quelle place, déduit du journal — le même calcul sur chaque appareil. Mêmes règles
 * que `SessionIdentities` (Swift) :
 * - seul le dernier registre du créateur compte ; une place qu'il relie à un profil est à ce
 *   profil, sans revendication ;
 * - une revendication annulée ne compte plus (annulation par le créateur ou par son auteur) ;
 * - premier arrivé, premier servi : une place déjà revendiquée par un autre profil, ou reliée à
 *   un autre profil par le créateur, ne peut pas l'être ;
 * - un profil n'occupe qu'une place : une nouvelle revendication remplace la précédente, y compris
 *   sa place reliée par le créateur, qui devient alors libre (reconnu d'office sur la mauvaise
 *   fiche, on peut toujours changer de place).
 */
class SessionIdentities(
    records: List<SessionIdentityRecord>,
    ownerDeviceID: String,
) {
    var owner: ProfileCard? = null
        private set
    val linkedSeats: Map<SeatRef, UUID>
    val activeClaims: List<ActiveClaim>

    init {
        val revoked = mutableSetOf<UUID>()
        for (record in records) {
            if (record.event.kind != SessionIdentityEvent.Kind.Revoke) continue
            val claimID = record.event.revokedClaimID ?: continue
            val claim =
                records.firstOrNull {
                    it.event.id == claimID && it.event.kind == SessionIdentityEvent.Kind.Claim
                }
            if (record.event.deviceID == ownerDeviceID || record.event.deviceID == claim?.event?.deviceID) {
                revoked += claimID
            }
        }
        val linked = mutableMapOf<SeatRef, UUID>()
        records
            .lastOrNull { it.event.kind == SessionIdentityEvent.Kind.Roster && it.event.deviceID == ownerDeviceID }
            ?.let { roster ->
                owner = roster.event.profile
                for (seat in roster.event.linkedSeats.orEmpty()) linked[seat.seat] = seat.profileID
            }
        linkedSeats = linked
        val claims = mutableListOf<ActiveClaim>()
        for (record in records) {
            if (record.event.kind != SessionIdentityEvent.Kind.Claim || record.event.id in revoked) continue
            val seat = record.event.seat ?: continue
            val profile = record.event.profile ?: continue
            val linkedProfile = linked[seat]
            if (linkedProfile != null &&
                linkedProfile != profile.id &&
                claims.none { it.profile.id == linkedProfile && it.seat != seat }
            ) {
                continue
            }
            if (claims.any { it.seat == seat && it.profile.id != profile.id }) continue
            claims.removeAll { it.profile.id == profile.id }
            claims += ActiveClaim(record.event.id, seat, profile, record.event.deviceID, record.seq)
        }
        activeClaims = claims
    }

    /** Le profil qui occupe cette place, s'il y en a un. */
    fun occupant(seat: SeatRef): UUID? {
        activeClaims.firstOrNull { it.seat == seat }?.let { return it.profile.id }
        val linked = linkedSeats[seat] ?: return null
        return linked.takeIf { activeClaims.none { it.profile.id == linked } }
    }

    /** La place de ce profil : revendiquée, sinon reliée par le créateur. */
    fun seatOf(profileID: UUID): SeatRef? =
        activeClaims.firstOrNull { it.profile.id == profileID }?.seat
            ?: linkedSeats.entries.firstOrNull { it.value == profileID }?.key

    fun activeClaimOf(profileID: UUID): ActiveClaim? = activeClaims.firstOrNull { it.profile.id == profileID }

    companion object {
        val EMPTY = SessionIdentities(emptyList(), "")
    }
}
