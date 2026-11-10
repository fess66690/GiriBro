package ru.petproject.giribro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BodyMeasurementRepository(private val dao: BodyMeasurementDao) {

    fun observeAll(): Flow<List<BodyMeasurement>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toModelOrNull() } }

    suspend fun all(): List<BodyMeasurement> = dao.getAll().mapNotNull { it.toModelOrNull() }

    suspend fun save(measurement: BodyMeasurement): Long = dao.upsert(measurement.toEntity())

    suspend fun delete(measurement: BodyMeasurement) = dao.delete(measurement.toEntity())

    suspend fun clear() = dao.clear()

    /** Восстанавливает копию: новые id выдаст база, старые записи заменяются. */
    suspend fun replaceAll(measurements: List<BodyMeasurement>) =
        dao.replaceAll(measurements.map { it.copy(id = 0L).toEntity() })
}
