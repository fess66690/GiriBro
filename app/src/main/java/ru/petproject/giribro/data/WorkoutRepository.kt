package ru.petproject.giribro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeAll(): Flow<List<Workout>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    suspend fun save(workout: Workout): Long = dao.upsert(workout.toEntity())

    suspend fun delete(workout: Workout) = dao.delete(workout.toEntity())

    suspend fun clear() = dao.clear()
}
