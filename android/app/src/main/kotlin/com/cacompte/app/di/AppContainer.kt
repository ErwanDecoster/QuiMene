package com.cacompte.app.di

import android.content.Context
import com.cacompte.catalog.GameCatalogEmbedded
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.AppSettings
import com.cacompte.store.CaCompteDatabase
import com.cacompte.store.LeaderboardRepository
import com.cacompte.store.MatchRepository
import com.cacompte.store.PlayerRepository
import com.cacompte.store.ProfileRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Composition root manuelle — pas de framework d'injection (Hilt/Dagger) : même esprit que
 * ADR-0012 (« zéro dépendance tierce »), il n'y a que quatre repositories et un catalogue à
 * relier, un conteneur simple suffit. Une seule instance vit dans [com.cacompte.app.CaCompteApplication].
 */
class AppContainer(
    context: Context,
    applicationScope: CoroutineScope,
) {
    val catalog: GameCatalog = GameCatalogEmbedded.embedded

    private val database = CaCompteDatabase.build(context)

    val playerRepository = PlayerRepository(database.playerDao())
    val matchRepository = MatchRepository(database.matchDao(), database.participantDao(), database.playerDao())
    val leaderboardRepository =
        LeaderboardRepository(database.matchDao(), database.participantDao(), database.playerDao())
    val profileRepository = ProfileRepository(database.matchDao(), database.participantDao())

    val appSettings = AppSettings(context, applicationScope)
}
