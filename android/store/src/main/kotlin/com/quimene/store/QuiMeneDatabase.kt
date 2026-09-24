package com.quimene.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Miroir de `QuiMeneSchemaV1`/`QuiMeneMigrationPlan` (`Schema.swift`) — posé dès la v1, même
 * plan de migration vide, pour la même raison que côté Swift : le créer après coup coûte plus
 * cher qu'une base vide au départ.
 */
@Database(
    entities = [PlayerEntity::class, MatchEntity::class, ParticipantEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class QuiMeneDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    abstract fun matchDao(): MatchDao

    abstract fun participantDao(): ParticipantDao

    companion object {
        private const val DATABASE_NAME = "quimene.db"

        fun build(context: Context): QuiMeneDatabase =
            Room.databaseBuilder(context.applicationContext, QuiMeneDatabase::class.java, DATABASE_NAME).build()
    }
}
