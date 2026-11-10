package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.QuickWorkoutParser
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry

class QuickWorkoutParserTest {

    @Test
    fun `parses compact workout and groups by exercise`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок 16 5x10
            Рывок 16 10+10+8 1:30
            Махи 24 3x20 отдых 1:00
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertEquals(
            listOf(SectionType.JERK, SectionType.SNATCH, SectionType.SWINGS),
            result.sections.map { it.type }
        )

        val jerk = result.sections[0]
        assertEquals(5, jerk.sets.size)
        assertTrue(jerk.sets.all { it.weightKg == 16 && it.reps == 10 })

        val snatch = result.sections[1]
        assertEquals(listOf(10, 10, 8), snatch.sets.map { it.reps })
        assertTrue(snatch.sets.all { it.durationSec == 90 })

        val swings = result.sections[2]
        assertEquals(3, swings.sets.size)
        assertTrue(swings.sets.all { it.restSec == 60 })
        assertEquals(2048.0, result.sections.sumOf { it.tonnageKg }, 0.001)
    }

    @Test
    fun `understands kilograms and cyrillic multiplication`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок 16 кг 5х10
            Рывок 16 кг 10 по 10
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertEquals(5, result.sections[0].sets.size)
        assertEquals(10, result.sections[1].sets.size)
        assertTrue(result.sections.flatMap { it.sets }.all { it.weightKg == 16 })
    }

    @Test
    fun `exercise header continues on following lines`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок:
            16 5x10
            16 5x8 1:30
            Рывок 20 10
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertEquals(2, result.sections.size)

        val jerk = result.sections[0]
        assertEquals(10, jerk.sets.size)
        assertEquals(10, jerk.sets.first().reps)
        assertEquals(8, jerk.sets.last().reps)
        assertEquals(90, jerk.sets.last().durationSec)

        assertEquals(1, result.sections[1].sets.size)
        assertEquals(20, result.sections[1].sets.single().weightKg)
    }

    @Test
    fun `repeated exercise is merged into one section`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок 16 2x10
            Рывок 16 3x10
            Толчок 20 1x5
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertEquals(2, result.sections.size)
        assertEquals(3, result.sections[0].sets.size)
        assertEquals(20, result.sections[0].sets.last().weightKg)
        assertEquals(5, result.sections[0].sets.last().reps)
    }

    @Test
    fun `supports several time and rest formats`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок 16 2x10 1:30
            Рывок 16 2x10 90с
            Махи 24 2x10 1м30с
            ОФП 0 2x10 01:30
            Заминка 0 2x10 отдых=1:30
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertTrue(result.sections.dropLast(1).flatMap { it.sets }.all { it.durationSec == 90 })
        assertTrue(result.sections.last().sets.all { it.durationSec == 0 })
        assertEquals(90, result.sections.last().sets.last().restSec)
    }

    @Test
    fun `rest can be written with colon or equals`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок 16 2x10 отдых=1:30
            Рывок 16 2x10 отдых:90с
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertTrue(result.sections.flatMap { it.sets }.all { it.restSec == 90 })
    }

    @Test
    fun `blank lines and comments are ignored`() {
        val result = QuickWorkoutParser.parse(
            """
            # тренировка
            // комментарий

            Толчок 16 2x10
            """.trimIndent()
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertEquals(1, result.sections.size)
        assertEquals(2, result.sections.single().sets.size)
    }

    @Test
    fun `unknown exercise is reported with line number`() {
        val result = QuickWorkoutParser.parse("Жим 20 5x10")

        assertFalse(result.isValid)
        assertTrue(result.sections.isEmpty())
        assertTrue(result.errors.single().contains("Строка 1"))
        assertTrue(result.errors.single().contains("упражнение"))
    }

    @Test
    fun `invalid weight and reps are rejected`() {
        val result = QuickWorkoutParser.parse(
            """
            Толчок 500 5x10
            Рывок 16 5x0
            """.trimIndent()
        )

        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors[0].contains("Строка 1"))
        assertTrue(result.errors[0].contains("вес"))
        assertTrue(result.errors[1].contains("Строка 2"))
        assertTrue(result.errors[1].contains("повторы"))
    }

    @Test
    fun `all reps in a plus scheme are validated`() {
        val result = QuickWorkoutParser.parse("Толчок 16 10+0+10")

        assertFalse(result.isValid)
        assertTrue(result.errors.single().contains("повторы"))
    }

    @Test
    fun `empty text and header without sets produce clear errors`() {
        val empty = QuickWorkoutParser.parse("   ")
        assertFalse(empty.isValid)
        assertTrue(empty.errors.single().contains("хотя бы одну строку"))

        val headerOnly = QuickWorkoutParser.parse("Толчок:")
        assertFalse(headerOnly.isValid)
        assertTrue(headerOnly.errors.single().contains("ни одного подхода"))
    }

    @Test
    fun `too many sets are rejected`() {
        val result = QuickWorkoutParser.parse(
            List(4) { "Толчок 16 99x10" }.joinToString("\n")
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("максимум ${QuickWorkoutParser.MAX_SETS}") })
    }

    @Test
    fun `parsed sets keep defaults for rest and optional time`() {
        val result = QuickWorkoutParser.parse("Толчок 16 10")

        assertTrue(result.errors.joinToString(), result.isValid)
        val set = result.sections.single().sets.single()
        assertEquals(SetEntry.DEFAULT_REST_SEC, set.restSec)
        assertEquals(0, set.durationSec)
    }
}
