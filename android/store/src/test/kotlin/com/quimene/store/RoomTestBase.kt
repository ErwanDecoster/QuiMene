package com.quimene.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base commune des tests Room — Robolectric fait tourner une vraie base SQLite en JVM pur, sans
 * émulateur ni appareil connecté (aucun n'était disponible pendant cette session). `@Config(sdk
 * = [34])` : Robolectric n'embarque pas encore de shadows pour des niveaux d'API aussi récents
 * que `compileSdk` (37) au moment de l'écriture — 34 est un niveau stable largement supporté,
 * sans rapport avec `minSdk`/`compileSdk` du module lui-même.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class RoomTestBase {
    protected lateinit var db: QuiMeneDatabase

    @Before
    fun setUpDatabase() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), QuiMeneDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected fun runTest(block: suspend () -> Unit) = runBlocking { block() }
}
