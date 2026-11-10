package ru.petproject.giribro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val weightKg: Double,
    val waistCm: Double?,
    val chestCm: Double?,
    val hipsCm: Double?,
    val armCm: Double?,
    val thighCm: Double?,
    val note: String,
    val createdAt: Long
)

fun BodyMeasurementEntity.toModelOrNull(): BodyMeasurement? {
    if (!weightKg.isFinite()) return null
    return runCatching {
        BodyMeasurement(
            id = id,
            date = LocalDate.ofEpochDay(dateEpochDay),
            weightKg = weightKg,
            waistCm = waistCm?.takeIf { it.isFinite() },
            chestCm = chestCm?.takeIf { it.isFinite() },
            hipsCm = hipsCm?.takeIf { it.isFinite() },
            armCm = armCm?.takeIf { it.isFinite() },
            thighCm = thighCm?.takeIf { it.isFinite() },
            note = note,
            createdAt = createdAt
        )
    }.getOrNull()
}

fun BodyMeasurement.toEntity(): BodyMeasurementEntity = BodyMeasurementEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    weightKg = weightKg,
    waistCm = waistCm,
    chestCm = chestCm,
    hipsCm = hipsCm,
    armCm = armCm,
    thighCm = thighCm,
    note = note,
    createdAt = createdAt
)
