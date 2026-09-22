package uk.co.mheonsitetraining.miles.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.mheonsitetraining.miles.core.TripCategory

@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE endMillis IS NOT NULL ORDER BY startMillis DESC")
    fun observeFinished(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun inProgress(): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE endMillis IS NOT NULL ORDER BY startMillis")
    suspend fun allFinished(): List<TripEntity>

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Delete
    suspend fun delete(trip: TripEntity)

    @Query("UPDATE trips SET distanceMetres = :metres WHERE id = :id")
    suspend fun setDistance(id: Long, metres: Double)

    @Query("UPDATE trips SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: TripCategory)
}
