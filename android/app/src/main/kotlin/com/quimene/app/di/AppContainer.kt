package com.quimene.app.di

import android.content.Context
import com.quimene.app.livesync.LiveShareCoordinator
import com.quimene.app.livesync.MatchConnectionCoordinator
import com.quimene.app.profilesharing.SharedProfileSyncCoordinator
import com.quimene.catalog.GameCatalogEmbedded
import com.quimene.domain.rules.GameCatalog
import com.quimene.store.AppSettings
import com.quimene.store.DeviceIdentity
import com.quimene.store.LeaderboardRepository
import com.quimene.store.MatchRepository
import com.quimene.store.PlayerRepository
import com.quimene.store.ProfileRepository
import com.quimene.store.QuiMeneDatabase
import kotlinx.coroutines.CoroutineScope

/**
 * Composition root manuelle — pas de framework d'injection (Hilt/Dagger) : même esprit que
 * ADR-0012 (« zéro dépendance tierce »), il n'y a que quatre repositories et un catalogue à
 * relier, un conteneur simple suffit. Une seule instance vit dans [com.quimene.app.QuiMeneApplication].
 */
class AppContainer(
    context: Context,
    applicationScope: CoroutineScope,
) {
    val catalog: GameCatalog = GameCatalogEmbedded.embedded

    private val database = QuiMeneDatabase.build(context)

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
     * [com.quimene.app.QuiMeneApplication]), pas par un minuteur propre. */
    val sharedProfileSyncCoordinator = SharedProfileSyncCoordinator(matchRepository, playerRepository)
}
