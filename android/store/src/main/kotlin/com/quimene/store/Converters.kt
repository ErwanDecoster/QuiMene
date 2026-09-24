package com.quimene.store

import androidx.room.TypeConverter
import com.quimene.domain.model.MatchStatus
import java.time.Instant
import java.util.UUID

/**
 * Room exige un convertisseur explicite pour tout type qui n'est pas un type SQLite primitif —
 * contrairement à SwiftData qui stocke `UUID`/`Date` nativement. Le format de stockage
 * (`UUID.toString()`, millisecondes epoch) n'a aucune contrainte de compatibilité avec le format
 * JSON du wire Swift (`InstantSerializer`/`UUIDSerializer` dans `:domain`) : c'est une
 * représentation SQLite interne à cette base, jamais échangée avec l'app Apple.
 */
class Converters {
    @TypeConverter
    fun uuidToString(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun matchStatusToString(status: MatchStatus): String = status.name

    @TypeConverter
    fun stringToMatchStatus(value: String): MatchStatus = MatchStatus.valueOf(value)
}
