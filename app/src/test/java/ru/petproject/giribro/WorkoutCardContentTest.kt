package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutCardContent
import java.time.LocalDate

class WorkoutCardContentTest {

    private val date = LocalDate.of(2026, 11, 12)

    @Test
    fun `title uses main sections and ignores warmup`() {
        val card = WorkoutCardContent.from(
            workout(
                sections = listOf(
                    section(SectionType.WARMUP),
                    section(SectionType.JERK),
                    section(SectionType.SNATCH)
                )
            )
        )

        assertEquals("Толчок · Рывок", card.title)
        assertEquals("12 ноября", card.date)
        assertEquals("Четверг", card.weekday)
        assertEquals("2026", card.year)
    }

    @Test
    fun `tonnage switches to tons above one thousand kilograms`() {
        val card = WorkoutCardContent.from(
            workout(sections = listOf(section(SectionType.JERK, weight = 32, reps = 10, sets = 2)))
        )

        assertEquals("1.28", card.tonnageValue)
        assertEquals("т", card.tonnageUnit)
        assertEquals("20", card.reps)
        assertEquals("1", card.sectionsCount)
    }

    @Test
    fun `tonnage stays in kilograms below one thousand`() {
        val card = WorkoutCardContent.from(
            workout(sections = listOf(section(SectionType.JERK, weight = 16, reps = 5)))
        )

        assertEquals("160", card.tonnageValue)
        assertEquals("кг", card.tonnageUnit)
        assertEquals("160 кг", card.sections.single().volume)
    }

    @Test
    fun `section details include sets reps and duration`() {
        val card = WorkoutCardContent.from(
            workout(
                sections = listOf(
                    section(SectionType.JERK, reps = 5, duration = 90)
                )
            )
        )

        assertEquals("1 подход · 5 подъёмов · 1:30", card.sections.single().details)
        assertEquals("1:30", card.duration)
    }

    @Test
    fun `card shows five sections and reports the rest`() {
        val card = WorkoutCardContent.from(
            workout(sections = List(6) { section(SectionType.JERK) })
        )

        assertEquals(5, card.sections.size)
        assertEquals(1, card.hiddenSections)
    }

    @Test
    fun `note reduces visible sections to keep the card readable`() {
        val card = WorkoutCardContent.from(
            workout(
                sections = List(6) { section(SectionType.JERK) },
                note = "Хорошая тренировка"
            )
        )

        assertEquals(4, card.sections.size)
        assertEquals(2, card.hiddenSections)
        assertEquals("Хорошая тренировка", card.note)
    }

    @Test
    fun `empty workout gets safe fallback values`() {
        val card = WorkoutCardContent.from(workout(sections = emptyList()))

        assertEquals("Тренировка", card.title)
        assertEquals("0", card.tonnageValue)
        assertEquals("кг", card.tonnageUnit)
        assertEquals("0", card.reps)
        assertEquals("—", card.duration)
        assertEquals("0", card.sectionsCount)
        assertTrue(card.sections.isEmpty())
        assertNull(card.note)
    }

    @Test
    fun `file name contains date and workout id`() {
        val card = WorkoutCardContent.from(workout(id = 42, sections = emptyList()))

        assertEquals("giribro-2026-11-12-42.png", card.fileName)
    }

    private fun workout(
        id: Long = 1,
        sections: List<Section>,
        note: String = ""
    ): Workout = Workout(
        id = id,
        date = date,
        sections = sections,
        note = note
    )

    private fun section(
        type: SectionType,
        weight: Int = 16,
        reps: Int = 5,
        sets: Int = 1,
        duration: Int = 0
    ): Section = Section(
        type = type,
        sets = List(sets) {
            SetEntry(
                weightKg = weight,
                reps = reps,
                durationSec = duration
            )
        }
    )
}
