package com.cacompte.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Miroir partiel de `AppSettings.swift` — **`iCloudSyncEnabled` n'est pas porté** : c'est un
 * réglage qui active/désactive CloudKit, sans équivalent Android (doc 11 « Synchronisation » :
 * « il n'existe pas d'équivalent Android à CloudKit », v1 Android sans sync multi-appareils). Le
 * porter littéralement créerait un réglage qui ne contrôlerait rien. `playerSortMode` reste, lui,
 * un vrai choix d'UI partagé entre les deux plateformes (doc 01).
 *
 * `StateFlow` plutôt que `@Observable` (Kotlin n'a pas d'équivalent direct) — un
 * [CoroutineScope] de portée application doit être fourni par l'appelant (`:app`), comme
 * `AppSettings.swift` suppose un `MainActor` déjà en place.
 */
class AppSettings(
    context: Context,
    scope: CoroutineScope,
) {
    enum class PlayerSortMode { Automatic, Manual }

    private val dataStore = context.applicationContext.appSettingsDataStore

    val playerSortMode: StateFlow<PlayerSortMode> =
        dataStore.data
            .map { preferences ->
                preferences[PLAYER_SORT_MODE_KEY]?.let { raw ->
                    runCatching { PlayerSortMode.valueOf(raw) }.getOrNull()
                } ?: PlayerSortMode.Automatic
            }.stateIn(scope, SharingStarted.Eagerly, PlayerSortMode.Automatic)

    suspend fun setPlayerSortMode(mode: PlayerSortMode) {
        dataStore.edit { it[PLAYER_SORT_MODE_KEY] = mode.name }
    }

    companion object {
        private val PLAYER_SORT_MODE_KEY = stringPreferencesKey("playerSortMode")
    }
}
