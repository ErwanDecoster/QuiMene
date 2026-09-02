package com.cacompte.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Miroir de `CaCompteSchemaV1`/`CaCompteMigrationPlan` (`Schema.swift`) — posé dès la v1, même
 * plan de migration vide, pour la même raison que côté Swift : le créer après coup coûte plus
 * cher qu'une base vide au départ.
 */
@Database(
    entities = [PlayerEntity::class, MatchEntity::class, ParticipantEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CaCompteDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    abstract fun matchDao(): MatchDao

    abstract fun participantDao(): ParticipantDao

    companion object {
        private const val DATABASE_NAME = "cacompte.db"

        fun build(context: Context): CaCompteDatabase =
            Room.databaseBuilder(context.applicationContext, CaCompteDatabase::class.java, DATABASE_NAME).build()
    }
}
