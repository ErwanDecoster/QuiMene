package com.quimene.app.features.livematch

import com.quimene.app.R
import com.quimene.app.ui.UiText
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.ScoreExplanation
import com.quimene.domain.model.ValidationError
import com.quimene.domain.model.ValidationError.Reason

/**
 * Textes des règles, rédigés ici plutôt que dans `:domain`/`:catalog` (doc 04, ADR-0002) : les
 * moteurs ne produisent qu'une raison typée, l'app la met en mots dans la langue de
 * l'utilisateur. Miroir de `Rules+Messages.swift` côté Apple.
 */
val ValidationError.message: UiText
    get() =
        when (val reason = reason) {
            is Reason.ScoreBelowMinimum ->
                UiText.Resource(
                    R.string.score_sous_le_minimum_autorise_count1,
                    listOf(reason.min),
                )
            is Reason.ScoreAboveMaximum ->
                UiText.Resource(R.string.score_au_dessus_du_maximum_autorise_count1, listOf(reason.max))
            Reason.SingleCloserRequired -> UiText.Resource(R.string.un_seul_joueur_ferme_la_manche)
            Reason.TwoTeamsRequired -> UiText.Resource(R.string.deux_equipes_attendues_par_donne)
            Reason.SingleTakingTeamRequired -> UiText.Resource(R.string.une_seule_equipe_preneuse_par_donne)
            is Reason.TakerPointsOutOfRange -> UiText.Resource(R.string.points_invalides_0_a_count1, listOf(reason.max))
            Reason.TakerRequired -> UiText.Resource(R.string.une_donne_doit_avoir_un_preneur_ou_etre_marquee_passee)
            Reason.PartnerRequired -> UiText.Resource(R.string.un_partenaire_roi_appele_est_requis_a_5_joueurs)
            Reason.InvalidTarotHand -> UiText.Resource(R.string.contrat_bouts_ou_poignee_invalides)
            is Reason.TricksOutOfRange -> UiText.Resource(R.string.resultat_invalide_0_a_count1, listOf(reason.max))
            is Reason.BidOutOfRange -> UiText.Resource(R.string.annonce_invalide_0_a_count1, listOf(reason.max))
            is Reason.TricksTotalMismatch ->
                UiText.Resource(
                    R.string.le_total_des_plis_realises_count1_doit_egaler_count2,
                    listOf(reason.total, reason.expected),
                )
            Reason.SingleCategoryPerTurn -> UiText.Resource(R.string.une_seule_categorie_est_remplie_par_tour)
            Reason.UnknownCategory -> UiText.Resource(R.string.categorie_inconnue)
            Reason.CategoryAlreadyFilled -> UiText.Resource(R.string.cette_categorie_est_deja_remplie)
            Reason.InvalidDiceCount -> UiText.Resource(R.string.nombre_de_des_invalide_0_a_5)
            Reason.InvalidFigureValue -> UiText.Resource(R.string.valeur_invalide)
            Reason.InvalidDiceSum -> UiText.Resource(R.string.somme_invalide)
        }

/** Explication du dernier score recalculé (doublement du Skyjo, dépassement du Mölkky, bonus du
 * Yams), à afficher juste après la validation d'une manche. */
val MatchState.lastRoundExplanation: UiText?
    get() {
        val entry = rounds.lastOrNull()?.entries?.firstOrNull { it.explanation != null } ?: return null
        val name = participants.firstOrNull { it.id == entry.participantID }?.displayName.orEmpty()
        return when (entry.explanation) {
            ScoreExplanation.DoubledForClosingWithoutLowest ->
                UiText.Resource(R.string.score_double_value1_a_ferme_la_manche_sans_le_score_le_plus, listOf(name))
            ScoreExplanation.BustBackTo25 -> UiText.Resource(R.string.depassement_de_50_retour_a_25)
            ScoreExplanation.UpperSectionBonus -> UiText.Resource(R.string.bonus_de_section_haute_35)
            null -> null
        }
    }
