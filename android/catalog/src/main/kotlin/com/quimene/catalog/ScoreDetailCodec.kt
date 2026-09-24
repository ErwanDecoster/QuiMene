package com.quimene.catalog

import com.quimene.domain.model.ScoreDetail
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encodage/décodage JSON générique du payload opaque de [ScoreDetail] — même principe que
 * `JSONEncoder().encode(...)` dans `YamsRulesV1.swift`/`TarotRulesV1.swift`/`WizardRulesV1.swift`
 * (`YamsCategoryDetail`, `TarotHandDetail`, `WizardBidDetail`), factorisé ici une seule fois
 * plutôt que répété dans les trois fichiers de moteur. Public (pas `internal`) : `:app` encode le
 * même payload côté écran de saisie (`LiveMatchViewModel.commitRound` prend un
 * `Map<UUID, ScoreDetail>` déjà construit par l'appelant) — mêmes types de detail, même codec,
 * pas de duplication de la logique d'encodage.
 */
inline fun <reified T> T.toScoreDetail(): ScoreDetail = ScoreDetail(Json.encodeToString(this).encodeToByteArray())

inline fun <reified T> ScoreDetail?.decodeDetail(): T? =
    this?.let { detail -> runCatching { Json.decodeFromString<T>(detail.payload.decodeToString()) }.getOrNull() }
