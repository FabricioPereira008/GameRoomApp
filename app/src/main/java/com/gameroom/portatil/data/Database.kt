package com.gameroom.portatil.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Transaction
    @Query("SELECT * FROM games ORDER BY updatedAt DESC")
    fun gamesWithEvents(): Flow<List<GameWithEvents>>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun gameWithEvents(id: Long): Flow<GameWithEvents?>

    @Transaction
    @Query("SELECT * FROM games")
    suspend fun allGamesWithEvents(): List<GameWithEvents>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun game(id: Long): GameEntity?

    @Insert suspend fun insertGame(game: GameEntity): Long
    @Update suspend fun updateGame(game: GameEntity)
    @Delete suspend fun deleteGame(game: GameEntity)

    @Query("UPDATE games SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun event(id: Long): EventEntity?

    @Insert suspend fun insertEvent(event: EventEntity): Long
    @Update suspend fun updateEvent(event: EventEntity)
    @Delete suspend fun deleteEvent(event: EventEntity)

    @Query("SELECT COUNT(*) FROM events WHERE exportedAt IS NULL")
    fun pendingCount(): Flow<Int>

    @Query("UPDATE events SET exportedAt = :now WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>, now: Long)

    @Query("UPDATE events SET exportedAt = NULL WHERE id IN (:ids)")
    suspend fun unmarkExported(ids: List<Long>)

    @Query("SELECT DISTINCT platform FROM games WHERE platform IS NOT NULL")
    fun localPlatforms(): Flow<List<String>>
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_games ORDER BY title")
    fun games(): Flow<List<CatalogGameEntity>>

    @Query("SELECT name FROM catalog_platforms ORDER BY name")
    fun platforms(): Flow<List<String>>

    @Query("DELETE FROM catalog_games") suspend fun clearGames()
    @Query("DELETE FROM catalog_platforms") suspend fun clearPlatforms()
    @Insert suspend fun insertGames(games: List<CatalogGameEntity>)
    @Insert suspend fun insertPlatforms(platforms: List<CatalogPlatformEntity>)

    @Transaction
    suspend fun replace(games: List<CatalogGameEntity>, platforms: List<CatalogPlatformEntity>) {
        clearGames(); clearPlatforms(); insertGames(games); insertPlatforms(platforms)
    }
}

@Database(
    entities = [GameEntity::class, EventEntity::class, CatalogGameEntity::class, CatalogPlatformEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun catalog(): CatalogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gameroom-portatil.db")
                .build().also { instance = it }
        }
    }
}
