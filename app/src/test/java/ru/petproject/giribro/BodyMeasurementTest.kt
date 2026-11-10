package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.petproject.giribro.data.BodyMeasurement
import ru.petproject.giribro.data.BodyMeasurementValidator
import ru.petproject.giribro.data.asBodyNumber
import ru.petproject.giribro.data.asBodySize
import ru.petproject.giribro.data.asBodyWeight
import ru.petproject.giribro.data.asSignedBodyWeight
import ru.petproject.giribro.data.toEntity
import ru.petproject.giribro.data.toModelOrNull
import java.time.LocalDate

class BodyMeasurementTest {

    private val today = LocalDate.now()

    private fun measurement(
        date: LocalDate = today,
        weightKg: Double = 82.5,
        waistCm: Double? = 86.0,
        note: String = "Утро"
    ) = BodyMeasurement(
        date = date,
        weightKg = weightKg,
        waistCm = waistCm,
        chestCm = 104.0,
        hipsCm = 100.0,
        armCm = 36.0,
        thighCm = 58.5,
        note = note,
        createdAt = 123L
    )

    @Test
    fun `valid measurement passes validation`() {
        assertNull(BodyMeasurementValidator.validate(measurement()))
    }

    @Test
    fun `future date is rejected`() {
        assertEquals(
            "Дата замера не может быть в будущем.",
            BodyMeasurementValidator.validate(measurement(date = today.plusDays(1)))
        )
    }

    @Test
    fun `weight outside allowed range is rejected`() {
        assertNotNull(BodyMeasurementValidator.validate(measurement(weightKg = 19.9)))
        assertNotNull(BodyMeasurementValidator.validate(measurement(weightKg = 300.1)))
    }

    @Test
    fun `circumference outside allowed range is rejected`() {
        assertNotNull(BodyMeasurementValidator.validate(measurement(waistCm = 19.9)))
        assertNotNull(BodyMeasurementValidator.validate(measurement(waistCm = 250.1)))
    }

    @Test
    fun `too long note is rejected`() {
        assertNotNull(
            BodyMeasurementValidator.validate(
                measurement(note = "я".repeat(BodyMeasurementValidator.MAX_NOTE_LENGTH + 1))
            )
        )
    }

    @Test
    fun `entity mapping keeps all fields`() {
        val original = measurement()
        assertEquals(original, original.toEntity().toModelOrNull())
    }

    @Test
    fun `body values are formatted without trailing zero`() {
        assertEquals("82", 82.0.asBodyNumber())
        assertEquals("82.5", 82.46.asBodyNumber())
        assertEquals("82.5 кг", 82.5.asBodyWeight())
        assertEquals("86 см", 86.0.asBodySize())
        assertEquals("−1.2 кг", (-1.2).asSignedBodyWeight())
        assertEquals("+0.4 кг", 0.4.asSignedBodyWeight())
        assertEquals("0 кг", 0.0.asSignedBodyWeight())
    }
}
