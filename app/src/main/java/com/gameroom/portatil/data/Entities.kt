package com.gameroom.portatil.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.gameroom.portatil.domain.EventType
import java.time.LocalDate

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platform: String?,
    val catalogGameId: Int? = null,  // id no GameRoom, quando escolhido do catálogo
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId"), Index(value = ["uuid"], unique = true)],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val gameId: Long,
    val type: EventType,
    val date: LocalDate,
    val createdAt: Long = System.currentTimeMillis(),
    val playedHours: Double? = null,
    val score: Double? = null,
    val difficulty: Double? = null,
    val notes: String? = null,
    val exportedAt: Long? = null,  // null = ainda não foi para o GameRoom
    // Platinado com zeramento em outro dia: vai ao GameRoom como um evento Zerado antes da platina
    val finishDate: LocalDate? = null,
    val finishUuid: String? = null,
    // Quando passou a aguardar exportação (criação ou correção); null em bancos antigos = createdAt
    val pendingSince: Long? = null,
)

@Entity(tableName = "catalog_games")
data class CatalogGameEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val platform: String?,
    val status: String,
)

@Entity(tableName = "catalog_platforms")
data class CatalogPlatformEntity(@PrimaryKey val name: String)

/** Linha única com a origem do catálogo importado. */
@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val id: Int = 0,
    val exportedAt: String?,  // exported_at do arquivo (ISO 8601), como veio do GameRoom
    val importedAt: Long,
)

data class GameWithEvents(
    @Embedded val game: GameEntity,
    @Relation(parentColumn = "id", entityColumn = "gameId") val events: List<EventEntity>,
) {
    val sortedEvents: List<EventEntity> get() = events.sortedWith(compareBy({ it.date }, { it.createdAt }))
    val latest: EventEntity? get() = sortedEvents.lastOrNull()
    val pendingCount: Int get() = events.count { it.exportedAt == null }

    /** Dia do zeramento mais recente registrado no app (Zerado, ou o zeramento de um Platinado). */
    fun lastFinishDate(exceptEventId: Long? = null): LocalDate? = events.asSequence()
        .filter { it.id != exceptEventId }
        .mapNotNull { if (it.type == EventType.FINISHED) it.date else it.finishDate }
        .maxOrNull()
}

class Converters {
    @TypeConverter fun fromDate(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun toDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun fromType(t: EventType): String = t.wire
    @TypeConverter fun toType(v: String): EventType = EventType.fromWire(v)
}
