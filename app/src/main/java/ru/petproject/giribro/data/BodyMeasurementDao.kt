package ru.petproject.giribro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {

    @Query("SELECT * FROM body_measurements ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY dateEpochDay DESC, createdAt DESC")
    suspend fun getAll(): List<BodyMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BodyMeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BodyMeasurementEntity>)

    @Delete
    suspend fun delete(entity: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurements")
    suspend fun clear()

    /** Замена истории замеров одной транзакцией: при ошибке старые данные останутся. */
    @Transaction
    suspend fun replaceAll(entities: List<BodyMeasurementEntity>) {
        clear()
        insertAll(entities)
    }
}
