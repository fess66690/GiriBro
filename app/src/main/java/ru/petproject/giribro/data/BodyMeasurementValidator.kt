package ru.petproject.giribro.data

import java.time.LocalDate

/** Проверка замера перед сохранением и импортом резервной копии. */
object BodyMeasurementValidator {

    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 300.0
    const val MIN_SIZE_CM = 20.0
    const val MAX_SIZE_CM = 250.0
    const val MAX_NOTE_LENGTH = 500

    fun validate(measurement: BodyMeasurement): String? {
        if (measurement.date.isAfter(LocalDate.now())) {
            return "Дата замера не может быть в будущем."
        }
        if (!measurement.weightKg.isFinite() ||
            measurement.weightKg < MIN_WEIGHT_KG ||
            measurement.weightKg > MAX_WEIGHT_KG
        ) {
            return "Вес должен быть от ${MIN_WEIGHT_KG.toInt()} до ${MAX_WEIGHT_KG.toInt()} кг."
        }

        val sizes = listOf(
            "талия" to measurement.waistCm,
            "грудь" to measurement.chestCm,
            "бёдра" to measurement.hipsCm,
            "плечо" to measurement.armCm,
            "бедро" to measurement.thighCm
        )
        sizes.forEach { (label, value) ->
            if (value != null &&
                (!value.isFinite() || value < MIN_SIZE_CM || value > MAX_SIZE_CM)
            ) {
                return "Обхват «$label» должен быть от ${MIN_SIZE_CM.toInt()} до " +
                    "${MAX_SIZE_CM.toInt()} см."
            }
        }

        if (measurement.note.length > MAX_NOTE_LENGTH) {
            return "Заметка слишком длинная: максимум $MAX_NOTE_LENGTH символов."
        }
        return null
    }
}
