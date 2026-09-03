package com.cacompte.sync

import com.cacompte.domain.engine.StampedEvent
import com.cacompte.domain.model.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Miroir de `WireMessage.swift` — protocole applicatif, indépendant du transport actif
 * (`SupabaseTransport`, voir [TransportSession]). `welcome` porte le journal complet plutôt qu'un
 * type d'instantané séparé : le pair qui rejoint appelle `MatchEngine.replay`, la même fonction
 * qu'au lancement de l'app — une seule façon de reconstruire un `MatchState`.
 */
@Serializable
data class WireMessage(
    val protocolVersion: Int = 1,
    /** Identifiant de la **session de partage**, stable tant qu'elle dure — pas de la partie
     * courante (`matchID`), qui peut changer plusieurs fois sans jamais rouvrir la connexion ni
     * changer de clé (doc 09 « Fin de partie »). */
    @Serializable(with = UUIDSerializer::class)
    val sessionID: UUID,
    val kind: Kind,
) {
    @Serializable
    enum class Platform {
        @SerialName("apple")
        Apple,

        @SerialName("android")
        Android,
    }

    /** Miroir de `WireMessage.Kind` — sérialisé via [WireMessageKindSerializer], forme exacte du
     * `Codable` synthétisé par Swift (voir ce sérialiseur), vérifiée bit-à-bit contre les 8
     * golden files du dossier `spec/wire`. */
    @Serializable(with = WireMessageKindSerializer::class)
    sealed interface Kind {
        /** `deviceID` (le même que celui qui horodate ses `StampedEvent`) permet à l'hôte de
         * relier « cette manche vient de X » à « X, c'est Théo » — sans lui, seul un UUID
         * technique accompagne chaque manche distante, impossible à attribuer à un pair affiché. */
        data class Hello(
            val deviceName: String,
            val appVersion: String,
            val platform: Platform,
            val role: Role,
            val deviceID: String,
        ) : Kind

        data class Welcome(
            val log: List<StampedEvent>,
            val role: Role,
        ) : Kind

        data class Events(
            val events: List<StampedEvent>,
        ) : Kind

        /** L'hôte enchaîne une nouvelle partie (même jeu rejoué ou jeu différent) sans rompre la
         * session : diffusé à tous les pairs déjà connectés, contrairement à [Welcome] qui ne
         * sert qu'au pair qui vient de rejoindre. Porte le journal complet de la nouvelle partie
         * — un pair qui le reçoit doit repartir d'un journal vide plutôt que d'y ajouter les
         * événements (ils appartiennent à une autre partie). */
        data class MatchChanged(
            val log: List<StampedEvent>,
        ) : Kind

        data class Proposal(
            val events: List<StampedEvent>,
        ) : Kind

        data class Rejection(
            val eventID: UUID,
            val reason: String,
        ) : Kind

        data class Heartbeat(
            val lamport: ULong,
        ) : Kind

        data object Goodbye : Kind
    }
}
