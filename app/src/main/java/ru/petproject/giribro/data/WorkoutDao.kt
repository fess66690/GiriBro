package ru.petproject.giribro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY dateEpochDay DESC, createdAt DESC")
    suspend fun getAll(): List<WorkoutEntity>

    /** Вставляет новую запись или заменяет существующую с тем же id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WorkoutEntity>)

    @Delete
    suspend fun delete(entity: WorkoutEntity)

    @Query("DELETE FROM workouts")
    suspend fun clear()

    /** Замена дневника одной транзакцией: при ошибке старые данные останутся. */
    @Transaction
    suspend fun replaceAll(entities: List<WorkoutEntity>) {
        clear()
        insertAll(entities)
    }
}
