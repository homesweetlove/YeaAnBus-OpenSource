package kr.yeaan.bus.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_stops")
data class SavedStopEntity(
    @PrimaryKey val key: String, val payload: String,
    val favorite: Boolean = false, val viewedAt: Long = 0
)
@Entity(tableName = "api_cache")
data class CacheEntity(@PrimaryKey val key: String, val payload: String, val savedAt: Long)
@Entity(tableName = "bus_positions")
data class PositionEntity(@PrimaryKey val key: String, val payload: String, val observedAt: Long)

@Dao
interface BusDao {
    @Query("SELECT * FROM saved_stops WHERE favorite = 1 ORDER BY viewedAt DESC")
    fun favorites(): Flow<List<SavedStopEntity>>
    @Query("SELECT * FROM saved_stops WHERE viewedAt > 0 ORDER BY viewedAt DESC LIMIT 12")
    fun recent(): Flow<List<SavedStopEntity>>
    @Query("SELECT * FROM saved_stops WHERE key = :key") suspend fun saved(key: String): SavedStopEntity?
    @Upsert suspend fun save(entity: SavedStopEntity)
    @Query("SELECT * FROM api_cache WHERE key = :key") suspend fun cache(key: String): CacheEntity?
    @Upsert suspend fun cache(entity: CacheEntity)
    @Upsert suspend fun positions(entities: List<PositionEntity>)
    @Query("SELECT * FROM bus_positions WHERE observedAt > :after") suspend fun positionsAfter(after: Long): List<PositionEntity>
    @Query("DELETE FROM api_cache WHERE savedAt < :before") suspend fun pruneCache(before: Long)
    @Query("DELETE FROM bus_positions WHERE observedAt < :before") suspend fun prunePositions(before: Long)
    @Query("DELETE FROM saved_stops WHERE favorite = 0 AND key NOT IN (SELECT key FROM saved_stops ORDER BY viewedAt DESC LIMIT 12)")
    suspend fun pruneHistory()
}
@Database(entities = [SavedStopEntity::class, CacheEntity::class, PositionEntity::class], version = 1, exportSchema = true)
abstract class BusDatabase : RoomDatabase() { abstract fun dao(): BusDao }
