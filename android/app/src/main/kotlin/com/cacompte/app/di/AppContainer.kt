package com.cacompte.app.di

import android.content.Context
import com.cacompte.app.livesync.LiveShareCoordinator
import com.cacompte.app.livesync.MatchConnectionCoordinator
import com.cacompte.app.profilesharing.SharedProfileSyncCoordinator
import com.cacompte.catalog.GameCatalogEmbedded
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.AppSettings
import com.cacompte.store.CaCompteDatabase
import com.cacompte.store.DeviceIdentity
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

    /** Doc 09 — un seul `LiveSession` hôte et un seul rejoint à la fois, tous deux de durée de
     * vie applicative (pas liés à un écran) : voir [LiveShareCoordinator]/
     * [MatchConnectionCoordinator]. */
    val liveShareCoordinator =
        LiveShareCoordinator(
            catalog = catalog,
            matchRepository = matchRepository,
            resolveDeviceID = { DeviceIdentity.current(context) },
            scope = applicationScope,
        )
    val matchConnectionCoordinator =
        MatchConnectionCoordinator(
            catalog = catalog,
            resolveDeviceID = { DeviceIdentity.current(context) },
            scope = applicationScope,
        )

    /** Doc 14 — poussé au lancement et à chaque retour au premier plan (voir
     * [com.cacompte.app.CaCompteApplication]), pas par un minuteur propre. */
    val sharedProfileSyncCoordinator = SharedProfileSyncCoordinator(matchRepository, playerRepository)
}
