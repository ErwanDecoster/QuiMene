package com.cacompte.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.deviceIdentityDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_identity")

/**
 * Miroir de `DeviceIdentity.swift` — le `deviceID` qui départage l'horloge de Lamport
 * `(lamport, deviceID)` doit être stable pour un même appareil à travers les sessions. Généré une
 * fois, persisté (Preferences DataStore au lieu d'`UserDefaults`, doc 11 étape D) : suffisant pour
 * départager plusieurs appareils pendant une partie, une réinstallation n'a pas besoin d'en
 * hériter.
 *
 * `suspend` plutôt que la propriété calculée synchrone de Swift : DataStore est asynchrone par
 * nature, contrairement à `UserDefaults`. Les appelants (couche modèle/ViewModel) lisent la
 * valeur une fois et la font suivre explicitement, comme le fait déjà `MatchRepository` côté
 * Swift (`deviceID` est un paramètre, jamais relu en interne à chaque appel).
 */
object DeviceIdentity {
    private val key = stringPreferencesKey("deviceID")

    suspend fun current(context: Context): String {
        val existing = context.deviceIdentityDataStore.data.first()[key]
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        context.deviceIdentityDataStore.edit { it[key] = generated }
        return generated
    }
}
