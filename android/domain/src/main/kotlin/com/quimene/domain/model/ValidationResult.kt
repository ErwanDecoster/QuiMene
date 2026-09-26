package com.quimene.domain.model

import java.util.UUID

/** Miroir de `ValidationResult.swift` — pas de sérialisation, résultat de calcul en mémoire
 * uniquement (jamais transmis ni persisté). Aucun texte d'interface ici : erreurs et
 * avertissements portent une raison typée, que l'app rédige dans la langue de l'utilisateur
 * (`:domain` n'a accès à aucune ressource de traduction, ADR-0002). */
sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Warning(
        val warnings: List<ValidationWarning>,
    ) : ValidationResult

    data class Invalid(
        val errors: List<ValidationError>,
    ) : ValidationResult
}

/** Saisie acceptée mais inhabituelle : à signaler, sans empêcher de valider (doc 04). */
enum class ValidationWarning {
    /** Score hors de la plage habituelle du jeu (`warnBelow`/`warnAbove`, extrêmes du Skyjo). */
    UnusualScore,
}

/** Miroir de `ValidationError.swift`. */
data class ValidationError(
    val field: Field,
    val reason: Reason,
) {
    sealed interface Field {
        data class ParticipantField(
            val participantID: UUID,
        ) : Field

        data class ModifierField(
            val modifierID: ModifierID,
        ) : Field

        data object General : Field
    }

    /** Pourquoi la saisie est refusée — miroir de `ValidationError.Reason`. */
    sealed interface Reason {
        /** Score sous le minimum de la définition du jeu. */
        data class ScoreBelowMinimum(
            val min: Int,
        ) : Reason

        /** Score au-dessus du maximum de la définition du jeu. */
        data class ScoreAboveMaximum(
            val max: Int,
        ) : Reason

        /** Skyjo : exactement un joueur ferme la manche. */
        data object SingleCloserRequired : Reason

        /** Belote : deux équipes par donne. */
        data object TwoTeamsRequired : Reason

        /** Belote : une seule équipe preneuse par donne. */
        data object SingleTakingTeamRequired : Reason

        /** Belote, Tarot : points du preneur hors de 0..[max]. */
        data class TakerPointsOutOfRange(
            val max: Int,
        ) : Reason

        /** Tarot : un preneur, ou la donne marquée passée. */
        data object TakerRequired : Reason

        /** Tarot à 5 : un partenaire (roi appelé), distinct du preneur. */
        data object PartnerRequired : Reason

        /** Tarot : contrat, bouts ou poignée hors des valeurs possibles. */
        data object InvalidTarotHand : Reason

        /** Wizard : plis réalisés hors de 0..[max]. */
        data class TricksOutOfRange(
            val max: Int,
        ) : Reason

        /** Wizard : annonce hors de 0..[max]. */
        data class BidOutOfRange(
            val max: Int,
        ) : Reason

        /** Wizard : le total des plis réalisés doit égaler le numéro de la manche. */
        data class TricksTotalMismatch(
            val total: Int,
            val expected: Int,
        ) : Reason

        /** Yams : une seule catégorie remplie par tour. */
        data object SingleCategoryPerTurn : Reason

        /** Yams : catégorie absente de la grille. */
        data object UnknownCategory : Reason

        /** Yams : catégorie déjà remplie. */
        data object CategoryAlreadyFilled : Reason

        /** Yams : nombre de dés hors de 0..5. */
        data object InvalidDiceCount : Reason

        /** Yams : une figure vaut 0 (ratée) ou 1 (réussie). */
        data object InvalidFigureValue : Reason

        /** Yams : somme de dés impossible. */
        data object InvalidDiceSum : Reason
    }
}
