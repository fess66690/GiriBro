package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.util.bestStreak
import ru.petproject.giribro.util.computeStreak
import java.time.LocalDate

class VolumeTest {

    private fun workout(vararg sections: Section) = Workout(
        date = LocalDate.of(2026, 11, 4),
        sections = sections.toList()
    )

    @Test
    fun `jerk counts two kettlebells`() {
        val w = workout(Section(SectionType.JERK, listOf(SetEntry(24, 20))))
        assertEquals(960.0, w.tonnageKg, 0.001)
    }

    @Test
    fun `double kettlebell exercises use two bells`() {
        assertEquals(2.0, SectionType.JERK.coefficient, 0.001)
        assertEquals(2.6, SectionType.LONG_CYCLE.coefficient, 0.001)
        assertEquals(1.0, SectionType.SNATCH.coefficient, 0.001)
    }

    @Test
    fun `warmup is not included in tonnage`() {
        val w = workout(
            Section(SectionType.WARMUP, listOf(SetEntry(16, 20))),
            Section(SectionType.SNATCH, listOf(SetEntry(24, 10)))
        )
        assertEquals(240.0, w.tonnageKg, 0.001)
        assertEquals(560.0, w.volumeKg, 0.001)
    }

    @Test
    fun `long cycle uses two bells and coefficient`() {
        val w = workout(Section(SectionType.LONG_CYCLE, listOf(SetEntry(24, 10))))
        assertEquals(624.0, w.tonnageKg, 0.001)
    }

    @Test
    fun `swings count volume but not tonnage`() {
        val w = workout(Section(SectionType.SWINGS, listOf(SetEntry(24, 20))))
        assertEquals(0.0, w.tonnageKg, 0.001)
        assertEquals(480.0, w.volumeKg, 0.001)
    }

    @Test
    fun `duration sums all sections and sets`() {
        val w = workout(
            Section(
                SectionType.WARMUP,
                listOf(SetEntry(16, 20, 60), SetEntry(16, 20, 60))
            ),
            Section(
                SectionType.JERK,
                listOf(SetEntry(24, 20, 120), SetEntry(24, 15, 90))
            )
        )
        assertEquals(2, w.sections.size)
        assertEquals(330, w.durationSec)
    }

    @Test
    fun `main duration excludes warmup`() {
        val w = workout(
            Section(SectionType.WARMUP, listOf(SetEntry(16, 20, 60))),
            Section(SectionType.JERK, listOf(SetEntry(24, 20, 120)))
        )
        assertEquals(180, w.durationSec)
        assertEquals(120, w.mainDurationSec)
    }

    @Test
    fun `set duration is optional and defaults to zero`() {
        assertEquals(0, SetEntry(24, 20).durationSec)
    }

    @Test
    fun `streak counts consecutive weeks with goal`() {
        val today = LocalDate.of(2026, 11, 4)
        val dates = listOf(
            today,                          // текущая неделя
            today.minusDays(1),             // текущая неделя
            today.minusWeeks(1),
            today.minusWeeks(1).minusDays(1),
            today.minusWeeks(2)             // только одна — серия рвётся
        )
        assertEquals(2, computeStreak(dates, goal = 2, today = today))
    }

    @Test
    fun `current unfinished week does not break streak`() {
        val today = LocalDate.of(2026, 11, 4)
        val dates = listOf(
            today.minusWeeks(1),
            today.minusWeeks(1).minusDays(1),
            today.minusWeeks(2),
            today.minusWeeks(2).minusDays(1)
        )
        assertEquals(2, computeStreak(dates, goal = 2, today = today))
    }

    @Test
    fun `best streak is independent of today`() {
        val today = LocalDate.of(2026, 11, 4)
        val dates = listOf(
            today.minusWeeks(6),
            today.minusWeeks(6).minusDays(1),
            today.minusWeeks(5),
            today.minusWeeks(5).minusDays(1),
            today.minusWeeks(1)
        )
        assertEquals(2, bestStreak(dates, goal = 2))
    }
}
